package com.bytd.forward.engine;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.engine.api.CollectingOutputSink;
import com.bytd.forward.engine.api.CollectingScriptLog;
import com.bytd.forward.engine.api.JsonHelper;
import com.bytd.forward.engine.api.StateStore;
import com.bytd.forward.engine.api.TimeHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯脚本引擎回归（不依赖 DB/MQTT）：验证 output/json/time/state 与沙箱，
 * 并用 bridge_sywyhq 平铺、tunnel_bdl 时间戳×10 的迁移逻辑作为样例。
 */
class ScriptEngineRegressionTest {

    private static ScriptEngineService engine;
    private static ScheduledExecutorService watchdog;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setup() {
        ForwardProperties props = new ForwardProperties();
        watchdog = Executors.newScheduledThreadPool(1);
        engine = new ScriptEngineService(
                new GroovyScriptCompiler(),
                new CompiledScriptCache(props),
                new ScriptExecutor(watchdog),
                new JsonHelper(MAPPER),
                new TimeHelper(),
                props);
    }

    @AfterAll
    static void tearDown() {
        watchdog.shutdownNow();
    }

    private DebugResult exec(String code, String msg, Map<String, Object> ctx, StateStore state) {
        CompileResult cr = engine.compile(code);
        assertTrue(cr.isSuccess(), "编译应成功: " + cr.getError());
        CollectingOutputSink sink = new CollectingOutputSink();
        CollectingScriptLog log = new CollectingScriptLog();
        Binding binding = engine.buildBinding(msg, ctx, sink, log, state);
        ScriptExecutionResult r = engine.execute(cr.getCompiled(), binding, 3000);
        cr.getCompiled().close();
        return new DebugResult(r, sink, log);
    }

    @Test
    void sywyhqFlatten() {
        String code = """
                def obj = json.parse(msg)
                obj.data.each { d ->
                    def m = [device_id: obj.deviceId, deviceType: obj.deviceType]
                    int i = 1
                    d.each { k, v -> if (k != 'time') { m['data' + (i++)] = v } }
                    m.timestamp = d.time
                    output(m)
                }
                """;
        String msg = "{\"deviceType\":1,\"deviceId\":\"SYWYHQ-DIS-G01-001-01\","
                + "\"data\":[{\"time\":1711624530000,\"temperature\":22.41,\"humidity\":45.3}]}";

        DebugResult res = exec(code, msg, ctx(), new StateStore());
        assertTrue(res.result.isSuccess(), res.result.getError());
        assertEquals(1, res.sink.getItems().size());

        Map<?, ?> out = (Map<?, ?>) res.sink.getItems().get(0).data();
        assertEquals("SYWYHQ-DIS-G01-001-01", out.get("device_id"));
        assertEquals(1711624530000L, ((Number) out.get("timestamp")).longValue());
        assertEquals(22.41, ((Number) out.get("data1")).doubleValue(), 1e-6);
        assertEquals(45.3, ((Number) out.get("data2")).doubleValue(), 1e-6);
    }

    @Test
    void tunnelTimestampTimesTen() {
        String code = """
                def obj = json.parse(msg)
                output([
                    device_id: obj.deviceId,
                    flag: 'vibratingwire',
                    timestamp: time.toEpochMillis(obj.ts),
                    data1: obj.freq,
                    datat: obj.temp
                ])
                """;
        // 12 位时间戳 ×10 → 13 位毫秒
        String msg = "{\"deviceId\":\"K56_970_3\",\"ts\":\"178224600059\",\"freq\":911.16,\"temp\":25.9}";

        DebugResult res = exec(code, msg, ctx(), new StateStore());
        assertTrue(res.result.isSuccess(), res.result.getError());
        Map<?, ?> out = (Map<?, ?>) res.sink.getItems().get(0).data();
        assertEquals(1782246000590L, ((Number) out.get("timestamp")).longValue());
    }

    @Test
    void stateAggregationAcrossMessages() {
        String code = """
                def obj = json.parse(msg)
                state.put(obj.axis, obj.value)
                if (state.get('X') != null && state.get('Y') != null) {
                    output([valueX: state.get('X'), valueY: state.get('Y')])
                }
                """;
        StateStore state = new StateStore();
        exec(code, "{\"axis\":\"X\",\"value\":1.1}", ctx(), state);
        DebugResult res = exec(code, "{\"axis\":\"Y\",\"value\":2.2}", ctx(), state);
        assertEquals(1, res.sink.getItems().size());
        Map<?, ?> out = (Map<?, ?>) res.sink.getItems().get(0).data();
        assertEquals(1.1, ((Number) out.get("valueX")).doubleValue(), 1e-6);
        assertEquals(2.2, ((Number) out.get("valueY")).doubleValue(), 1e-6);
    }

    @Test
    void sandboxBlocksDangerousApi() {
        // 直接调用 System.exit 应被沙箱拒绝（编译失败）
        CompileResult cr = engine.compile("System.exit(1)");
        assertFalse(cr.isSuccess(), "System.exit 应被沙箱拦截");
    }

    private Map<String, Object> ctx() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("source", "test");
        ctx.put("receivedAt", System.currentTimeMillis());
        return ctx;
    }

    private record DebugResult(ScriptExecutionResult result, CollectingOutputSink sink, CollectingScriptLog log) {}
}
