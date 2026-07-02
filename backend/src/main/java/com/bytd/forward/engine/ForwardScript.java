package com.bytd.forward.engine;

import com.bytd.forward.engine.api.OutputSink;
import groovy.lang.Script;

/**
 * 所有用户脚本的基类。用户脚本被编译为该类的子类，
 * 因此可直接调用 output(...) 方法，并通过 binding 访问 msg/ctx/json/state/time/log。
 */
public abstract class ForwardScript extends Script {

    /** 输出到协议配置的默认目标。 */
    public void output(Object data) {
        sink().emit(null, data);
    }

    /** 输出到指定目标（多目标场景）。 */
    public void output(String targetKey, Object data) {
        sink().emit(targetKey, data);
    }

    private OutputSink sink() {
        Object s = getBinding().getVariable("__sink");
        if (!(s instanceof OutputSink)) {
            throw new IllegalStateException("输出通道未初始化");
        }
        return (OutputSink) s;
    }
}
