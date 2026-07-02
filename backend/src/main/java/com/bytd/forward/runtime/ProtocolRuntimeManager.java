package com.bytd.forward.runtime;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.log.AsyncLogWriter;
import com.bytd.forward.runtime.sink.SinkFactory;
import com.bytd.forward.runtime.source.SourceConnectorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议运行时的集中管理：启动/停止/重启/热更新，以及开机自启与优雅关闭。
 * 每个协议 id 上加锁，保证并发管理操作安全。
 */
@Service
public class ProtocolRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(ProtocolRuntimeManager.class);

    private final Map<Long, ProtocolRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private final ProtocolRepository protocolRepository;
    private final DataSourceRepository dataSourceRepository;
    private final OutputTargetRepository outputTargetRepository;
    private final ScriptVersionRepository scriptVersionRepository;
    private final ScriptEngineService engine;
    private final SourceConnectorFactory sourceFactory;
    private final SinkFactory sinkFactory;
    private final AsyncLogWriter logWriter;
    private final ObjectMapper mapper;
    private final ForwardProperties props;

    public ProtocolRuntimeManager(ProtocolRepository protocolRepository,
                                  DataSourceRepository dataSourceRepository,
                                  OutputTargetRepository outputTargetRepository,
                                  ScriptVersionRepository scriptVersionRepository,
                                  ScriptEngineService engine,
                                  SourceConnectorFactory sourceFactory,
                                  SinkFactory sinkFactory,
                                  AsyncLogWriter logWriter,
                                  ObjectMapper mapper,
                                  ForwardProperties props) {
        this.protocolRepository = protocolRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.outputTargetRepository = outputTargetRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.engine = engine;
        this.sourceFactory = sourceFactory;
        this.sinkFactory = sinkFactory;
        this.logWriter = logWriter;
        this.mapper = mapper;
        this.props = props;
    }

    private Object lockFor(Long id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    public void start(Long protocolId) {
        synchronized (lockFor(protocolId)) {
            ProtocolEntity p = protocolRepository.findById(protocolId)
                    .orElseThrow(() -> new IllegalArgumentException("协议不存在: " + protocolId));

            ProtocolRuntime existing = runtimes.get(protocolId);
            if (existing != null && "RUNNING".equals(existing.getStatus())) {
                return;
            }

            DataSourceEntity ds = requireSource(p);
            OutputTargetEntity target = requireTarget(p);
            String code = requireCode(p);

            ProtocolRuntime runtime = new ProtocolRuntime(
                    p.getId(), p.getName(), code, p.getSampleRate(),
                    p.getRingBufferSize(), p.getWorkerThreads(),
                    ds, target, engine, sourceFactory, sinkFactory, logWriter, mapper);

            try {
                runtime.start();
                runtimes.put(protocolId, runtime);
                p.setEnabled(true);
                p.setStatus("RUNNING");
                p.setStatusMessage(null);
                protocolRepository.save(p);
            } catch (Exception e) {
                p.setStatus("ERROR");
                p.setStatusMessage(e.getMessage());
                protocolRepository.save(p);
                throw e;
            }
        }
    }

    public void stop(Long protocolId) {
        synchronized (lockFor(protocolId)) {
            ProtocolRuntime runtime = runtimes.remove(protocolId);
            if (runtime != null) {
                runtime.stop();
            }
            protocolRepository.findById(protocolId).ifPresent(p -> {
                p.setEnabled(false);
                p.setStatus("STOPPED");
                p.setStatusMessage(null);
                protocolRepository.save(p);
            });
        }
    }

    public void restart(Long protocolId) {
        synchronized (lockFor(protocolId)) {
            ProtocolRuntime runtime = runtimes.remove(protocolId);
            if (runtime != null) {
                runtime.stop();
            }
            start(protocolId);
        }
    }

    /** 配置/脚本变更后调用：仅当协议在运行时才重启以应用新配置。 */
    public void reloadIfRunning(Long protocolId) {
        synchronized (lockFor(protocolId)) {
            ProtocolRuntime runtime = runtimes.get(protocolId);
            if (runtime != null && "RUNNING".equals(runtime.getStatus())) {
                restart(protocolId);
            }
        }
    }

    public boolean isRunning(Long protocolId) {
        ProtocolRuntime r = runtimes.get(protocolId);
        return r != null && "RUNNING".equals(r.getStatus());
    }

    public Optional<RuntimeStatus> status(Long protocolId) {
        ProtocolRuntime r = runtimes.get(protocolId);
        return r == null ? Optional.empty() : Optional.of(r.snapshot());
    }

    public List<RuntimeStatus> allStatuses() {
        List<RuntimeStatus> list = new ArrayList<>();
        runtimes.values().forEach(r -> list.add(r.snapshot()));
        return list;
    }

    private DataSourceEntity requireSource(ProtocolEntity p) {
        if (p.getSourceId() == null) {
            throw new IllegalStateException("协议未配置数据源");
        }
        return dataSourceRepository.findById(p.getSourceId())
                .orElseThrow(() -> new IllegalStateException("数据源不存在: " + p.getSourceId()));
    }

    private OutputTargetEntity requireTarget(ProtocolEntity p) {
        if (p.getOutputTargetId() == null) {
            throw new IllegalStateException("协议未配置输出目标");
        }
        return outputTargetRepository.findById(p.getOutputTargetId())
                .orElseThrow(() -> new IllegalStateException("输出目标不存在: " + p.getOutputTargetId()));
    }

    private String requireCode(ProtocolEntity p) {
        if (p.getCurrentVersionId() == null) {
            throw new IllegalStateException("协议未配置脚本版本");
        }
        ScriptVersionEntity v = scriptVersionRepository.findById(p.getCurrentVersionId())
                .orElseThrow(() -> new IllegalStateException("脚本版本不存在: " + p.getCurrentVersionId()));
        return v.getCode();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartEnabled() {
        List<ProtocolEntity> enabled = protocolRepository.findByEnabledTrue();
        for (ProtocolEntity p : enabled) {
            try {
                start(p.getId());
            } catch (Exception e) {
                log.error("开机自启协议[{}]失败: {}", p.getId(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdownAll() {
        runtimes.forEach((id, runtime) -> {
            try {
                runtime.stop();
            } catch (Exception e) {
                log.warn("关闭协议[{}]异常: {}", id, e.getMessage());
            }
        });
        runtimes.clear();
    }
}
