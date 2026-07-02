package com.bytd.forward.engine;

import groovy.lang.GroovyClassLoader;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把用户 Groovy 代码编译成 {@link ForwardScript} 的子类。
 *
 * <ul>
 *   <li>ScriptBaseClass = ForwardScript，使脚本可直接调用 output()</li>
 *   <li>ImportCustomizer 预置常用包</li>
 *   <li>SecureASTCustomizer 屏蔽 IO/反射/线程/进程等危险 API（务实沙箱）</li>
 *   <li>ThreadInterrupt AST 让脚本在循环中响应中断，配合执行超时</li>
 * </ul>
 */
@Component
public class GroovyScriptCompiler {

    private static final AtomicLong SEQ = new AtomicLong();

    private static final List<String> DISALLOWED_STAR_IMPORTS = List.of(
            "java.io", "java.nio", "java.nio.file", "java.net", "javax.net",
            "java.lang.reflect", "java.lang.invoke"
    );

    private static final List<String> DISALLOWED_IMPORTS = List.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.Thread",
            "java.lang.ProcessBuilder", "java.lang.Process", "java.lang.Class"
    );

    private static final List<String> DISALLOWED_RECEIVERS = List.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.Thread",
            "java.lang.ProcessBuilder", "java.lang.Class", "groovy.lang.GroovySystem"
    );

    private CompilerConfiguration buildConfig() {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(ForwardScript.class.getName());

        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("java.util", "java.util.stream", "java.time");

        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setIndirectImportCheckEnabled(true);
        secure.setDisallowedStarImports(DISALLOWED_STAR_IMPORTS);
        secure.setDisallowedImports(DISALLOWED_IMPORTS);
        secure.setDisallowedReceivers(DISALLOWED_RECEIVERS);

        config.addCompilationCustomizers(
                imports,
                secure,
                new ASTTransformationCustomizer(ThreadInterrupt.class)
        );
        return config;
    }

    /**
     * 编译代码，成功返回可缓存的 CompiledScript，失败返回错误信息。
     */
    public CompileResult compile(String code) {
        if (code == null || code.isBlank()) {
            return CompileResult.fail("脚本代码为空");
        }
        GroovyClassLoader loader = new GroovyClassLoader(
                Thread.currentThread().getContextClassLoader(), buildConfig());
        try {
            String name = "ForwardUserScript_" + SEQ.incrementAndGet() + ".groovy";
            Class<?> clazz = loader.parseClass(code, name);
            return CompileResult.ok(new CompiledScript(clazz, loader));
        } catch (Throwable t) {
            try {
                loader.close();
            } catch (Exception ignore) {
                // ignore
            }
            return CompileResult.fail(rootMessage(t));
        }
    }

    private String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 5) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }
}
