package com.bytd.forward.web;

import com.bytd.forward.runtime.source.HttpIngestRegistry;
import com.bytd.forward.runtime.source.HttpSourceConnector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 推送型数据源的统一接收端点。
 * 上游向 /ingest/{path} 推送数据：POST 取请求体原文，GET 把 query 参数序列化为 JSON。
 * 数据源未启动时返回 404；方法与配置不符时返回 405。
 */
@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final HttpIngestRegistry registry;
    private final ObjectMapper mapper;

    public IngestController(HttpIngestRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @PostMapping("/{path}")
    public ResponseEntity<Map<String, Object>> post(@PathVariable String path,
                                                    @RequestBody(required = false) String body) {
        return handle(path, "POST", body == null ? "" : body);
    }

    @GetMapping("/{path}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String path,
                                                   HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v == null || v.length == 0) {
                params.put(k, null);
            } else if (v.length == 1) {
                params.put(k, v[0]);
            } else {
                params.put(k, java.util.Arrays.asList(v));
            }
        });
        String payload;
        try {
            payload = mapper.writeValueAsString(params);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "参数序列化失败"));
        }
        return handle(path, "GET", payload);
    }

    private ResponseEntity<Map<String, Object>> handle(String path, String method, String payload) {
        HttpSourceConnector connector = registry.lookup(path);
        if (connector == null || !connector.isRunning()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "error", "接口未启用: /ingest/" + path));
        }
        if (!connector.getMethod().equals(method)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(Map.of("ok", false, "error", "该接口仅支持 " + connector.getMethod()));
        }
        try {
            connector.dispatch(payload, method);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
