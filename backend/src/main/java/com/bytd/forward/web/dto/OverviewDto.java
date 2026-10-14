package com.bytd.forward.web.dto;

public record OverviewDto(
        int runningCount,
        long totalIn,
        long totalOut,
        long totalScriptError,
        long totalTimeout,
        long totalSinkError,
        int logQueueSize,
        int recordQueueSize,
        long droppedLogs,
        long droppedRecords,
        SystemResourceDto systemResource,
        String logWriteStatus,
        java.time.Instant logLastSuccessAt,
        long logWriteFailures,
        String logLastFailure
) {
}
