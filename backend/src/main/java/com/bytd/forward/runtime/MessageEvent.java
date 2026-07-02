package com.bytd.forward.runtime;

import com.bytd.forward.runtime.source.SourceMessage;

/**
 * Disruptor 环形缓冲中的事件槽，可复用以减少 GC。
 */
public class MessageEvent {
    private SourceMessage message;

    public SourceMessage getMessage() {
        return message;
    }

    public void setMessage(SourceMessage message) {
        this.message = message;
    }

    public void clear() {
        this.message = null;
    }
}
