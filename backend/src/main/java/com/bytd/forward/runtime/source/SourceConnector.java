package com.bytd.forward.runtime.source;

/**
 * 数据源连接器抽象：MQTT / Kafka。
 */
public interface SourceConnector {

    /** 建立连接并开始投递消息。 */
    void start(SourceMessageHandler handler) throws Exception;

    /** 停止并释放资源。 */
    void stop();

    boolean isRunning();

    /** 人类可读的连接描述（用于日志/状态展示）。 */
    String describe();
}
