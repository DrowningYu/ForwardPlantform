package com.bytd.forward.runtime.shared;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.runtime.sink.Sink;
import com.bytd.forward.runtime.sink.SinkFactory;
import com.bytd.forward.runtime.source.HttpIngestRegistry;
import com.bytd.forward.runtime.source.HttpSourceConnector;
import com.bytd.forward.runtime.source.SourceConnectorFactory;
import com.bytd.forward.runtime.source.SourceMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享数据源/输出目标管理器：引用计数、fan-out、topic 过滤。
 * 用 HTTP 源做真实连接器（无需外部 broker）。
 */
class SharedManagersTest {

    private HttpIngestRegistry registry;
    private SharedSourceManager sourceManager;

    @BeforeEach
    void setUp() {
        registry = new HttpIngestRegistry();
        sourceManager = new SharedSourceManager(new SourceConnectorFactory(new ObjectMapper(), registry));
    }

    private DataSourceEntity httpSource(long id, String path) {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setId(id);
        ds.setName("http-src-" + id);
        ds.setType("HTTP");
        ds.setConfig("{\"path\":\"" + path + "\",\"method\":\"POST\"}");
        return ds;
    }

    @Test
    void fanOutWithTopicFilterAndRefCount() throws Exception {
        DataSourceEntity ds = httpSource(1L, "t1");

        List<String> gotA = new CopyOnWriteArrayList<>();
        List<String> gotB = new CopyOnWriteArrayList<>();

        // A 匹配 t1（即该源全部）；B 过滤一个不存在的 topic
        SourceSubscription subA = sourceManager.subscribe(ds, 101L, List.of(), m -> gotA.add(m.payload()));
        SourceSubscription subB = sourceManager.subscribe(ds, 102L, List.of("other-topic"), m -> gotB.add(m.payload()));

        HttpSourceConnector connector = registry.lookup("t1");
        assertTrue(connector != null && connector.isRunning(), "第一个订阅者应触发建连");

        connector.dispatch("{\"v\":1}", "POST");

        assertEquals(1, gotA.size(), "A 无过滤应收到消息");
        assertEquals(0, gotB.size(), "B 过滤不匹配不应收到");

        // 退订 A：连接保持（B 仍在）
        subA.close();
        assertTrue(registry.lookup("t1") != null, "还有订阅者时连接不应释放");

        // 退订 B：最后一个订阅者离开，连接释放
        subB.close();
        assertTrue(registry.lookup("t1") == null, "最后一个订阅者离开后应释放连接");
    }

    @Test
    void duplicateSubscribeReusesSameConnection() throws Exception {
        DataSourceEntity ds = httpSource(2L, "t2");
        SourceSubscription s1 = sourceManager.subscribe(ds, 201L, List.of(), m -> { });
        HttpSourceConnector first = registry.lookup("t2");
        SourceSubscription s2 = sourceManager.subscribe(ds, 202L, List.of(), m -> { });
        assertEquals(first, registry.lookup("t2"), "第二个订阅者应复用同一连接");
        assertEquals(List.of(201L, 202L), sourceManager.subscriberProtocolIds(2L));
        s1.close();
        s2.close();
    }

    @Test
    void ctxIsCopiedPerSubscriberWhenShared() throws Exception {
        DataSourceEntity ds = httpSource(3L, "t3");
        List<SourceMessage> gotA = new CopyOnWriteArrayList<>();
        List<SourceMessage> gotB = new CopyOnWriteArrayList<>();
        SourceSubscription s1 = sourceManager.subscribe(ds, 301L, List.of(), gotA::add);
        SourceSubscription s2 = sourceManager.subscribe(ds, 302L, List.of(), gotB::add);

        registry.lookup("t3").dispatch("x", "POST");
        assertEquals(1, gotA.size());
        assertEquals(1, gotB.size());
        assertFalse(gotA.get(0).ctx() == gotB.get(0).ctx(), "多订阅者应各自持有 ctx 副本");
        s1.close();
        s2.close();
    }

    @Test
    void sinkLeaseRefCounting() throws Exception {
        Sink sink = mock(Sink.class);
        when(sink.describe()).thenReturn("mock-sink");
        SinkFactory factory = mock(SinkFactory.class);
        when(factory.create(any())).thenReturn(sink);

        SharedSinkManager manager = new SharedSinkManager(factory);

        OutputTargetEntity target = new OutputTargetEntity();
        target.setId(9L);
        target.setName("t");
        target.setType("MQTT");
        target.setConfig("{}");

        SharedSinkManager.SinkLease l1 = manager.acquire(target);
        SharedSinkManager.SinkLease l2 = manager.acquire(target);

        // 只建连一次
        verify(factory, times(1)).create(any());
        verify(sink, times(1)).open();

        AtomicInteger sent = new AtomicInteger();
        l1.send("a");
        l2.send("b");
        verify(sink, times(2)).send(any());

        l1.close();
        verify(sink, times(0)).close();
        l2.close();
        verify(sink, times(1)).close();

        // 释放后 send 应失败
        assertThrows(IllegalStateException.class, () -> l2.send("c"));
    }
}
