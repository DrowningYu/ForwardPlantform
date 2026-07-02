package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.repository.ProtocolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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

    public PartitionMaintenanceService(JdbcTemplate jdbcTemplate, ForwardProperties props,
                                       ProtocolRepository protocolRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.protocolRepository = protocolRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            ensurePartitions();
            dropExpiredPartitions();
        } catch (Exception e) {
            log.error("启动时分区维护失败: {}", e.getMessage(), e);
        }
    }

    /** 每天 00:10 维护分区。 */
    @Scheduled(cron = "0 10 0 * * *")
    public void daily() {
        ensurePartitions();
        dropExpiredPartitions();
    }

    public void ensurePartitions() {
        int preDays = props.getPartition().getPreCreateDays();
        LocalDate today = LocalDate.now();
        for (String parent : PARENT_TABLES) {
            for (int i = 0; i <= preDays; i++) {
                LocalDate day = today.plusDays(i);
                createPartition(parent, day);
            }
        }
    }

    private void createPartition(String parent, LocalDate day) {
        String child = parent + "_" + day.format(SUFFIX);
        String from = day.toString();
        String to = day.plusDays(1).toString();
        String sql = "CREATE TABLE IF NOT EXISTS " + child
                + " PARTITION OF " + parent
                + " FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("创建分区 {} 失败: {}", child, e.getMessage());
        }
    }

    public void dropExpiredPartitions() {
        int retention = resolveRetentionDays();
        LocalDate cutoff = LocalDate.now().minusDays(retention);
        for (String parent : PARENT_TABLES) {
            List<String> partitions = listPartitions(parent);
            for (String child : partitions) {
                LocalDate day = parseDay(parent, child);
                if (day != null && day.isBefore(cutoff)) {
                    try {
                        jdbcTemplate.execute("DROP TABLE IF EXISTS " + child);
                        log.info("已删除过期分区 {}", child);
                    } catch (Exception e) {
                        log.warn("删除分区 {} 失败: {}", child, e.getMessage());
                    }
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
