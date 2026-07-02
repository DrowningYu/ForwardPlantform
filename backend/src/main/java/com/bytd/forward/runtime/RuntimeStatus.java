package com.bytd.forward.runtime;

/**
 * 协议运行时状态快照（用于监控大盘）。
 */
public record RuntimeStatus(
        long protocolId,
        String status,
        String statusMessage,
        String sourceDesc,
        String sinkDesc,
        long in,
        long processed,
        long out,
        long scriptError,
        long timeout,
        long sinkError,
        double avgCostMs,
        int bufferSize,
        long bufferRemaining,
        String lastError
) {
}
