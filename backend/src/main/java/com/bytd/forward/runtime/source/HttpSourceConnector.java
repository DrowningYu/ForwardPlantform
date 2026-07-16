package com.bytd.forward.runtime.source;

import com.bytd.forward.runtime.source.config.HttpSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 推送型数据源：start 时把 path 注册到 {@link HttpIngestRegistry}，
 * 上游向 /ingest/{path} 推送数据，由 IngestController 调用 {@link #dispatch}。
 * 下游环形缓冲满时 dispatch 阻塞（即 HTTP 请求线程等待），形成背压。
 */
public class HttpSourceConnector implements SourceConnector {

    private static final Logger log = LoggerFactory.getLogger(HttpSourceConnector.class);

    private final HttpSourceConfig config;
    private final HttpIngestRegistry registry;
    private final String path;

    private volatile SourceMessageHandler handler;
    private volatile boolean running;

    public HttpSourceConnector(HttpSourceConfig config, HttpIngestRegistry registry) {
        this.config = config;
        this.registry = registry;
        this.path = normalizePath(config.path);
    }

    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("HTTP 数据源未配置接口名称(path)");
        }
        String p = raw.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isBlank()) {
            throw new IllegalArgumentException("HTTP 数据源接口名称(path)无效");
        }
        return p;
    }

    public String getPath() {
        return path;
    }

    /** 配置的 HTTP 方法，默认 POST。 */
    public String getMethod() {
        return (config.method == null || config.method.isBlank()) ? "POST" : config.method.trim().toUpperCase();
    }

    @Override
    public void start(SourceMessageHandler handler) {
        this.handler = handler;
        registry.register(path, this);
        running = true;
        log.info("HTTP 源已注册 /ingest/{} method={}", path, getMethod());
    }

    @Override
    public void stop() {
        running = false;
        registry.unregister(path, this);
        handler = null;
        log.info("HTTP 源已注销 /ingest/{}", path);
    }

    /** IngestController 收到请求后调用；缓冲满会阻塞请求线程（背压）。 */
    public void dispatch(String payload, String method) {
        SourceMessageHandler h = handler;
        if (!running || h == null) {
            throw new IllegalStateException("HTTP 数据源未运行");
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("topic", path);
        ctx.put("source", "http");
        ctx.put("method", method);
        ctx.put("receivedAt", System.currentTimeMillis());
        h.onMessage(new SourceMessage(payload, ctx));
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String describe() {
        return "HTTP[/ingest/" + path + " method=" + getMethod() + "]";
    }
}
