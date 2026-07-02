package com.bytd.forward.engine.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试用输出通道：收集 output() 的数据，不真正发送。
 */
public class CollectingOutputSink implements OutputSink {

    public record Item(String targetKey, Object data) {}

    private final List<Item> items = new ArrayList<>();

    @Override
    public void emit(String targetKey, Object data) {
        items.add(new Item(targetKey, data));
    }

    public List<Item> getItems() {
        return items;
    }
}
