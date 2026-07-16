package com.bytd.forward.runtime.source;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.runtime.source.config.HttpSourceConfig;
import com.bytd.forward.runtime.source.config.KafkaSourceConfig;
import com.bytd.forward.runtime.source.config.MqttSourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SourceConnectorFactory {

    private final ObjectMapper mapper;
    private final HttpIngestRegistry httpIngestRegistry;

    public SourceConnectorFactory(ObjectMapper mapper, HttpIngestRegistry httpIngestRegistry) {
        this.mapper = mapper;
        this.httpIngestRegistry = httpIngestRegistry;
    }

    public SourceConnector create(DataSourceEntity ds, String threadName) {
        String type = ds.getType() == null ? "" : ds.getType().toUpperCase();
        try {
            return switch (type) {
                case "MQTT" -> new MqttSourceConnector(mapper.readValue(ds.getConfig(), MqttSourceConfig.class));
                case "KAFKA" -> new KafkaSourceConnector(mapper.readValue(ds.getConfig(), KafkaSourceConfig.class), threadName);
                case "HTTP" -> new HttpSourceConnector(mapper.readValue(ds.getConfig(), HttpSourceConfig.class), httpIngestRegistry);
                default -> throw new IllegalArgumentException("不支持的数据源类型: " + ds.getType());
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析数据源配置失败: " + e.getMessage(), e);
        }
    }
}
