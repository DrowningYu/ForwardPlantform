package com.bytd.forward.engine;

import groovy.lang.Binding;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 在当前 worker 线程执行脚本，并用看门狗线程在超时后中断。
 * 依赖编译期插入的 ThreadInterrupt AST 使脚本在循环中响应中断。
 */
@Component
public class ScriptExecutor {

    private final ScheduledExecutorService watchdog;

    public ScriptExecutor(ScheduledExecutorService scriptWatchdogExecutor) {
        this.watchdog = scriptWatchdogExecutor;
    }

    public ScriptExecutionResult execute(CompiledScript compiled, Binding binding, long timeoutMs) {
        Script script = InvokerHelper.createScript(compiled.getScriptClass(), binding);
        final Thread runner = Thread.currentThread();
        ScheduledFuture<?> wd = watchdog.schedule(runner::interrupt, timeoutMs, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();
        try {
            script.run();
            long cost = (System.nanoTime() - start) / 1_000_000L;
            return ScriptExecutionResult.ok(cost);
        } catch (Throwable t) {
            long cost = (System.nanoTime() - start) / 1_000_000L;
            if (isInterruption(t) || runner.isInterrupted()) {
                return ScriptExecutionResult.timeout(cost);
            }
            return ScriptExecutionResult.error(describe(t), cost);
        } finally {
            wd.cancel(false);
            // 清除可能残留的中断标记，避免污染线程池后续任务
            Thread.interrupted();
        }
    }

    private boolean isInterruption(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (cur instanceof InterruptedException) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    private String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
