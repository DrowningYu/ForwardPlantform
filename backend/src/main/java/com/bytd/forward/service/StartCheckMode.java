package com.bytd.forward.service;

public enum StartCheckMode {
    /** 配置阶段：有其他协议引用同一源/目标即提示 */
    CONFIG,
    /** 启动阶段：仅其他正在运行的协议共用源/目标时提示 */
    START
}
