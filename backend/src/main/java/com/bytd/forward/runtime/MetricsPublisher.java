package com.bytd.forward.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 周期性把每个协议的运行指标发布为 Micrometer gauge（带 protocolId 标签），
 * 供 Prometheus 抓取 /actuator/prometheus。
 */
@Component
public class MetricsPublisher {

    private final MeterRegistry registry;
    private final ProtocolRuntimeManager manager;

    private final Map<String, AtomicLong> holders = new ConcurrentHashMap<>();

    public MetricsPublisher(MeterRegistry registry, ProtocolRuntimeManager manager) {
        this.registry = registry;
        this.manager = manager;
    }

    @Scheduled(fixedRate = 5000)
    public void publish() {
        for (RuntimeStatus s : manager.allStatuses()) {
            String pid = String.valueOf(s.protocolId());
            gauge("forward.protocol.in", pid, s.in());
            gauge("forward.protocol.out", pid, s.out());
            gauge("forward.protocol.script_error", pid, s.scriptError());
            gauge("forward.protocol.timeout", pid, s.timeout());
            gauge("forward.protocol.sink_error", pid, s.sinkError());
            gauge("forward.protocol.buffer_remaining", pid, s.bufferRemaining());
            gauge("forward.protocol.avg_cost_ms", pid, Math.round(s.avgCostMs()));
        }
    }

    private void gauge(String name, String pid, long value) {
        String key = name + ":" + pid;
        AtomicLong holder = holders.computeIfAbsent(key, k -> {
            AtomicLong a = new AtomicLong();
            registry.gauge(name, Tags.of("protocolId", pid), a, AtomicLong::doubleValue);
            return a;
        });
        holder.set(value);
    }
}
