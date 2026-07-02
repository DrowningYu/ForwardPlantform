package com.bytd.forward.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台自定义配置，绑定 application.yml 中 forward.* 节点。
 */
@Component
@ConfigurationProperties(prefix = "forward")
public class ForwardProperties {

    private Log log = new Log();
    private Partition partition = new Partition();
    private Script script = new Script();
    private Runtime runtime = new Runtime();

    public Log getLog() { return log; }
    public void setLog(Log log) { this.log = log; }
    public Partition getPartition() { return partition; }
    public void setPartition(Partition partition) { this.partition = partition; }
    public Script getScript() { return script; }
    public void setScript(Script script) { this.script = script; }
    public Runtime getRuntime() { return runtime; }
    public void setRuntime(Runtime runtime) { this.runtime = runtime; }

    public static class Log {
        private int queueCapacity = 100000;
        private int batchSize = 500;
        private long flushIntervalMs = 1000;

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    }

    public static class Partition {
        private int preCreateDays = 3;
        private int minRetentionDays = 1;

        public int getPreCreateDays() { return preCreateDays; }
        public void setPreCreateDays(int preCreateDays) { this.preCreateDays = preCreateDays; }
        public int getMinRetentionDays() { return minRetentionDays; }
        public void setMinRetentionDays(int minRetentionDays) { this.minRetentionDays = minRetentionDays; }
    }

    public static class Script {
        private long execTimeoutMs = 3000;
        private int compiledCacheSize = 256;

        public long getExecTimeoutMs() { return execTimeoutMs; }
        public void setExecTimeoutMs(long execTimeoutMs) { this.execTimeoutMs = execTimeoutMs; }
        public int getCompiledCacheSize() { return compiledCacheSize; }
        public void setCompiledCacheSize(int compiledCacheSize) { this.compiledCacheSize = compiledCacheSize; }
    }

    public static class Runtime {
        private int defaultRingBufferSize = 16384;
        private int defaultWorkerThreads = 4;
        private int debugCaptureMax = 20;

        public int getDefaultRingBufferSize() { return defaultRingBufferSize; }
        public void setDefaultRingBufferSize(int defaultRingBufferSize) { this.defaultRingBufferSize = defaultRingBufferSize; }
        public int getDefaultWorkerThreads() { return defaultWorkerThreads; }
        public void setDefaultWorkerThreads(int defaultWorkerThreads) { this.defaultWorkerThreads = defaultWorkerThreads; }
        public int getDebugCaptureMax() { return debugCaptureMax; }
        public void setDebugCaptureMax(int debugCaptureMax) { this.debugCaptureMax = debugCaptureMax; }
    }
}
