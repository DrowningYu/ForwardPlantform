package com.bytd.forward.engine;

import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;

/**
 * 一次编译的产物：脚本 Class + 其专属 ClassLoader。缓存淘汰时需 close 以释放 Metaspace。
 */
public class CompiledScript implements AutoCloseable {

    private final Class<? extends Script> scriptClass;
    private final GroovyClassLoader classLoader;
    private final long compiledAt;

    @SuppressWarnings("unchecked")
    public CompiledScript(Class<?> scriptClass, GroovyClassLoader classLoader) {
        this.scriptClass = (Class<? extends Script>) scriptClass;
        this.classLoader = classLoader;
        this.compiledAt = System.currentTimeMillis();
    }

    public Class<? extends Script> getScriptClass() {
        return scriptClass;
    }

    public long getCompiledAt() {
        return compiledAt;
    }

    @Override
    public void close() {
        try {
            classLoader.close();
        } catch (Exception ignore) {
            // best effort
        }
    }
}
