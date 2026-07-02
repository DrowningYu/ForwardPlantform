package com.bytd.forward.web.dto;

public class ProtocolRequest {
    public String name;
    public String description;
    public Long sourceId;
    public Long outputTargetId;
    public Integer ringBufferSize;
    public Integer workerThreads;
    public Integer logRetentionDays;
    public Double sampleRate;
}
