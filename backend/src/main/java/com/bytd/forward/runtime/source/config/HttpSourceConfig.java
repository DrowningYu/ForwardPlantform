package com.bytd.forward.runtime.source.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * HTTP 推送型数据源配置：平台在 /ingest/{path} 暴露接收端点，上游主动推送。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpSourceConfig {

    /** 接口名称（URL 路径段），如 "bridge-abc" -> POST /ingest/bridge-abc */
    public String path;

    /** GET / POST */
    public String method;
}
