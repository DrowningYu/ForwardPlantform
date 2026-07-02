package com.bytd.forward.runtime.source;

import java.util.Map;

/**
 * 从数据源收到的一条原始消息。
 *
 * @param payload 原始报文（字符串）
 * @param ctx     元数据：topic、来源、接收时间、分区/offset 等
 */
public record SourceMessage(String payload, Map<String, Object> ctx) {
}
