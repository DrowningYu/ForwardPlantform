package com.bytd.forward.engine.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试用日志：收集到内存列表，不落库。
 */
public class CollectingScriptLog implements ScriptLog {

    private final List<String> logs = new ArrayList<>();

    @Override
    public void info(Object msg) { logs.add("INFO: " + str(msg)); }

    @Override
    public void warn(Object msg) { logs.add("WARN: " + str(msg)); }

    @Override
    public void error(Object msg) { logs.add("ERROR: " + str(msg)); }

    @Override
    public void debug(Object msg) { logs.add("DEBUG: " + str(msg)); }

    private String str(Object msg) {
        return msg == null ? "null" : msg.toString();
    }

    public List<String> getLogs() {
        return logs;
    }
}
