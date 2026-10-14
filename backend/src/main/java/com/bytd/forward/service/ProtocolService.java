package com.bytd.forward.service;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ProtocolGroupRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.ProtocolDto;
import com.bytd.forward.web.dto.ProtocolRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProtocolService {

    private static final String STARTER_CODE = """
            // 平台提供的变量:
            //   msg   - 收到的原始报文(字符串)
            //   ctx   - 元数据(topic/source/receivedAt/partition/offset...)
            //   json  - json.parse(str) / json.stringify(obj)
            //   state - 跨消息聚合的键值存储(带 TTL): state.get/put/remove
            //   time  - time.toEpochMillis(x) / time.nowMs() / time.format(ms, pattern)
            //   log   - log.info/warn/error/debug
            // 输出:
            //   output(data)            发送到协议配置的输出目标
            //   output('key', data)     发送到指定目标(多目标场景)

            def obj = json.parse(msg)
            output([
                receivedAt: ctx.receivedAt,
                payload   : obj
            ])
            """;

    private final ProtocolRepository protocolRepository;
    private final ScriptVersionRepository versionRepository;
    private final ProtocolRuntimeManager runtimeManager;
    private final ScriptEngineService engine;
    private final ForwardProperties props;
    private final ProtocolBindingWarningService bindingWarningService;
    private final ProtocolGroupRepository groups;
    private final ProtocolMutationExecutor mutations;

    public ProtocolService(ProtocolRepository protocolRepository,
                           ScriptVersionRepository versionRepository,
                           ProtocolRuntimeManager runtimeManager,
                           ScriptEngineService engine,
                           ForwardProperties props,
                           ProtocolBindingWarningService bindingWarningService,
                           ProtocolGroupRepository groups, ProtocolMutationExecutor mutations) {
        this.protocolRepository = protocolRepository;
        this.versionRepository = versionRepository;
        this.runtimeManager = runtimeManager;
        this.engine = engine;
        this.props = props;
        this.bindingWarningService = bindingWarningService;
        this.groups = groups;
        this.mutations = mutations;
    }

    public List<ProtocolDto> list() {
        return protocolRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toDto).toList();
    }

    public ProtocolDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public ProtocolDto create(ProtocolRequest req) {
        ProtocolEntity p = new ProtocolEntity();
        p.setName(req.name);
        p.setDescription(req.description);
        validateGroup(req.getGroupId());
        p.setGroupId(req.getGroupId());
        p.setSortOrder(req.sortOrder == null ? 0 : req.sortOrder);
        p.setSourceId(req.sourceId);
        p.setSourceTopics(normalizeTopics(req.sourceTopics));
        p.setOutputTargetId(req.outputTargetId);
        p.setRingBufferSize(req.ringBufferSize != null ? req.ringBufferSize : props.getRuntime().getDefaultRingBufferSize());
        p.setWorkerThreads(req.workerThreads != null ? req.workerThreads : props.getRuntime().getDefaultWorkerThreads());
        p.setLogRetentionDays(req.logRetentionDays != null ? req.logRetentionDays : 7);
        p.setSampleRate(req.sampleRate != null ? req.sampleRate : 1.0);
        p.setStatus("STOPPED");
        p.setEnabled(false);
        p = protocolRepository.save(p);

        // 初始脚本版本，保证协议可运行、编辑器有初始内容
        CompileResult cr = engine.compile(STARTER_CODE);
        ScriptVersionEntity v = new ScriptVersionEntity();
        v.setProtocolId(p.getId());
        v.setVersion(1);
        v.setLanguage("groovy");
        v.setCode(STARTER_CODE);
        v.setCompileStatus(cr.isSuccess() ? "OK" : "ERROR");
        v.setCompileError(cr.isSuccess() ? null : cr.getError());
        v.setRemark("初始模板");
        v = versionRepository.save(v);

        p.setCurrentVersionId(v.getId());
        p = protocolRepository.save(p);
        return toDto(p);
    }

    public ProtocolDto update(Long id, ProtocolRequest req) {
        mutations.execute(id, () -> {
            ProtocolEntity p = protocolRepository.findLockedById(id)
                    .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + id));
            boolean reload = runtimeSettingsChanged(p, req);
            if (req.name != null) p.setName(req.name);
            p.setDescription(req.description);
            if (req.sourceId != null) p.setSourceId(req.sourceId);
            if (req.hasSourceTopics()) p.setSourceTopics(normalizeTopics(req.sourceTopics));
            if (req.outputTargetId != null) p.setOutputTargetId(req.outputTargetId);
            if (req.ringBufferSize != null) p.setRingBufferSize(req.ringBufferSize);
            if (req.workerThreads != null) p.setWorkerThreads(req.workerThreads);
            if (req.logRetentionDays != null) p.setLogRetentionDays(req.logRetentionDays);
            if (req.sampleRate != null) p.setSampleRate(req.sampleRate);
            if (req.hasGroupId()) {
                validateGroup(req.getGroupId());
                p.setGroupId(req.getGroupId());
            }
            if (req.sortOrder != null) p.setSortOrder(req.sortOrder);
            p = protocolRepository.save(p);
            return new UpdateResult(p, reload);
        }, result -> {
            runtimeManager.updateDisplay(id, result.protocol().getName(), result.protocol().getSortOrder());
            if (result.reload() && runtimeManager.isRunning(id)) {
                try {
                    restart(id, true);
                } catch (ProtocolBindingBlockedException e) {
                    runtimeManager.stop(id);
                    throw e;
                }
            }
        });
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        runtimeManager.stop(id);
        ProtocolEntity p = find(id);
        // 必须先清空 current_version_id 并刷盘，否则删除 script_version 会触发
        // fk_protocol_current_version 外键约束（protocol 仍引用该版本 id）
        p.setCurrentVersionId(null);
        protocolRepository.saveAndFlush(p);
        versionRepository.deleteByProtocolId(id);
        protocolRepository.deleteById(id);
    }

    public void start(Long id, boolean acknowledgeWarnings) {
        bindingWarningService.assertCanStart(id, acknowledgeWarnings);
        runtimeManager.start(id);
    }

    public void stop(Long id) {
        runtimeManager.stop(id);
    }

    public void restart(Long id, boolean acknowledgeWarnings) {
        bindingWarningService.assertCanStart(id, acknowledgeWarnings);
        runtimeManager.restart(id);
    }

    private ProtocolEntity find(Long id) {
        return protocolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + id));
    }

    private ProtocolDto toDto(ProtocolEntity p) {
        return new ProtocolDto(
                p.getId(), p.getName(), p.getDescription(), p.getStatus(), p.getStatusMessage(),
                p.isEnabled(), runtimeManager.isRunning(p.getId()),
                p.getSourceId(), p.getSourceTopics(), p.getOutputTargetId(), p.getCurrentVersionId(),
                p.getRingBufferSize(), p.getWorkerThreads(), p.getLogRetentionDays(), p.getSampleRate(),
                p.getGroupId(), p.getSortOrder());
    }

    private record UpdateResult(ProtocolEntity protocol, boolean reload) {}

    private void validateGroup(Long id) {
        if (id != null && !groups.existsById(id)) throw new IllegalArgumentException("分组不存在: " + id);
    }

    static boolean runtimeSettingsChanged(ProtocolEntity p, ProtocolRequest r) {
        return (r.sourceId != null && !Objects.equals(r.sourceId, p.getSourceId()))
                || (r.hasSourceTopics() && !Objects.equals(normalizeTopics(r.sourceTopics), p.getSourceTopics()))
                || (r.outputTargetId != null && !Objects.equals(r.outputTargetId, p.getOutputTargetId()))
                || (r.ringBufferSize != null && r.ringBufferSize != p.getRingBufferSize())
                || (r.workerThreads != null && r.workerThreads != p.getWorkerThreads())
                || (r.sampleRate != null && Double.compare(r.sampleRate, p.getSampleRate()) != 0);
    }

    /** 空白串统一存为 NULL（= 接收全部 topic）。 */
    private static String normalizeTopics(String topics) {
        return (topics == null || topics.isBlank()) ? null : topics.trim();
    }
}
