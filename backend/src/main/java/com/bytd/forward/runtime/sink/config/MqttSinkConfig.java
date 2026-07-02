package com.bytd.forward.runtime.sink.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttSinkConfig {
    public String url;
    public String clientId;
    public String topic;
    public Integer qos = 1;
    public String username;
    public String password;
    public Boolean retained = false;
}
