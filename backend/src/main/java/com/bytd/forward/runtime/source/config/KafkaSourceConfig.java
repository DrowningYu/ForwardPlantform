package com.bytd.forward.runtime.source.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kafka 数据源配置（对应 data_source.config JSONB）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaSourceConfig {
    public String bootstrapServers;
    /** 支持 | 或 , 分隔多个主题 */
    public String topics;
    public String groupId;
    public String autoOffsetReset = "latest";
    /** SASL：为空则不启用认证 */
    public String saslMechanism;        // 如 SCRAM-SHA-256
    public String securityProtocol;     // 如 SASL_PLAINTEXT
    public String username;
    public String password;
    public Integer maxPollRecords = 500;
}
