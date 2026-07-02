package com.bytd.forward.runtime.sink.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpSinkConfig {
    public String url;
    public String method = "POST";
    public String contentType = "application/json";
    public Map<String, String> headers;
    public Integer timeoutMs = 5000;
}
