package com.bytd.forward.runtime.sink;

import com.bytd.forward.runtime.sink.config.KafkaSinkConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Kafka 输出。KafkaProducer 线程安全且内部异步批量发送，适合高吞吐。
 */
public class KafkaSink implements Sink {

    private final KafkaSinkConfig config;
    private volatile KafkaProducer<String, String> producer;

    public KafkaSink(KafkaSinkConfig config) {
        this.config = config;
    }

    @Override
    public void open() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        if (config.saslMechanism != null && !config.saslMechanism.isBlank()) {
            props.put("sasl.mechanism", config.saslMechanism);
            props.put("security.protocol", config.securityProtocol == null ? "SASL_PLAINTEXT" : config.securityProtocol);
            String module = config.saslMechanism.startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule";
            props.put("sasl.jaas.config", module + " required username=\""
                    + config.username + "\" password=\"" + config.password + "\";");
        }
        producer = new KafkaProducer<>(props);
    }

    @Override
    public void send(String payload) {
        KafkaProducer<String, String> p = producer;
        if (p == null) {
            throw new IllegalStateException("Kafka sink 未初始化");
        }
        p.send(new ProducerRecord<>(config.topic, payload));
    }

    @Override
    public void close() {
        if (producer != null) {
            try {
                producer.flush();
                producer.close();
            } catch (Exception ignore) {
                // ignore
            } finally {
                producer = null;
            }
        }
    }

    @Override
    public String describe() {
        return "Kafka[" + config.bootstrapServers + " topic=" + config.topic + "]";
    }
}
