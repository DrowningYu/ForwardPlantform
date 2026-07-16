package com.bytd.forward.runtime.shared;

import com.bytd.forward.runtime.source.SourceMessageHandler;

import java.util.List;

/**
 * 协议对某个共享数据源的一份订阅。close() 归还订阅，最后一个订阅者离开时连接被释放。
 */
public final class SourceSubscription {

    private final SharedSourceManager manager;
    private final long dataSourceId;
    private final long protocolId;
    private final List<String> topicFilters;
    private final SourceMessageHandler handler;

    private volatile boolean closed;

    SourceSubscription(SharedSourceManager manager, long dataSourceId, long protocolId,
                       List<String> topicFilters, SourceMessageHandler handler) {
        this.manager = manager;
        this.dataSourceId = dataSourceId;
        this.protocolId = protocolId;
        this.topicFilters = topicFilters == null ? List.of() : List.copyOf(topicFilters);
        this.handler = handler;
    }

    public long getProtocolId() {
        return protocolId;
    }

    public List<String> getTopicFilters() {
        return topicFilters;
    }

    SourceMessageHandler getHandler() {
        return handler;
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        if (!closed) {
            closed = true;
            manager.unsubscribe(dataSourceId, this);
        }
    }

    /** 连接描述 + 本协议的 topic 过滤。 */
    public String describe() {
        String conn = manager.describeSource(dataSourceId);
        String filter = topicFilters.isEmpty() ? "全部" : String.join("|", topicFilters);
        return conn + " filter=" + filter;
    }
}
