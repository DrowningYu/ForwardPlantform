package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.repository.ProtocolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按天维护 protocol_log / forward_record 的 RANGE 分区：
 * - 预建今天及未来 N 天分区（保证插入不落空）
 * - 按全局保留天数 DROP 过期日分区（远快于 DELETE）
 *
 * 保留策略：取所有协议 log_retention_days 的最大值作为全局兜底，
 * 保证没有协议的数据被提前删除；下限受 partition.min-retention-days 保护。
 */
@Service
public class PartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<String> PARENT_TABLES = List.of("protocol_log", "forward_record");

    private final JdbcTemplate jdbcTemplate;
    private final ForwardProperties props;
    private final ProtocolRepository protocolRepository;
    private final ReentrantLock maintenanceLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "partition-maintenance");
        thread.setDaemon(true);
        return thread;
    });
    private LocalDate lastMaintained;

    public PartitionMaintenanceService(JdbcTemplate jdbcTemplate, ForwardProperties props,
                                       ProtocolRepository protocolRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.protocolRepository = protocolRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        scheduler.scheduleWithFixedDelay(this::daily, 0, 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() { scheduler.shutdownNow(); }

    /** 独立线程每天维护；失败每分钟重试，不占用指标调度线程。 */
    public void daily() {
        if (LocalDate.now().equals(lastMaintained) || !maintenanceLock.tryLock()) return;
        try {
            // 启动时立即维护；后续保留原先每天 00:10 的维护时间。
            if (lastMaintained != null && java.time.LocalTime.now().isBefore(java.time.LocalTime.of(0, 10))) return;
            ensurePartitions();
            dropExpiredPartitions();
            lastMaintained = LocalDate.now();
        } catch (Exception e) {
            log.warn("分区维护失败，将重试: {}", DatabaseFailureSummary.of(e));
        } finally {
            maintenanceLock.unlock();
        }
    }

    public void ensurePartitions() {
        // 日常维护与恢复补建互斥，避免同时 CREATE 同名分区。
        // 恢复线程不无限等待另一个数据库操作。
        if (!maintenanceLock.tryLock()) throw new IllegalStateException("分区维护正在进行");
        try {
            int preDays = props.getPartition().getPreCreateDays();
            LocalDate today = LocalDate.now();
            for (String parent : PARENT_TABLES) {
                for (int i = 0; i <= preDays; i++) {
                    LocalDate day = today.plusDays(i);
                    createPartition(parent, day);
                }
            }
        } finally {
            maintenanceLock.unlock();
        }
    }

    private void createPartition(String parent, LocalDate day) {
        String child = parent + "_" + day.format(SUFFIX);
        String from = day.toString();
        String to = day.plusDays(1).toString();
        String sql = "CREATE TABLE IF NOT EXISTS " + child
                + " PARTITION OF " + parent
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
        jdbcTemplate.execute(sql);
    }

    public void dropExpiredPartitions() {
        int retention = resolveRetentionDays();
        LocalDate cutoff = LocalDate.now().minusDays(retention);
        for (String parent : PARENT_TABLES) {
            List<String> partitions = listPartitions(parent);
            for (String child : partitions) {
                LocalDate day = parseDay(parent, child);
                if (day != null && day.isBefore(cutoff)) {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS " + child);
                    log.info("已删除过期分区 {}", child);
                }
            }
        }
    }

    private int resolveRetentionDays() {
        int min = props.getPartition().getMinRetentionDays();
        int max = protocolRepository.findAll().stream()
                .mapToInt(p -> p.getLogRetentionDays())
                .max()
                .orElse(7);
        return Math.max(min, max);
    }

    private List<String> listPartitions(String parent) {
        String sql = "SELECT c.relname FROM pg_inherits i "
                + "JOIN pg_class c ON c.oid = i.inhrelid "
                + "JOIN pg_class p ON p.oid = i.inhparent "
                + "WHERE p.relname = ?";
        return jdbcTemplate.queryForList(sql, String.class, parent);
    }

    private LocalDate parseDay(String parent, String child) {
        String prefix = parent + "_";
        if (!child.startsWith(prefix)) {
            return null;
        }
        try {
            return LocalDate.parse(child.substring(prefix.length()), SUFFIX);
        } catch (Exception e) {
            return null;
        }
    }
}
