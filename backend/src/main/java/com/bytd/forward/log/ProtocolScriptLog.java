package com.bytd.forward.log;

import com.bytd.forward.engine.api.ScriptLog;

/**
 * 生产环境脚本日志：把 log.xxx 异步写入 protocol_log 分区表。
 */
public class ProtocolScriptLog implements ScriptLog {

    private final long protocolId;
    private final AsyncLogWriter writer;

    public ProtocolScriptLog(long protocolId, AsyncLogWriter writer) {
        this.protocolId = protocolId;
        this.writer = writer;
    }

    @Override
    public void info(Object msg) {
        writer.log(protocolId, "INFO", str(msg));
    }

    @Override
    public void warn(Object msg) {
        writer.log(protocolId, "WARN", str(msg));
    }

    @Override
    public void error(Object msg) {
        writer.log(protocolId, "ERROR", str(msg));
    }

    @Override
    public void debug(Object msg) {
        writer.log(protocolId, "DEBUG", str(msg));
    }

    private String str(Object msg) {
        return msg == null ? "null" : msg.toString();
    }
}
