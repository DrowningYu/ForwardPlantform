package com.bytd.forward.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProtocolRequest {
    public String name;
    public String description;
    public Long sourceId;
    public String sourceTopics;
    public Long outputTargetId;
    public Integer ringBufferSize;
    public Integer workerThreads;
    public Integer logRetentionDays;
    public Double sampleRate;
}
