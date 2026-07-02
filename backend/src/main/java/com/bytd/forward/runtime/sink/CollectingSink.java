package com.bytd.forward.runtime.sink;

import com.bytd.forward.engine.api.OutputSink;

import java.util.ArrayList;
import java.util.List;

/**
 * 采样命中时使用：既把数据真实发出（委托 SinkRouter），又收集序列化后的输出，
 * 供写入 forward_record 明细。单条消息内单线程使用，无需同步。
 */
public class CollectingSink implements OutputSink {

    private final SinkRouter router;
    private final List<String> collected = new ArrayList<>(4);

    public CollectingSink(SinkRouter router) {
        this.router = router;
    }

    @Override
    public void emit(String targetKey, Object data) {
        collected.add(router.serialize(data));
        router.emit(targetKey, data);
    }

    public List<String> getCollected() {
        return collected;
    }
}
