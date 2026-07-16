package com.bytd.forward.runtime.source;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 推送型数据源的路径注册表：IngestController 按 path 查找已启动的连接器。
 */
@Component
public class HttpIngestRegistry {

    private final Map<String, HttpSourceConnector> byPath = new ConcurrentHashMap<>();

    /** @throws IllegalStateException path 已被其他数据源占用 */
    public void register(String path, HttpSourceConnector connector) {
        HttpSourceConnector prev = byPath.putIfAbsent(path, connector);
        if (prev != null && prev != connector) {
            throw new IllegalStateException("HTTP 接口路径已被占用: /ingest/" + path);
        }
    }

    public void unregister(String path, HttpSourceConnector connector) {
        byPath.remove(path, connector);
    }

    public HttpSourceConnector lookup(String path) {
        return byPath.get(path);
    }
}
