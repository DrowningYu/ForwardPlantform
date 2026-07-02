package com.bytd.forward.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 数据源 / 输出目标的通用返回：config 已解析为 JSON 对象，便于前端直接使用。
 */
public record ConfigCarrierDto(Long id, String name, String type, JsonNode config,
                               Instant createdAt, Instant updatedAt) {
}
