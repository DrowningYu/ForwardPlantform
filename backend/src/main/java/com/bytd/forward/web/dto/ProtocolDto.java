package com.bytd.forward.web.dto;

public record ProtocolDto(
        Long id,
        String name,
        String description,
        String status,
        String statusMessage,
        boolean enabled,
        boolean running,
        Long sourceId,
        String sourceTopics,
        Long outputTargetId,
        Long currentVersionId,
        int ringBufferSize,
        int workerThreads,
        int logRetentionDays,
        double sampleRate
) {
}
