package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅使用模拟 JDBC；不会创建真实连接。 */
class AsyncLogWriterTest {
    private JdbcTemplate jdbc;
    private PartitionMaintenanceService partitions;
    private AsyncLogWriter writer;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        partitions = mock(PartitionMaintenanceService.class);
        ForwardProperties props = new ForwardProperties();
        props.getLog().setQueueCapacity(2);
        writer = new AsyncLogWriter(jdbc, props, partitions);
        writer.initializeQueues();
    }

    private ForwardRecordRow row() { return new ForwardRecordRow(1, "in", "out", true, null, 1, Instant.now()); }

    @Test
    void failedBatchAndBacklogAreCountedAndRecoveryResumesNewWrites() {
        when(jdbc.batchUpdate(anyString(), anyList()))
                .thenThrow(new DataAccessResourceFailureException("SQL includes sensitive payload"))
                .thenReturn(new int[]{1});
        writer.log(1, "INFO", "a");
        writer.record(row());
        writer.flushOnce(System.nanoTime());
        assertEquals("DEGRADED", writer.getWriteStatus());
        assertEquals(1, writer.getDroppedLogs());
        assertEquals(1, writer.getDroppedRecords());
        writer.record(row());
        assertEquals(2, writer.getDroppedRecords());
        assertEquals(0, writer.getRecordQueueSize());
        assertFalse(writer.getLastFailure().contains("sensitive"));

        writer.flushOnce(System.nanoTime());
        verify(jdbc, never()).queryForObject("SELECT 1", Integer.class);
        writer.flushOnce(System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
        verify(partitions).ensurePartitions();
        assertEquals("READY", writer.getWriteStatus());
        writer.record(row());
        writer.flushOnce(System.nanoTime());
        assertNotNull(writer.getLastSuccess());
        assertEquals(0, writer.getRecordQueueSize());
    }

    @Test
    void missingPartitionsKeepWriterDegradedUntilTheyCanBeCreated() {
        when(jdbc.batchUpdate(anyString(), anyList())).thenThrow(new DataAccessResourceFailureException("down"));
        writer.record(row());
        writer.flushOnce(System.nanoTime());
        doThrow(new IllegalStateException("partition unavailable")).when(partitions).ensurePartitions();
        writer.flushOnce(System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
        assertEquals("DEGRADED", writer.getWriteStatus());
        assertEquals(2, writer.getFailures());
    }

    @Test
    void boundedQueueAndShutdownNeverWriteSynchronously() {
        writer.record(row()); writer.record(row()); writer.record(row());
        assertEquals(2, writer.getRecordQueueSize());
        assertEquals(1, writer.getDroppedRecords());
        assertTimeout(Duration.ofSeconds(1), writer::stop);
        verifyNoInteractions(jdbc);
        assertEquals(3, writer.getDroppedRecords());
        assertEquals("STOPPED", writer.getWriteStatus());
    }

    @Test
    void producerDoesNotWaitForBlockedJdbc() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new int[]{1};
        });
        writer.record(row());
        Thread background = new Thread(() -> writer.flushOnce(System.nanoTime()));
        background.setDaemon(true);
        background.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                for (int i = 0; i < 10000; i++) writer.record(row());
            });
            assertEquals(2, writer.getRecordQueueSize());
            assertEquals(9998, writer.getDroppedRecords());
        } finally { release.countDown(); background.join(2000); writer.stop(); }
    }

    @Test
    void summaryContainsSqlStateButNeverSqlOrPayload() {
        Exception error = new DataAccessResourceFailureException("secret", new SQLException("secret SQL", "08006"));
        assertEquals("DataAccessResourceFailureException SQLState=08006", DatabaseFailureSummary.of(error));
    }

    @Test
    void shutdownOfBlockedWriterIsBoundedAndDoesNotStartAnotherFlush() throws Exception {
        ForwardProperties props = new ForwardProperties();
        props.getLog().setFlushIntervalMs(1);
        AsyncLogWriter active = new AsyncLogWriter(jdbc, props, partitions);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(call -> {
            entered.countDown();
            boolean done = false;
            while (!done) {
                try { done = release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* 模拟不响应线程中断的 socket 读 */ }
            }
            return new int[]{1};
        });
        active.start();
        try {
            active.record(row());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            active.record(row());
            assertTimeoutPreemptively(Duration.ofSeconds(6), active::stop);
            verify(jdbc, times(1)).batchUpdate(anyString(), anyList());
            assertEquals(0, active.getRecordQueueSize());
        } finally { release.countDown(); active.stop(); }
    }

    @Test
    void forwardingOutputContinuesWhileLogDatabaseIsOffline() {
        when(jdbc.batchUpdate(anyString(), anyList())).thenThrow(new DataAccessResourceFailureException("offline"));
        writer.record(row()); writer.flushOnce(System.nanoTime());
        clearInvocations(jdbc);
        var engine = mock(com.bytd.forward.engine.ScriptEngineService.class);
        var compiled = mock(com.bytd.forward.engine.CompiledScript.class);
        var router = new com.bytd.forward.runtime.sink.SinkRouter(
                mock(com.bytd.forward.runtime.sink.Sink.class), new com.fasterxml.jackson.databind.ObjectMapper());
        when(engine.buildBinding(anyString(), anyMap(), any(), any(), any())).thenAnswer(call -> {
            var binding = new groovy.lang.Binding();
            binding.setVariable("sink", call.getArgument(2));
            return binding;
        });
        when(engine.execute(eq(compiled), any())).thenAnswer(call -> {
            groovy.lang.Binding binding = call.getArgument(1);
            ((com.bytd.forward.engine.api.OutputSink) binding.getVariable("sink")).emit(null, "forwarded");
            return com.bytd.forward.engine.ScriptExecutionResult.ok(1);
        });
        var metrics = new com.bytd.forward.runtime.ProtocolMetrics();
        var handler = new com.bytd.forward.runtime.ForwardWorkHandler(1L, engine, () -> compiled, router,
                new com.bytd.forward.engine.api.StateStore(), new ProtocolScriptLog(1L, writer), writer, 1.0, metrics);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            for (int i = 0; i < 1000; i++) {
                var event = new com.bytd.forward.runtime.MessageEvent();
                event.setMessage(new com.bytd.forward.runtime.source.SourceMessage("input", java.util.Map.of()));
                handler.onEvent(event);
            }
        });
        assertEquals(1000, router.getOutCount());
        assertEquals(1000, metrics.getProcessed());
        assertEquals(1001, writer.getDroppedRecords());
        assertEquals(0, writer.getRecordQueueSize());
        verifyNoInteractions(jdbc);
    }
}
