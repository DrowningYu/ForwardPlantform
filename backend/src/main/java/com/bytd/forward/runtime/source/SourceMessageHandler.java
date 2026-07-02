package com.bytd.forward.runtime.source;

/**
 * 数据源消息回调。实现方通常把消息发布到 Disruptor 环形缓冲；
 * 当缓冲满时该调用会阻塞，从而对上游形成背压。
 */
@FunctionalInterface
public interface SourceMessageHandler {
    void onMessage(SourceMessage message);
}
