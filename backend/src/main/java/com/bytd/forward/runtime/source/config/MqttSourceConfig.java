package com.bytd.forward.runtime.source.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * MQTT 数据源配置（对应 data_source.config JSONB）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttSourceConfig {
    /** 支持逗号分隔多个 broker 做 failover，例如 tcp://a:1883,tcp://b:1883 */
    public String url;
    public String clientId;
    /** 支持 | 或 , 分隔多个主题 */
    public String topics;
    public Integer qos = 1;
    public String username;
    public String password;
}
