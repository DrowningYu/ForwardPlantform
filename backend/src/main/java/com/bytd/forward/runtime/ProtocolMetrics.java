package com.bytd.forward.runtime;

import java.util.concurrent.atomic.LongAdder;

/**
 * 单协议运行指标。热路径全部用 LongAdder，避免高并发下的 CAS 争用。
 */
public class ProtocolMetrics {

    private final LongAdder inCount = new LongAdder();
    private final LongAdder processedCount = new LongAdder();
    private final LongAdder scriptErrorCount = new LongAdder();
    private final LongAdder timeoutCount = new LongAdder();
    private final LongAdder totalCostMs = new LongAdder();
    private volatile String lastError;

    public void incrementIn() { inCount.increment(); }
    public void incrementProcessed() { processedCount.increment(); }
    public void incrementScriptError() { scriptErrorCount.increment(); }
    public void incrementTimeout() { timeoutCount.increment(); }
    public void addCost(long ms) { totalCostMs.add(ms); }
    public void setLastError(String e) { this.lastError = e; }

    public long getIn() { return inCount.sum(); }
    public long getProcessed() { return processedCount.sum(); }
    public long getScriptError() { return scriptErrorCount.sum(); }
    public long getTimeout() { return timeoutCount.sum(); }
    public long getTotalCostMs() { return totalCostMs.sum(); }
    public String getLastError() { return lastError; }

    public double getAvgCostMs() {
        long p = processedCount.sum();
        return p == 0 ? 0.0 : (double) totalCostMs.sum() / p;
    }
}
