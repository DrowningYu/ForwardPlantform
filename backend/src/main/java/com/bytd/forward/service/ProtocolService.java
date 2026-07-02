package com.bytd.forward.service;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.ProtocolDto;
import com.bytd.forward.web.dto.ProtocolRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public ProtocolService(ProtocolRepository protocolRepository,
                           ScriptVersionRepository versionRepository,
                           ProtocolRuntimeManager runtimeManager,
                           ScriptEngineService engine,
                           ForwardProperties props) {
        this.protocolRepository = protocolRepository;
        this.versionRepository = versionRepository;
        this.runtimeManager = runtimeManager;
        this.engine = engine;
        this.props = props;
    }

    public List<ProtocolDto> list() {
        return protocolRepository.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public ProtocolDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public ProtocolDto create(ProtocolRequest req) {
        ProtocolEntity p = new ProtocolEntity();
        p.setName(req.name);
        p.setDescription(req.description);
        p.setSourceId(req.sourceId);
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

    @Transactional
    public ProtocolDto update(Long id, ProtocolRequest req) {
        ProtocolEntity p = find(id);
        if (req.name != null) p.setName(req.name);
        p.setDescription(req.description);
        if (req.sourceId != null) p.setSourceId(req.sourceId);
        if (req.outputTargetId != null) p.setOutputTargetId(req.outputTargetId);
        if (req.ringBufferSize != null) p.setRingBufferSize(req.ringBufferSize);
        if (req.workerThreads != null) p.setWorkerThreads(req.workerThreads);
        if (req.logRetentionDays != null) p.setLogRetentionDays(req.logRetentionDays);
        if (req.sampleRate != null) p.setSampleRate(req.sampleRate);
        p = protocolRepository.save(p);
        runtimeManager.reloadIfRunning(id);
        return toDto(p);
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

    public void start(Long id) {
        runtimeManager.start(id);
    }

    public void stop(Long id) {
        runtimeManager.stop(id);
    }

    public void restart(Long id) {
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
                p.getSourceId(), p.getOutputTargetId(), p.getCurrentVersionId(),
                p.getRingBufferSize(), p.getWorkerThreads(), p.getLogRetentionDays(), p.getSampleRate());
    }
}
