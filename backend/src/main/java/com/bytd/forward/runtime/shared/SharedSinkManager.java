package com.bytd.forward.runtime.shared;

import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.runtime.sink.Sink;
import com.bytd.forward.runtime.sink.SinkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台级共享输出目标：每个 output_target 只维护一条真实连接（MQTT/Kafka/HTTP），
 * 多协议通过各自的租约（{@link SinkLease}）复用同一底层 Sink。
 * 底层 Sink 的 send 均线程安全，可被多协议 Worker 并发调用。
 */
@Service
public class SharedSinkManager {

    private static final Logger log = LoggerFactory.getLogger(SharedSinkManager.class);

    private final SinkFactory factory;
    private final Map<Long, SharedSink> sinks = new ConcurrentHashMap<>();

    public SharedSinkManager(SinkFactory factory) {
        this.factory = factory;
    }

    /** 租借共享 Sink。第一个租约触发建连；配置变化时自动重建。 */
    public synchronized SinkLease acquire(OutputTargetEntity target) throws Exception {
        SharedSink shared = sinks.computeIfAbsent(target.getId(), id -> new SharedSink(target.getId()));
        shared.ensureOpen(target, factory);
        shared.refCount++;
        log.info("输出目标[{}] 新增租约（当前引用数 {}）", target.getId(), shared.refCount);
        return new SinkLease(this, shared);
    }

    synchronized void release(SharedSink shared) {
        shared.refCount--;
        if (shared.refCount <= 0) {
            shared.closeDelegate();
            sinks.remove(shared.targetId);
            log.info("输出目标[{}] 最后一个租约释放，连接已关闭", shared.targetId);
        } else {
            log.info("输出目标[{}] 租约释放（剩余引用数 {}）", shared.targetId, shared.refCount);
        }
    }

    static final class SharedSink {

        final long targetId;
        volatile Sink delegate;
        String configSnapshot;
        int refCount;

        SharedSink(long targetId) {
            this.targetId = targetId;
        }

        void ensureOpen(OutputTargetEntity target, SinkFactory factory) throws Exception {
            String snapshot = target.getType() + "\n" + target.getConfig();
            if (delegate != null && snapshot.equals(configSnapshot)) {
                return;
            }
            Sink created = factory.create(target);
            created.open();
            Sink old = delegate;
            delegate = created;
            configSnapshot = snapshot;
            if (old != null) {
                try {
                    old.close();
                } catch (Exception e) {
                    LoggerFactory.getLogger(SharedSinkManager.class)
                            .warn("关闭旧共享 Sink[{}] 异常: {}", targetId, e.getMessage());
                }
            }
        }

        void closeDelegate() {
            Sink d = delegate;
            delegate = null;
            configSnapshot = null;
            if (d != null) {
                try {
                    d.close();
                } catch (Exception e) {
                    LoggerFactory.getLogger(SharedSinkManager.class)
                            .warn("关闭共享 Sink[{}] 异常: {}", targetId, e.getMessage());
                }
            }
        }
    }

    /**
     * 协议持有的共享 Sink 租约。实现 {@link Sink} 以便直接交给 SinkRouter：
     * open 为空操作（由管理器负责建连），close 仅归还引用。
     */
    public static final class SinkLease implements Sink {

        private final SharedSinkManager manager;
        private final SharedSink shared;
        private volatile boolean closed;

        SinkLease(SharedSinkManager manager, SharedSink shared) {
            this.manager = manager;
            this.shared = shared;
        }

        @Override
        public void open() {
            // 连接由 SharedSinkManager 管理
        }

        @Override
        public void send(String payload) throws Exception {
            Sink d = shared.delegate;
            if (d == null) {
                throw new IllegalStateException("共享输出连接已关闭");
            }
            d.send(payload);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                manager.release(shared);
            }
        }

        @Override
        public String describe() {
            Sink d = shared.delegate;
            return d == null ? "-" : d.describe();
        }
    }
}
