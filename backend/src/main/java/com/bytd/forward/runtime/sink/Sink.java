package com.bytd.forward.runtime.sink;

/**
 * 输出目标抽象：MQTT / Kafka / HTTP。实现需保证 send 线程安全（多 worker 并发调用）。
 */
public interface Sink {

    void open() throws Exception;

    /** 发送一条已序列化好的字符串负载。 */
    void send(String payload) throws Exception;

    void close();

    String describe();
}
