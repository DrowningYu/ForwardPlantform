package com.bytd.forward.runtime;

/**
 * 协议运行时状态快照（用于监控大盘）。
 */
public record RuntimeStatus(
        long protocolId,
        String protocolName,
        String status,
        String statusMessage,
        String sourceName,
        String sourceConfig,
        String sourceDesc,
        String sinkName,
        String sinkConfig,
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
        String lastError,
        /** 最近一次成功转发时间（epoch ms），null 表示运行以来尚未转发 */
        Long lastForwardAtMs
) {
}
