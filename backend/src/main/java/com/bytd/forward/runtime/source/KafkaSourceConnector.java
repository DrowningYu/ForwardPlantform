package com.bytd.forward.runtime.source;

import com.bytd.forward.runtime.source.config.KafkaSourceConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 基于原生 KafkaConsumer 的数据源，独立线程 poll 循环。
 * 下游环形缓冲满时 handler 阻塞，poll 循环自然减速，形成背压。
 */
public class KafkaSourceConnector implements SourceConnector {

    private static final Logger log = LoggerFactory.getLogger(KafkaSourceConnector.class);

    private final KafkaSourceConfig config;
    private final String threadName;
    private volatile boolean running;
    private Thread pollThread;
    private KafkaConsumer<String, String> consumer;

    public KafkaSourceConnector(KafkaSourceConfig config, String threadName) {
        this.config = config;
        this.threadName = threadName;
    }

    @Override
    public void start(SourceMessageHandler handler) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                config.autoOffsetReset == null ? "latest" : config.autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                config.maxPollRecords == null ? 500 : config.maxPollRecords);

        if (config.saslMechanism != null && !config.saslMechanism.isBlank()) {
            props.put("sasl.mechanism", config.saslMechanism);
            props.put("security.protocol", config.securityProtocol == null ? "SASL_PLAINTEXT" : config.securityProtocol);
            String module = config.saslMechanism.startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule";
            props.put("sasl.jaas.config", module + " required username=\""
                    + config.username + "\" password=\"" + config.password + "\";");
        }

        String[] topics = MqttSourceConnector.splitTopics(config.topics);
        running = true;
        pollThread = new Thread(() -> runLoop(props, topics, handler), threadName);
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void runLoop(Properties props, String[] topics, SourceMessageHandler handler) {
        try {
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(topics));
            log.info("Kafka 源已启动 topics={} group={}", Arrays.toString(topics), config.groupId);
            while (running) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        Map<String, Object> ctx = new HashMap<>();
                        ctx.put("topic", record.topic());
                        ctx.put("source", "kafka");
                        ctx.put("partition", record.partition());
                        ctx.put("offset", record.offset());
                        ctx.put("key", record.key());
                        ctx.put("receivedAt", System.currentTimeMillis());
                        handler.onMessage(new SourceMessage(record.value(), ctx));
                    }
                } catch (org.apache.kafka.common.errors.WakeupException we) {
                    break;
                } catch (Exception e) {
                    log.error("Kafka poll 异常: {}", e.getMessage());
                    sleep(5000);
                }
            }
        } catch (Exception e) {
            log.error("Kafka 消费者启动失败: {}", e.getMessage(), e);
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (consumer != null) {
            try {
                consumer.wakeup();
            } catch (Exception ignore) {
                // ignore
            }
        }
        if (pollThread != null) {
            try {
                pollThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String describe() {
        return "Kafka[" + config.bootstrapServers + " topics=" + config.topics + " group=" + config.groupId + "]";
    }
}
