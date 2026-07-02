package com.bytd.forward.runtime.sink;

import com.bytd.forward.runtime.sink.config.MqttSinkConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 输出。Paho publish 本身线程安全，可被多 worker 并发调用。
 */
public class MqttSink implements Sink {

    private final MqttSinkConfig config;
    private volatile MqttClient client;

    public MqttSink(MqttSinkConfig config) {
        this.config = config;
    }

    @Override
    public void open() throws Exception {
        String[] servers = config.url.split(",");
        String primary = servers[0].trim();
        String clientId = (config.clientId == null || config.clientId.isBlank())
                ? "forward_sink_" + System.nanoTime() : config.clientId;
        client = new MqttClient(primary, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setMaxInflight(1000);
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
        client.connect(options);
    }

    @Override
    public void send(String payload) throws Exception {
        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(config.qos == null ? 1 : config.qos);
        msg.setRetained(Boolean.TRUE.equals(config.retained));
        MqttClient c = client;
        if (c == null) {
            throw new IllegalStateException("MQTT sink 未初始化");
        }
        if (!c.isConnected()) {
            c.reconnect();
        }
        c.publish(config.topic, msg);
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception ignore) {
                // ignore
            } finally {
                client = null;
            }
        }
    }

    @Override
    public String describe() {
        return "MQTT[" + config.url + " topic=" + config.topic + "]";
    }
}
