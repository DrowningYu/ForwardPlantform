package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** 仅后台线程访问数据库；生产者始终 offer，故障期间直接丢弃日志。 */
@Component
public class AsyncLogWriter {
    private static final Logger log = LoggerFactory.getLogger(AsyncLogWriter.class);
    private static final String INSERT_LOG =
            "INSERT INTO protocol_log(protocol_id, level, message, log_time) VALUES (?, ?, ?, ?)";
    private static final String INSERT_RECORD =
            "INSERT INTO forward_record(protocol_id, input, output, success, error, cost_ms, record_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private final JdbcTemplate jdbc;
    private final ForwardProperties props;
    private final PartitionMaintenanceService partitions;
    private BlockingQueue<ProtocolLogRow> logQueue;
    private BlockingQueue<ForwardRecordRow> recordQueue;
    private volatile boolean running;
    private volatile boolean degraded;
    private volatile Instant lastSuccess;
    private volatile String lastFailure;
    private long nextRecoveryNanos;
    private long lastWarningNanos;
    private volatile long shutdownDeadlineNanos;
    private Thread writerThread;
    private final AtomicLong droppedLogs = new AtomicLong();
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public AsyncLogWriter(JdbcTemplate jdbc, ForwardProperties props, PartitionMaintenanceService partitions) {
        this.jdbc = jdbc;
        this.props = props;
        this.partitions = partitions;
    }

    @PostConstruct
    public void start() {
        initializeQueues();
        writerThread = new Thread(this::runLoop, "async-log-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    // 包可见入口供纯单元测试逐轮驱动，不启动应用或真实连接。
    void initializeQueues() {
        if (props.getLog().getQueueCapacity() < 1 || props.getLog().getBatchSize() < 1
                || props.getLog().getFlushIntervalMs() < 1 || props.getLog().getRecoveryIntervalMs() < 1) {
            throw new IllegalArgumentException("日志容量、批量大小和间隔必须为正数");
        }
        logQueue = new ArrayBlockingQueue<>(props.getLog().getQueueCapacity());
        recordQueue = new ArrayBlockingQueue<>(props.getLog().getQueueCapacity());
        running = true;
    }

    public void log(long protocolId, String level, String message) {
        if (!running || degraded || !logQueue.offer(new ProtocolLogRow(protocolId, level, message, Instant.now()))) {
            droppedLogs.incrementAndGet();
        }
    }

    public void record(ForwardRecordRow row) {
        if (!running || degraded || !recordQueue.offer(row)) droppedRecords.incrementAndGet();
    }

    private void runLoop() {
        try {
            while (running) {
                Thread.sleep(props.getLog().getFlushIntervalMs());
                if (running) flushOnce(System.nanoTime());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            // 正常关闭时仍由原写库线程尽力刷完；调用 stop 的线程最多等待五秒。
            while (!degraded && System.nanoTime() < shutdownDeadlineNanos
                    && (getLogQueueSize() > 0 || getRecordQueueSize() > 0)) {
                try {
                    flushLogs(props.getLog().getBatchSize());
                    if (System.nanoTime() < shutdownDeadlineNanos) flushRecords(props.getLog().getBatchSize());
                } catch (Exception e) { fail(e, System.nanoTime()); }
            }
            discardQueues();
        }
    }

    void flushOnce(long nowNanos) {
        if (!running) return;
        if (degraded) {
            discardQueues(); // 回收与降级切换恰好并发的少量入队消息。
            if (nowNanos < nextRecoveryNanos) return;
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                partitions.ensurePartitions();
                if (!running) return;
                degraded = false;
                log.info("日志数据库连接和分区已恢复，开始接收新日志");
            } catch (Exception e) {
                fail(e, nowNanos);
            }
            return;
        }
        int batchSize = props.getLog().getBatchSize();
        try {
            flushLogs(batchSize);
            if (running) flushRecords(batchSize);
        } catch (Exception e) {
            fail(e, nowNanos);
        }
    }

    private void fail(Exception error, long nowNanos) {
        degraded = true;
        failures.incrementAndGet();
        lastFailure = DatabaseFailureSummary.of(error);
        // 从失败返回时计时，避免一次长超时之后立即再次重试。
        nextRecoveryNanos = Math.max(nowNanos, System.nanoTime())
                + TimeUnit.MILLISECONDS.toNanos(props.getLog().getRecoveryIntervalMs());
        discardQueues();
        if (lastWarningNanos == 0 || nowNanos - lastWarningNanos >= TimeUnit.MINUTES.toNanos(1)) {
            lastWarningNanos = nowNanos;
            log.warn("日志写入已降级，故障期间丢弃日志，转发继续: {}", lastFailure);
        }
    }

    private void flushLogs(int size) {
        List<ProtocolLogRow> rows = new ArrayList<>(size);
        logQueue.drainTo(rows, size);
        if (rows.isEmpty()) return;
        try {
            List<Object[]> args = new ArrayList<>(rows.size());
            for (ProtocolLogRow r : rows) args.add(new Object[]{r.protocolId(), r.level(), r.message(), Timestamp.from(r.logTime())});
            jdbc.batchUpdate(INSERT_LOG, args);
            lastSuccess = Instant.now();
        } catch (Exception e) {
            droppedLogs.addAndGet(rows.size());
            throw e;
        }
    }

    private void flushRecords(int size) {
        List<ForwardRecordRow> rows = new ArrayList<>(size);
        recordQueue.drainTo(rows, size);
        if (rows.isEmpty()) return;
        try {
            List<Object[]> args = new ArrayList<>(rows.size());
            for (ForwardRecordRow r : rows) args.add(new Object[]{r.protocolId(), r.input(), r.output(), r.success(),
                    r.error(), r.costMs(), Timestamp.from(r.recordTime())});
            jdbc.batchUpdate(INSERT_RECORD, args);
            lastSuccess = Instant.now();
        } catch (Exception e) {
            droppedRecords.addAndGet(rows.size());
            throw e;
        }
    }

    private void discardQueues() {
        if (logQueue != null) while (logQueue.poll() != null) droppedLogs.incrementAndGet();
        if (recordQueue != null) while (recordQueue.poll() != null) droppedRecords.incrementAndGet();
    }

    @PreDestroy
    public void stop() {
        shutdownDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try { writerThread.join(5000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        // 绝不在关闭线程再次执行 JDBC。正在执行的 JDBC 由读取超时自行释放。
        discardQueues();
    }

    public String getWriteStatus() { return !running ? "STOPPED" : degraded ? "DEGRADED" : "READY"; }
    public Instant getLastSuccess() { return lastSuccess; }
    public String getLastFailure() { return lastFailure; }
    public long getFailures() { return failures.get(); }
    public long getDroppedLogs() { return droppedLogs.get(); }
    public long getDroppedRecords() { return droppedRecords.get(); }
    public int getLogQueueSize() { return logQueue == null ? 0 : logQueue.size(); }
    public int getRecordQueueSize() { return recordQueue == null ? 0 : recordQueue.size(); }
}
