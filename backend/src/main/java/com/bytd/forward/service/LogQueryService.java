package com.bytd.forward.service;

import com.bytd.forward.web.dto.ForwardRecordDto;
import com.bytd.forward.web.dto.LogEntryDto;
import com.bytd.forward.web.dto.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JdbcTemplate 的日志/明细分页查询（直接查分区父表，PG 自动分区裁剪）。
 */
@Service
public class LogQueryService {

    private final JdbcTemplate jdbcTemplate;

    public LogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<LogEntryDto> queryLogs(Long protocolId, String level, String keyword,
                                             Long fromMs, Long toMs, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (protocolId != null) {
            where.append(" AND protocol_id = ?");
            args.add(protocolId);
        }
        if (level != null && !level.isBlank()) {
            where.append(" AND level = ?");
            args.add(level);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND message ILIKE ?");
            args.add("%" + keyword + "%");
        }
        if (fromMs != null) {
            where.append(" AND log_time >= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(fromMs)));
        }
        if (toMs != null) {
            where.append(" AND log_time <= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(toMs)));
        }

        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM protocol_log" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<LogEntryDto> items = jdbcTemplate.query(
                "SELECT id, protocol_id, level, message, log_time FROM protocol_log" + where
                        + " ORDER BY log_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new LogEntryDto(
                        rs.getLong("id"),
                        rs.getLong("protocol_id"),
                        rs.getString("level"),
                        rs.getString("message"),
                        rs.getTimestamp("log_time").toInstant()),
                pageArgs.toArray());

        return new PageResult<>(total == null ? 0 : total, page, size, items);
    }

    public PageResult<ForwardRecordDto> queryRecords(Long protocolId, Boolean success,
                                                     Long fromMs, Long toMs, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (protocolId != null) {
            where.append(" AND protocol_id = ?");
            args.add(protocolId);
        }
        if (success != null) {
            where.append(" AND success = ?");
            args.add(success);
        }
        if (fromMs != null) {
            where.append(" AND record_time >= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(fromMs)));
        }
        if (toMs != null) {
            where.append(" AND record_time <= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(toMs)));
        }

        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM forward_record" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<ForwardRecordDto> items = jdbcTemplate.query(
                "SELECT id, protocol_id, input, output, success, error, cost_ms, record_time FROM forward_record" + where
                        + " ORDER BY record_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new ForwardRecordDto(
                        rs.getLong("id"),
                        rs.getLong("protocol_id"),
                        rs.getString("input"),
                        rs.getString("output"),
                        rs.getBoolean("success"),
                        rs.getString("error"),
                        (Integer) rs.getObject("cost_ms"),
                        rs.getTimestamp("record_time").toInstant()),
                pageArgs.toArray());

        return new PageResult<>(total == null ? 0 : total, page, size, items);
    }
}
