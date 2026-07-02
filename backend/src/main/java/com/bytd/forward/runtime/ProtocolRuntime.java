package com.bytd.forward.runtime;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.CompiledScript;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.engine.api.ScriptLog;
import com.bytd.forward.engine.api.StateStore;
import com.bytd.forward.log.AsyncLogWriter;
import com.bytd.forward.log.ProtocolScriptLog;
import com.bytd.forward.runtime.sink.Sink;
import com.bytd.forward.runtime.sink.SinkFactory;
import com.bytd.forward.runtime.sink.SinkRouter;
import com.bytd.forward.runtime.source.SourceConnector;
import com.bytd.forward.runtime.source.SourceConnectorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个运行中的转发协议：数据源 -> Disruptor 环形缓冲(背压) -> Worker 池(脚本) -> 输出。
 */
public class ProtocolRuntime {

    private static final Logger log = LoggerFactory.getLogger(ProtocolRuntime.class);

    private final long protocolId;
    private final String name;
    private final String code;
    private final double sampleRate;
    private final int ringBufferSize;
    private final int workerThreads;

    private final DataSourceEntity dataSource;
    private final OutputTargetEntity outputTarget;

    private final ScriptEngineService engine;
    private final SourceConnectorFactory sourceFactory;
    private final SinkFactory sinkFactory;
    private final AsyncLogWriter logWriter;
    private final ObjectMapper mapper;

    private final ProtocolMetrics metrics = new ProtocolMetrics();
    private final StateStore state = new StateStore();

    private volatile String status = "STOPPED";
    private volatile String statusMessage;

    private volatile CompiledScript compiled;
    private SinkRouter router;
    private Disruptor<MessageEvent> disruptor;
    private RingBuffer<MessageEvent> ringBuffer;
    private SourceConnector connector;
    private ScriptLog scriptLog;

    public ProtocolRuntime(long protocolId, String name, String code, double sampleRate,
                           int ringBufferSize, int workerThreads,
                           DataSourceEntity dataSource, OutputTargetEntity outputTarget,
                           ScriptEngineService engine, SourceConnectorFactory sourceFactory,
                           SinkFactory sinkFactory, AsyncLogWriter logWriter, ObjectMapper mapper) {
        this.protocolId = protocolId;
        this.name = name;
        this.code = code;
        this.sampleRate = sampleRate;
        this.ringBufferSize = nextPowerOfTwo(Math.max(2, ringBufferSize));
        this.workerThreads = Math.max(1, workerThreads);
        this.dataSource = dataSource;
        this.outputTarget = outputTarget;
        this.engine = engine;
        this.sourceFactory = sourceFactory;
        this.sinkFactory = sinkFactory;
        this.logWriter = logWriter;
        this.mapper = mapper;
    }

    public synchronized void start() {
        if ("RUNNING".equals(status)) {
            return;
        }
        status = "STARTING";
        statusMessage = null;
        try {
            // 1. 编译脚本
            CompileResult cr = engine.compile(code);
            if (!cr.isSuccess()) {
                throw new IllegalStateException("脚本编译失败: " + cr.getError());
            }
            compiled = cr.getCompiled();

            // 2. 打开输出
            Sink sink = sinkFactory.create(outputTarget);
            router = new SinkRouter(sink, mapper);
            router.open();

            // 3. 日志通道
            scriptLog = new ProtocolScriptLog(protocolId, logWriter);

            // 4. Disruptor + Worker 池
            ThreadFactory tf = namedThreadFactory("proto-" + protocolId + "-worker");
            disruptor = new Disruptor<>(MessageEvent::new, ringBufferSize, tf,
                    ProducerType.MULTI, new BlockingWaitStrategy());

            ForwardWorkHandler[] handlers = new ForwardWorkHandler[workerThreads];
            for (int i = 0; i < workerThreads; i++) {
                handlers[i] = new ForwardWorkHandler(protocolId, engine, () -> compiled, router,
                        state, scriptLog, logWriter, sampleRate, metrics);
            }
            disruptor.handleEventsWithWorkerPool(handlers);
            disruptor.setDefaultExceptionHandler(new com.lmax.disruptor.ExceptionHandler<>() {
                @Override
                public void handleEventException(Throwable ex, long sequence, MessageEvent event) {
                    metrics.incrementScriptError();
                    metrics.setLastError(ex.getMessage());
                }
                @Override
                public void handleOnStartException(Throwable ex) {
                    log.error("Disruptor 启动异常 protocol={}: {}", protocolId, ex.getMessage());
                }
                @Override
                public void handleOnShutdownException(Throwable ex) {
                    log.warn("Disruptor 关闭异常 protocol={}: {}", protocolId, ex.getMessage());
                }
            });
            ringBuffer = disruptor.start();

            // 5. 启动数据源，消息发布到环形缓冲（满时阻塞 = 背压）
            connector = sourceFactory.create(dataSource, "proto-" + protocolId + "-source");
            connector.start(message -> ringBuffer.publishEvent((event, seq, m) -> event.setMessage(m), message));

            status = "RUNNING";
            logWriter.log(protocolId, "INFO", "协议已启动: 源=" + connector.describe() + " 出=" + router.describe());
            log.info("协议[{}] {} 已启动", protocolId, name);
        } catch (Exception e) {
            status = "ERROR";
            statusMessage = e.getMessage();
            log.error("协议[{}] {} 启动失败: {}", protocolId, name, e.getMessage(), e);
            safeCleanup();
            throw new RuntimeException("启动失败: " + e.getMessage(), e);
        }
    }

    public synchronized void stop() {
        try {
            if (connector != null) {
                connector.stop();
            }
            if (disruptor != null) {
                try {
                    disruptor.shutdown(10, TimeUnit.SECONDS);
                } catch (com.lmax.disruptor.TimeoutException te) {
                    disruptor.halt();
                }
            }
        } catch (Exception e) {
            log.warn("协议[{}] 停止时异常: {}", protocolId, e.getMessage());
        } finally {
            safeCleanup();
            status = "STOPPED";
            statusMessage = null;
            logWriter.log(protocolId, "INFO", "协议已停止");
            log.info("协议[{}] {} 已停止", protocolId, name);
        }
    }

    private void safeCleanup() {
        if (router != null) {
            router.close();
            router = null;
        }
        if (compiled != null) {
            compiled.close();
            compiled = null;
        }
        disruptor = null;
        ringBuffer = null;
        connector = null;
    }

    public RuntimeStatus snapshot() {
        return new RuntimeStatus(
                protocolId,
                name,
                status,
                statusMessage,
                dataSource.getName(),
                dataSource.getConfig(),
                connector == null ? "-" : connector.describe(),
                outputTarget.getName(),
                outputTarget.getConfig(),
                router == null ? "-" : router.describe(),
                metrics.getIn(),
                metrics.getProcessed(),
                router == null ? 0 : router.getOutCount(),
                metrics.getScriptError(),
                metrics.getTimeout(),
                router == null ? 0 : router.getErrorCount(),
                metrics.getAvgCostMs(),
                ringBufferSize,
                ringBuffer == null ? ringBufferSize : ringBuffer.remainingCapacity(),
                metrics.getLastError() != null ? metrics.getLastError()
                        : (router == null ? null : router.getLastError()),
                lastForwardAtMs(router)
        );
    }

    private static Long lastForwardAtMs(SinkRouter router) {
        if (router == null) {
            return null;
        }
        long ms = router.getLastForwardAtMs();
        return ms > 0 ? ms : null;
    }

    public String getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public long getProtocolId() { return protocolId; }
    public ProtocolMetrics getMetrics() { return metrics; }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger idx = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + idx.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static int nextPowerOfTwo(int v) {
        int n = 1;
        while (n < v) {
            n <<= 1;
        }
        return n;
    }
}
