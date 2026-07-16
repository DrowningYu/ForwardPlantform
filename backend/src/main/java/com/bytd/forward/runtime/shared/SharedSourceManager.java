package com.bytd.forward.runtime.shared;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.runtime.source.SourceConnector;
import com.bytd.forward.runtime.source.SourceConnectorFactory;
import com.bytd.forward.runtime.source.SourceMessage;
import com.bytd.forward.runtime.source.SourceMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 平台级共享数据源：每个 data_source 只维护一条真实连接（MQTT/Kafka/HTTP），
 * 收到的消息按各协议的 topic 过滤复制分发（fan-out）。
 *
 * <p>背压语义：分发按订阅者顺序串行调用 handler（即协议的 ringBuffer.publishEvent，
 * 队列满时阻塞），因此同一数据源下某个慢协议会拖慢该源的整体消费——与既有
 * 单协议阻塞背压语义一致，保证不丢数据。</p>
 */
@Service
public class SharedSourceManager {

    private static final Logger log = LoggerFactory.getLogger(SharedSourceManager.class);

    private final SourceConnectorFactory factory;
    private final Map<Long, SharedSource> sources = new ConcurrentHashMap<>();

    public SharedSourceManager(SourceConnectorFactory factory) {
        this.factory = factory;
    }

    /**
     * 订阅数据源。第一个订阅者触发建连；配置与当前连接不一致时自动重建连接。
     *
     * @param topicFilters 空列表 = 接收全部 topic
     */
    public synchronized SourceSubscription subscribe(DataSourceEntity ds, long protocolId,
                                                     List<String> topicFilters,
                                                     SourceMessageHandler handler) throws Exception {
        SharedSource shared = sources.computeIfAbsent(ds.getId(), id -> new SharedSource(ds.getId()));
        SourceSubscription sub = new SourceSubscription(this, ds.getId(), protocolId, topicFilters, handler);
        shared.subscriptions.add(sub);
        try {
            shared.ensureStarted(ds, factory);
        } catch (Exception e) {
            shared.subscriptions.remove(sub);
            if (shared.subscriptions.isEmpty()) {
                shared.stopConnector();
                sources.remove(ds.getId());
            }
            throw e;
        }
        log.info("协议[{}] 订阅共享数据源[{}] filter={}（当前订阅数 {}）",
                protocolId, ds.getId(),
                topicFilters == null || topicFilters.isEmpty() ? "全部" : topicFilters,
                shared.subscriptions.size());
        return sub;
    }

    synchronized void unsubscribe(long dataSourceId, SourceSubscription sub) {
        SharedSource shared = sources.get(dataSourceId);
        if (shared == null) {
            return;
        }
        shared.subscriptions.remove(sub);
        if (shared.subscriptions.isEmpty()) {
            shared.stopConnector();
            sources.remove(dataSourceId);
            log.info("共享数据源[{}] 最后一个订阅者离开，连接已释放", dataSourceId);
        } else {
            log.info("协议[{}] 退订共享数据源[{}]（剩余订阅数 {}）",
                    sub.getProtocolId(), dataSourceId, shared.subscriptions.size());
        }
    }

    public String describeSource(long dataSourceId) {
        SharedSource shared = sources.get(dataSourceId);
        if (shared == null || shared.connector == null) {
            return "-";
        }
        return shared.connector.describe();
    }

    public boolean isSourceRunning(long dataSourceId) {
        SharedSource shared = sources.get(dataSourceId);
        return shared != null && shared.connector != null && shared.connector.isRunning();
    }

    /** 当前订阅该数据源的协议 id 列表（监控/诊断用）。 */
    public List<Long> subscriberProtocolIds(long dataSourceId) {
        SharedSource shared = sources.get(dataSourceId);
        if (shared == null) {
            return List.of();
        }
        return shared.subscriptions.stream().map(SourceSubscription::getProtocolId).toList();
    }

    private static final class SharedSource {

        final long dataSourceId;
        final CopyOnWriteArrayList<SourceSubscription> subscriptions = new CopyOnWriteArrayList<>();

        volatile SourceConnector connector;
        /** 建连时的配置快照（type + config），用于检测配置变更后重建连接。 */
        String configSnapshot;

        SharedSource(long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        void ensureStarted(DataSourceEntity ds, SourceConnectorFactory factory) throws Exception {
            String snapshot = ds.getType() + "\n" + ds.getConfig();
            if (connector != null && snapshot.equals(configSnapshot) && connector.isRunning()) {
                return;
            }
            // 配置变化或连接未运行：重建
            stopConnector();
            SourceConnector created = factory.create(ds, "ds-" + dataSourceId + "-source");
            created.start(this::dispatch);
            connector = created;
            configSnapshot = snapshot;
        }

        void stopConnector() {
            SourceConnector c = connector;
            connector = null;
            configSnapshot = null;
            if (c != null) {
                try {
                    c.stop();
                } catch (Exception e) {
                    LoggerFactory.getLogger(SharedSourceManager.class)
                            .warn("停止共享数据源[{}]连接异常: {}", dataSourceId, e.getMessage());
                }
            }
        }

        /**
         * fan-out：按 topic 过滤串行分发给每个订阅协议。
         * handler 内部是 ringBuffer.publishEvent，队列满会阻塞（背压）。
         */
        void dispatch(SourceMessage message) {
            String topic = message.ctx() == null ? null : String.valueOf(message.ctx().get("topic"));
            boolean multi = subscriptions.size() > 1;
            for (SourceSubscription sub : subscriptions) {
                if (sub.isClosed()) {
                    continue;
                }
                if (!TopicMatcher.matchesAny(sub.getTopicFilters(), topic)) {
                    continue;
                }
                // 多协议共享时复制 ctx，避免脚本间通过共享 Map 相互影响
                SourceMessage delivered = multi
                        ? new SourceMessage(message.payload(), new java.util.HashMap<>(message.ctx()))
                        : message;
                try {
                    sub.getHandler().onMessage(delivered);
                } catch (Exception e) {
                    LoggerFactory.getLogger(SharedSourceManager.class)
                            .error("分发消息到协议[{}]失败: {}", sub.getProtocolId(), e.getMessage());
                }
            }
        }
    }
}
