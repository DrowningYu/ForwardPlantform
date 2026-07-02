package com.bytd.forward.engine;

import com.bytd.forward.config.ForwardProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按缓存键（protocolId:version）复用编译产物，避免每条消息重复编译。
 * LRU 淘汰时关闭对应 ClassLoader。
 */
@Component
public class CompiledScriptCache {

    private final int maxSize;
    private final LinkedHashMap<String, CompiledScript> cache;

    public CompiledScriptCache(ForwardProperties props) {
        this.maxSize = Math.max(16, props.getScript().getCompiledCacheSize());
        this.cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CompiledScript> eldest) {
                if (size() > CompiledScriptCache.this.maxSize) {
                    eldest.getValue().close();
                    return true;
                }
                return false;
            }
        };
    }

    public synchronized CompiledScript get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, CompiledScript compiled) {
        CompiledScript old = cache.put(key, compiled);
        if (old != null && old != compiled) {
            old.close();
        }
    }

    public synchronized void invalidate(String key) {
        CompiledScript removed = cache.remove(key);
        if (removed != null) {
            removed.close();
        }
    }

    public synchronized void clear() {
        cache.values().forEach(CompiledScript::close);
        cache.clear();
    }
}
