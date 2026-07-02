package com.bytd.forward.engine;

/**
 * 单次脚本执行结果。
 */
public class ScriptExecutionResult {

    private final boolean success;
    private final boolean timeout;
    private final String error;
    private final long costMs;

    private ScriptExecutionResult(boolean success, boolean timeout, String error, long costMs) {
        this.success = success;
        this.timeout = timeout;
        this.error = error;
        this.costMs = costMs;
    }

    public static ScriptExecutionResult ok(long costMs) {
        return new ScriptExecutionResult(true, false, null, costMs);
    }

    public static ScriptExecutionResult timeout(long costMs) {
        return new ScriptExecutionResult(false, true, "脚本执行超时", costMs);
    }

    public static ScriptExecutionResult error(String error, long costMs) {
        return new ScriptExecutionResult(false, false, error, costMs);
    }

    public boolean isSuccess() { return success; }
    public boolean isTimeout() { return timeout; }
    public String getError() { return error; }
    public long getCostMs() { return costMs; }
}
