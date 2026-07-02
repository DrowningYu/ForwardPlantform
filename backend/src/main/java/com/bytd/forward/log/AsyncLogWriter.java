package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步批量日志写入器。热路径只做入队（队列满即丢弃并计数，绝不阻塞转发主链路），
 * 由单独线程按批量/时间间隔刷入 PostgreSQL 分区表。
 */
@Component
public class AsyncLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncLogWriter.class);

    private static final String INSERT_PROTOCOL_LOG =
            "INSERT INTO protocol_log(protocol_id, level, message, log_time) VALUES (?, ?, ?, ?)";
    private static final String INSERT_FORWARD_RECORD =
            "INSERT INTO forward_record(protocol_id, input, output, success, error, cost_ms, record_time) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ForwardProperties props;

    private BlockingQueue<ProtocolLogRow> logQueue;
    private BlockingQueue<ForwardRecordRow> recordQueue;
    private volatile boolean running;
    private Thread writerThread;

    private final AtomicLong droppedLogs = new AtomicLong();
    private final AtomicLong droppedRecords = new AtomicLong();

    public AsyncLogWriter(JdbcTemplate jdbcTemplate, ForwardProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        int cap = props.getLog().getQueueCapacity();
        logQueue = new ArrayBlockingQueue<>(cap);
        recordQueue = new ArrayBlockingQueue<>(cap);
        running = true;
        writerThread = new Thread(this::runLoop, "async-log-writer");
        writerThread.setDaemon(true);
        writerThread.start();
        log.info("AsyncLogWriter 已启动 queueCapacity={}", cap);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flushAll();
    }

    /** 入队运行日志，队列满则丢弃并计数。 */
    public void log(long protocolId, String level, String message) {
        if (logQueue == null || !logQueue.offer(new ProtocolLogRow(protocolId, level, message, java.time.Instant.now()))) {
            droppedLogs.incrementAndGet();
        }
    }

    /** 入队转发明细，队列满则丢弃并计数。 */
    public void record(ForwardRecordRow row) {
        if (recordQueue == null || !recordQueue.offer(row)) {
            droppedRecords.incrementAndGet();
        }
    }

    private void runLoop() {
        int batchSize = props.getLog().getBatchSize();
        long flushInterval = props.getLog().getFlushIntervalMs();
        while (running) {
            try {
                Thread.sleep(flushInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            drainAndInsert(batchSize);
        }
    }

    private void drainAndInsert(int batchSize) {
        try {
            flushLogs(batchSize);
            flushRecords(batchSize);
        } catch (Exception e) {
            log.warn("日志批量写入失败: {}", e.getMessage());
        }
    }

    private void flushLogs(int batchSize) {
        List<ProtocolLogRow> buffer = new ArrayList<>(batchSize);
        logQueue.drainTo(buffer, batchSize);
        if (buffer.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>(buffer.size());
        for (ProtocolLogRow r : buffer) {
            args.add(new Object[]{r.protocolId(), r.level(), r.message(), Timestamp.from(r.logTime())});
        }
        jdbcTemplate.batchUpdate(INSERT_PROTOCOL_LOG, args);
    }

    private void flushRecords(int batchSize) {
        List<ForwardRecordRow> buffer = new ArrayList<>(batchSize);
        recordQueue.drainTo(buffer, batchSize);
        if (buffer.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>(buffer.size());
        for (ForwardRecordRow r : buffer) {
            args.add(new Object[]{r.protocolId(), r.input(), r.output(), r.success(),
                    r.error(), r.costMs(), Timestamp.from(r.recordTime())});
        }
        jdbcTemplate.batchUpdate(INSERT_FORWARD_RECORD, args);
    }

    private void flushAll() {
        try {
            while (logQueue != null && !logQueue.isEmpty()) {
                flushLogs(1000);
            }
            while (recordQueue != null && !recordQueue.isEmpty()) {
                flushRecords(1000);
            }
        } catch (Exception e) {
            log.warn("关闭前刷盘失败: {}", e.getMessage());
        }
    }

    public long getDroppedLogs() { return droppedLogs.get(); }
    public long getDroppedRecords() { return droppedRecords.get(); }
    public int getLogQueueSize() { return logQueue == null ? 0 : logQueue.size(); }
    public int getRecordQueueSize() { return recordQueue == null ? 0 : recordQueue.size(); }
}
