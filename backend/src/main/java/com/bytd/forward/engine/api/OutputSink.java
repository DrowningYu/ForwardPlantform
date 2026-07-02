package com.bytd.forward.engine.api;

/**
 * 脚本 output() 的落地出口。生产环境路由到真实 Sink；调试环境收集不真正发送。
 */
public interface OutputSink {
    /**
     * @param targetKey 可选的目标标识（多目标时使用），null 表示默认目标
     * @param data      脚本产出的数据（Map/List/String/数值等）
     */
    void emit(String targetKey, Object data);
}
