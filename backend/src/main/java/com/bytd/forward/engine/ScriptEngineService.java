package com.bytd.forward.engine;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.engine.api.JsonHelper;
import com.bytd.forward.engine.api.OutputSink;
import com.bytd.forward.engine.api.ScriptLog;
import com.bytd.forward.engine.api.StateStore;
import com.bytd.forward.engine.api.TimeHelper;
import groovy.lang.Binding;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 脚本引擎门面：编译缓存 + 绑定构建 + 带超时执行。
 */
@Service
public class ScriptEngineService {

    private final GroovyScriptCompiler compiler;
    private final CompiledScriptCache cache;
    private final ScriptExecutor executor;
    private final JsonHelper jsonHelper;
    private final TimeHelper timeHelper;
    private final ForwardProperties props;

    public ScriptEngineService(GroovyScriptCompiler compiler,
                               CompiledScriptCache cache,
                               ScriptExecutor executor,
                               JsonHelper jsonHelper,
                               TimeHelper timeHelper,
                               ForwardProperties props) {
        this.compiler = compiler;
        this.cache = cache;
        this.executor = executor;
        this.jsonHelper = jsonHelper;
        this.timeHelper = timeHelper;
        this.props = props;
    }

    /** 仅编译校验（调试/保存时用）。 */
    public CompileResult compile(String code) {
        return compiler.compile(code);
    }

    /**
     * 从缓存取编译产物，没有则编译并缓存。编译失败抛异常。
     */
    public CompiledScript getOrCompile(String cacheKey, String code) {
        CompiledScript cs = cache.get(cacheKey);
        if (cs != null) {
            return cs;
        }
        synchronized (this) {
            cs = cache.get(cacheKey);
            if (cs != null) {
                return cs;
            }
            CompileResult r = compiler.compile(code);
            if (!r.isSuccess()) {
                throw new IllegalStateException("脚本编译失败: " + r.getError());
            }
            cache.put(cacheKey, r.getCompiled());
            return r.getCompiled();
        }
    }

    public void invalidate(String cacheKey) {
        cache.invalidate(cacheKey);
    }

    /**
     * 构建单次执行的 binding。msg/ctx 为本条消息数据，sink/log/state 由运行时提供，
     * json/time 为共享无状态辅助。
     */
    public Binding buildBinding(String msg, Map<String, Object> ctx,
                                OutputSink sink, ScriptLog log, StateStore state) {
        Binding binding = new Binding();
        binding.setVariable("msg", msg);
        binding.setVariable("payload", msg);
        binding.setVariable("ctx", ctx);
        binding.setVariable("json", jsonHelper);
        binding.setVariable("time", timeHelper);
        binding.setVariable("log", log);
        binding.setVariable("state", state);
        binding.setVariable("__sink", sink);
        return binding;
    }

    public ScriptExecutionResult execute(CompiledScript compiled, Binding binding) {
        return executor.execute(compiled, binding, props.getScript().getExecTimeoutMs());
    }

    public ScriptExecutionResult execute(CompiledScript compiled, Binding binding, long timeoutMs) {
        return executor.execute(compiled, binding, timeoutMs);
    }
}
