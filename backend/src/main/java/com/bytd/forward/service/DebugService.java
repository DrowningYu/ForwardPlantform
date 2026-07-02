package com.bytd.forward.service;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.CompiledScript;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.engine.ScriptExecutionResult;
import com.bytd.forward.engine.api.CollectingOutputSink;
import com.bytd.forward.engine.api.CollectingScriptLog;
import com.bytd.forward.engine.api.StateStore;
import com.bytd.forward.runtime.source.SourceConnector;
import com.bytd.forward.runtime.source.SourceConnectorFactory;
import com.bytd.forward.web.dto.DebugCaseResult;
import com.bytd.forward.web.dto.DebugRunRequest;
import com.bytd.forward.web.dto.DebugRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.lang.Binding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 调试：编译草稿脚本，在沙箱运行模拟输入，收集 output()/log 但不真正转发。
 * 支持从真实数据源抓取实时样本供调试。
 */
@Service
public class DebugService {

    private final ScriptEngineService engine;
    private final DataSourceRepository dataSourceRepository;
    private final SourceConnectorFactory sourceFactory;
    private final ObjectMapper mapper;
    private final ForwardProperties props;

    public DebugService(ScriptEngineService engine, DataSourceRepository dataSourceRepository,
                        SourceConnectorFactory sourceFactory, ObjectMapper mapper, ForwardProperties props) {
        this.engine = engine;
        this.dataSourceRepository = dataSourceRepository;
        this.sourceFactory = sourceFactory;
        this.mapper = mapper;
        this.props = props;
    }

    public DebugRunResult run(DebugRunRequest req) {
        CompileResult cr = engine.compile(req.code);
        if (!cr.isSuccess()) {
            return new DebugRunResult(false, cr.getError(), List.of());
        }
        CompiledScript compiled = cr.getCompiled();
        long timeout = req.timeoutMs != null ? req.timeoutMs : props.getScript().getExecTimeoutMs();

        List<String> inputs = new ArrayList<>();
        if (req.inputs != null && !req.inputs.isEmpty()) {
            inputs.addAll(req.inputs);
        } else if (req.input != null) {
            inputs.add(req.input);
        }

        Map<String, Object> ctx = buildCtx(req);
        // 同一次调试共享 state，便于测试跨消息聚合
        StateStore state = new StateStore();

        List<DebugCaseResult> cases = new ArrayList<>();
        try {
            for (String input : inputs) {
                cases.add(runOne(compiled, input, ctx, state, timeout));
            }
        } finally {
            compiled.close();
        }
        return new DebugRunResult(true, null, cases);
    }

    private DebugCaseResult runOne(CompiledScript compiled, String input, Map<String, Object> ctx,
                                   StateStore state, long timeout) {
        CollectingOutputSink sink = new CollectingOutputSink();
        CollectingScriptLog log = new CollectingScriptLog();
        Binding binding = engine.buildBinding(input, new HashMap<>(ctx), sink, log, state);
        ScriptExecutionResult r = engine.execute(compiled, binding, timeout);

        List<String> outputs = new ArrayList<>();
        for (CollectingOutputSink.Item item : sink.getItems()) {
            outputs.add(serialize(item.data()));
        }
        return new DebugCaseResult(input, outputs, log.getLogs(),
                r.isSuccess(), r.isTimeout(), r.getError(), r.getCostMs());
    }

    private String serialize(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    private Map<String, Object> buildCtx(DebugRunRequest req) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("source", "debug");
        ctx.put("receivedAt", System.currentTimeMillis());
        if (req.ctx != null && req.ctx.isObject()) {
            req.ctx.fields().forEachRemaining(e -> ctx.put(e.getKey(),
                    mapper.convertValue(e.getValue(), Object.class)));
        }
        return ctx;
    }

    /** 从真实数据源抓取最多 max 条样本，用于调试。 */
    public List<String> capture(Long sourceId, Integer max, Integer timeoutMs) throws Exception {
        int limit = Math.min(max == null ? 5 : max, props.getRuntime().getDebugCaptureMax());
        long deadline = System.currentTimeMillis() + (timeoutMs == null ? 10000 : timeoutMs);

        DataSourceEntity ds = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + sourceId));
        DataSourceEntity clone = withDebugIdentity(ds);

        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        SourceConnector connector = sourceFactory.create(clone, "debug-capture-" + sourceId);
        try {
            connector.start(m -> {
                if (queue.size() < limit) {
                    queue.offer(m.payload());
                }
            });
            while (queue.size() < limit && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
        } finally {
            connector.stop();
        }
        return new ArrayList<>(queue);
    }

    /** 复制数据源配置并替换 clientId/groupId，避免与运行中的实例冲突。 */
    private DataSourceEntity withDebugIdentity(DataSourceEntity ds) throws Exception {
        ObjectNode cfg = (ObjectNode) mapper.readTree(ds.getConfig() == null ? "{}" : ds.getConfig());
        String suffix = "_dbg_" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffff);
        String type = ds.getType() == null ? "" : ds.getType().toUpperCase();
        if ("MQTT".equals(type)) {
            String cid = cfg.hasNonNull("clientId") ? cfg.get("clientId").asText() : "forward_src";
            cfg.put("clientId", cid + suffix);
        } else if ("KAFKA".equals(type)) {
            String gid = cfg.hasNonNull("groupId") ? cfg.get("groupId").asText() : "forward_grp";
            cfg.put("groupId", gid + suffix);
            cfg.put("autoOffsetReset", "latest");
        }
        DataSourceEntity clone = new DataSourceEntity();
        clone.setName(ds.getName());
        clone.setType(ds.getType());
        clone.setConfig(cfg.toString());
        return clone;
    }
}
