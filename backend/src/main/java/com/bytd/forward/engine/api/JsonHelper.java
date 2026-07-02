package com.bytd.forward.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 注入脚本的 json 辅助对象：json.parse(str) / json.stringify(obj)。线程安全，可全局共享。
 */
public class JsonHelper {

    private final ObjectMapper mapper;

    public JsonHelper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 解析 JSON 字符串为 Map/List/基本类型。 */
    public Object parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return mapper.readValue(text, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** 将对象序列化为 JSON 字符串。 */
    public String stringify(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败: " + e.getMessage(), e);
        }
    }
}
