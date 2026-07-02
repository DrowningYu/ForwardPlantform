package com.bytd.forward.engine.api;

/**
 * 脚本中可调用的日志接口：log.info/warn/error/debug。
 */
public interface ScriptLog {
    void info(Object msg);
    void warn(Object msg);
    void error(Object msg);
    void debug(Object msg);
}
