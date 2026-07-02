package com.bytd.forward.runtime.sink;

import com.bytd.forward.runtime.sink.config.HttpSinkConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 输出。共享一个 HttpClient（内部连接池），线程安全。
 */
public class HttpSink implements Sink {

    private final HttpSinkConfig config;
    private volatile HttpClient httpClient;

    public HttpSink(HttpSinkConfig config) {
        this.config = config;
    }

    @Override
    public void open() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs == null ? 5000 : config.timeoutMs))
                .build();
    }

    @Override
    public void send(String payload) throws Exception {
        HttpClient c = httpClient;
        if (c == null) {
            throw new IllegalStateException("HTTP sink 未初始化");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.url))
                .timeout(Duration.ofMillis(config.timeoutMs == null ? 5000 : config.timeoutMs))
                .header("Content-Type", config.contentType == null ? "application/json" : config.contentType);
        if (config.headers != null) {
            config.headers.forEach(builder::header);
        }
        String method = config.method == null ? "POST" : config.method.toUpperCase();
        HttpRequest request = switch (method) {
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(payload)).build();
            case "GET" -> builder.GET().build();
            default -> builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        };
        HttpResponse<String> resp = c.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new RuntimeException("HTTP 输出返回状态码 " + resp.statusCode());
        }
    }

    @Override
    public void close() {
        httpClient = null;
    }

    @Override
    public String describe() {
        return "HTTP[" + config.method + " " + config.url + "]";
    }
}
