package com.bytd.forward.runtime.source;

import com.bytd.forward.runtime.source.config.MqttSourceConfig;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Eclipse Paho 的 MQTT 数据源。收到消息后在回调线程调用 handler，
 * 若下游环形缓冲满会阻塞该回调，从而对上游形成背压。
 */
public class MqttSourceConnector implements SourceConnector {

    private static final Logger log = LoggerFactory.getLogger(MqttSourceConnector.class);

    private final MqttSourceConfig config;
    private volatile MqttClient client;
    private volatile boolean running;

    public MqttSourceConnector(MqttSourceConfig config) {
        this.config = config;
    }

    @Override
    public void start(SourceMessageHandler handler) throws Exception {
        String[] servers = config.url.split(",");
        String primary = servers[0].trim();
        String clientId = (config.clientId == null || config.clientId.isBlank())
                ? "forward_src_" + System.nanoTime() : config.clientId;

        client = new MqttClient(primary, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        if (servers.length > 1) {
            String[] uris = new String[servers.length];
            for (int i = 0; i < servers.length; i++) {
                uris[i] = servers[i].trim();
            }
            options.setServerURIs(uris);
        }
        if (config.username != null && !config.username.isBlank()) {
            options.setUserName(config.username);
            if (config.password != null) {
                options.setPassword(config.password.toCharArray());
            }
        }

        String[] topics = splitTopics(config.topics);
        int[] qos = new int[topics.length];
        int q = config.qos == null ? 1 : config.qos;
        for (int i = 0; i < topics.length; i++) {
            qos[i] = q;
        }

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // cleanSession=true 时重连后 Broker 无会话，必须重新订阅，否则断线后不再收数据
                if (reconnect) {
                    try {
                        client.subscribe(topics, qos);
                        log.info("MQTT 源重连成功并重新订阅 {} topics={}", serverURI, String.join(",", topics));
                    } catch (Exception e) {
                        log.error("MQTT 源重连后重新订阅失败: {}", e.getMessage());
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 连接断开: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("topic", topic);
                ctx.put("source", "mqtt");
                ctx.put("receivedAt", System.currentTimeMillis());
                ctx.put("qos", message.getQos());
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                handler.onMessage(new SourceMessage(payload, ctx));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 仅订阅，无需处理
            }
        });

        client.connect(options);
        client.subscribe(topics, qos);
        running = true;
        log.info("MQTT 源已连接 {} 订阅 {}", primary, String.join(",", topics));
    }

    static String[] splitTopics(String topics) {
        if (topics == null || topics.isBlank()) {
            return new String[0];
        }
        return topics.split("[|,]");
    }

    @Override
    public void stop() {
        running = false;
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception e) {
                log.warn("关闭 MQTT 源异常: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running && client != null && client.isConnected();
    }

    @Override
    public String describe() {
        return "MQTT[" + config.url + " topics=" + config.topics + "]";
    }
}
