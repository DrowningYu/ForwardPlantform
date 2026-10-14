package com.bytd.forward.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProtocolRequest {
    public String name;
    public String description;
    public Integer sortOrder;
    private Long groupId;
    private boolean groupIdPresent;

    @com.fasterxml.jackson.annotation.JsonSetter("groupId")
    public void setGroupId(Long groupId) { this.groupId = groupId; this.groupIdPresent = true; }
    public Long getGroupId() { return groupId; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasGroupId() { return groupIdPresent; }
    public Long sourceId;
    public String sourceTopics;
    private boolean sourceTopicsPresent;
    @com.fasterxml.jackson.annotation.JsonSetter("sourceTopics")
    public void setSourceTopics(String value) { sourceTopics = value; sourceTopicsPresent = true; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasSourceTopics() { return sourceTopicsPresent || sourceTopics != null; }
    public Long outputTargetId;
    public Integer ringBufferSize;
    public Integer workerThreads;
    public Integer logRetentionDays;
    public Double sampleRate;
}
