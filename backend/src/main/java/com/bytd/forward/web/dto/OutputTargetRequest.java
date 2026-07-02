package com.bytd.forward.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class OutputTargetRequest {
    public String name;
    public String type;      // MQTT / KAFKA / HTTP
    public JsonNode config;
}
