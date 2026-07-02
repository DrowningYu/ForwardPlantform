package com.bytd.forward.runtime.sink.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaSinkConfig {
    public String bootstrapServers;
    public String topic;
    public String saslMechanism;
    public String securityProtocol;
    public String username;
    public String password;
}
