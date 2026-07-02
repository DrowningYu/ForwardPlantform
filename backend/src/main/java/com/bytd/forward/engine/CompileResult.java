package com.bytd.forward.engine;

/**
 * 编译结果，用于校验/调试反馈。
 */
public class CompileResult {

    private final boolean success;
    private final String error;
    private final CompiledScript compiled;

    private CompileResult(boolean success, String error, CompiledScript compiled) {
        this.success = success;
        this.error = error;
        this.compiled = compiled;
    }

    public static CompileResult ok(CompiledScript compiled) {
        return new CompileResult(true, null, compiled);
    }

    public static CompileResult fail(String error) {
        return new CompileResult(false, error, null);
    }

    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public CompiledScript getCompiled() { return compiled; }
}
