package com.bytd.forward.engine.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个协议一个、带 TTL 的并发键值存储，供脚本做跨消息聚合
 * （复刻现有服务里 INC/DIS 合并、按最新采集时间取值等逻辑）。
 */
public class StateStore {

    private static final class Entry {
        final Object value;
        final long expireAt; // 0 表示不过期

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean expired(long now) {
            return expireAt > 0 && now >= expireAt;
        }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public Object get(String key) {
        Entry e = map.get(key);
        if (e == null) {
            return null;
        }
        if (e.expired(System.currentTimeMillis())) {
            map.remove(key, e);
            return null;
        }
        return e.value;
    }

    public void put(String key, Object value) {
        map.put(key, new Entry(value, 0L));
    }

    public void put(String key, Object value, long ttlMs) {
        long expireAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
        map.put(key, new Entry(value, expireAt));
    }

    public boolean containsKey(String key) {
        return get(key) != null;
    }

    public Object remove(String key) {
        Entry e = map.remove(key);
        return e == null ? null : e.value;
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    /** 惰性清理过期项，可由后台定时调用。 */
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        int[] removed = {0};
        map.forEach((k, v) -> {
            if (v.expired(now) && map.remove(k, v)) {
                removed[0]++;
            }
        });
        return removed[0];
    }

    public Map<String, Object> snapshotValues() {
        Map<String, Object> out = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        map.forEach((k, v) -> {
            if (!v.expired(now)) {
                out.put(k, v.value);
            }
        });
        return out;
    }
}
