package com.bytd.forward.runtime.sink;

import com.bytd.forward.engine.api.OutputSink;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 协议级输出路由：把脚本 output(data) 序列化后发到底层 Sink，并统计吞吐/错误。
 * 发送失败不抛回脚本（与现有服务的每条容错风格一致），仅计数与记录最后错误。
 */
public class SinkRouter implements OutputSink {

    private final Sink primary;
    private final ObjectMapper mapper;

    private final AtomicLong outCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    /** 最近一次成功转发的时间戳（毫秒），0 表示尚未转发。 */
    private final AtomicLong lastForwardAtMs = new AtomicLong();

    private volatile String lastError;

    public SinkRouter(Sink primary, ObjectMapper mapper) {
        this.primary = primary;
        this.mapper = mapper;
    }

    public void open() throws Exception {
        primary.open();
    }

    public void close() {
        primary.close();
    }

    @Override
    public void emit(String targetKey, Object data) {
        String payload = serialize(data);
        try {
            primary.send(payload);
            outCount.incrementAndGet();
            lastForwardAtMs.set(System.currentTimeMillis());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            lastError = e.getMessage();
        }
    }

    public String serialize(Object data) {
        if (data == null) {
            return "";
        }
        if (data instanceof String s) {
            return s;
        }
        if (data instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("输出数据序列化失败: " + e.getMessage(), e);
        }
    }

    public long getOutCount() { return outCount.get(); }
    public long getErrorCount() { return errorCount.get(); }
    public long getLastForwardAtMs() { return lastForwardAtMs.get(); }
    public String getLastError() { return lastError; }
    public String describe() { return primary.describe(); }
}
