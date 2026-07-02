package com.bytd.forward.runtime.sink;

import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.runtime.sink.config.HttpSinkConfig;
import com.bytd.forward.runtime.sink.config.KafkaSinkConfig;
import com.bytd.forward.runtime.sink.config.MqttSinkConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SinkFactory {

    private final ObjectMapper mapper;

    public SinkFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Sink create(OutputTargetEntity target) {
        String type = target.getType() == null ? "" : target.getType().toUpperCase();
        try {
            return switch (type) {
                case "MQTT" -> new MqttSink(mapper.readValue(target.getConfig(), MqttSinkConfig.class));
                case "KAFKA" -> new KafkaSink(mapper.readValue(target.getConfig(), KafkaSinkConfig.class));
                case "HTTP" -> new HttpSink(mapper.readValue(target.getConfig(), HttpSinkConfig.class));
                default -> throw new IllegalArgumentException("不支持的输出目标类型: " + target.getType());
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析输出目标配置失败: " + e.getMessage(), e);
        }
    }
}
