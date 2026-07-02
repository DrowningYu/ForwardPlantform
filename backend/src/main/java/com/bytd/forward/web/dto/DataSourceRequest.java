package com.bytd.forward.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class DataSourceRequest {
    public String name;
    public String type;      // MQTT / KAFKA
    public JsonNode config;  // 连接配置对象
}
