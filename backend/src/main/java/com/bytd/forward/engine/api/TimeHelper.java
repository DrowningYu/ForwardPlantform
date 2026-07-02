package com.bytd.forward.engine.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 注入脚本的 time 辅助对象。用于把各类采集时间统一成毫秒时间戳，
 * 复刻现有服务里 collect_time / sample_time / header.timestamp 的处理。
 */
public class TimeHelper {

    /** 当前毫秒时间戳。 */
    public long nowMs() {
        return System.currentTimeMillis();
    }

    /**
     * 尽力把输入转成毫秒时间戳：
     * - Long/Integer/Number 直接返回
     * - 纯数字字符串按长度补齐（10 位秒 *1000，12 位 *10，13 位原样）
     * - 形如 "yyyy-MM-dd HH:mm:ss[.SSS]" 的字符串按本地时区解析
     */
    public Long toEpochMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.chars().allMatch(Character::isDigit)) {
            long v = Long.parseLong(s);
            int len = s.length();
            if (len <= 10) {
                return v * 1000L;
            } else if (len == 12) {
                return v * 10L;
            }
            return v;
        }
        return parseDateTime(s);
    }

    /** 毫秒时间戳格式化为字符串。 */
    public String format(long epochMillis, String pattern) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern(pattern));
    }

    private Long parseDateTime(String s) {
        String[] patterns = {"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"};
        for (String p : patterns) {
            try {
                LocalDateTime dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p));
                return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignore) {
                // try next
            }
        }
        return null;
    }
}
