package com.bytd.forward.runtime;

import com.bytd.forward.engine.CompiledScript;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.engine.ScriptExecutionResult;
import com.bytd.forward.engine.api.OutputSink;
import com.bytd.forward.engine.api.ScriptLog;
import com.bytd.forward.engine.api.StateStore;
import com.bytd.forward.log.AsyncLogWriter;
import com.bytd.forward.log.ForwardRecordRow;
import com.bytd.forward.runtime.sink.CollectingSink;
import com.bytd.forward.runtime.sink.SinkRouter;
import com.lmax.disruptor.WorkHandler;
import groovy.lang.Binding;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Disruptor WorkHandler：每个事件被池中某一个 worker 独占处理，实现并行消费。
 * 负责执行脚本、更新指标、按采样率写入明细、异常写日志。
 */
public class ForwardWorkHandler implements WorkHandler<MessageEvent> {

    private final long protocolId;
    private final ScriptEngineService engine;
    private final Supplier<CompiledScript> compiledSupplier;
    private final SinkRouter router;
    private final StateStore state;
    private final ScriptLog scriptLog;
    private final AsyncLogWriter logWriter;
    private final double sampleRate;
    private final ProtocolMetrics metrics;

    public ForwardWorkHandler(long protocolId, ScriptEngineService engine,
                              Supplier<CompiledScript> compiledSupplier, SinkRouter router,
                              StateStore state, ScriptLog scriptLog, AsyncLogWriter logWriter,
                              double sampleRate, ProtocolMetrics metrics) {
        this.protocolId = protocolId;
        this.engine = engine;
        this.compiledSupplier = compiledSupplier;
        this.router = router;
        this.state = state;
        this.scriptLog = scriptLog;
        this.logWriter = logWriter;
        this.sampleRate = sampleRate;
        this.metrics = metrics;
    }

    @Override
    public void onEvent(MessageEvent event) {
        var message = event.getMessage();
        event.clear();
        if (message == null) {
            return;
        }
        metrics.incrementIn();

        boolean sampled = sampleRate >= 1.0
                || (sampleRate > 0 && ThreadLocalRandom.current().nextDouble() < sampleRate);
        CollectingSink collecting = sampled ? new CollectingSink(router) : null;
        OutputSink sink = sampled ? collecting : router;

        CompiledScript compiled = compiledSupplier.get();
        Binding binding = engine.buildBinding(message.payload(), message.ctx(), sink, scriptLog, state);
        ScriptExecutionResult result = engine.execute(compiled, binding);

        metrics.incrementProcessed();
        metrics.addCost(result.getCostMs());

        if (!result.isSuccess()) {
            if (result.isTimeout()) {
                metrics.incrementTimeout();
            } else {
                metrics.incrementScriptError();
            }
            metrics.setLastError(result.getError());
            logWriter.log(protocolId, "ERROR", "脚本执行失败: " + result.getError());
        }

        if (sampled) {
            String output = String.join("\n", collecting.getCollected());
            logWriter.record(new ForwardRecordRow(protocolId, message.payload(), output,
                    result.isSuccess(), result.getError(), (int) result.getCostMs(), Instant.now()));
        }
    }
}
