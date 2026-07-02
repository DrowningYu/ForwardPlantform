package com.bytd.forward.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class DebugRunRequest {
    public String code;
    /** 多条模拟输入；若为空则使用 input 单条 */
    public List<String> inputs;
    public String input;
    /** 可选：模拟 ctx 元数据 */
    public JsonNode ctx;
    /** 可选：覆盖执行超时(毫秒) */
    public Long timeoutMs;
}
