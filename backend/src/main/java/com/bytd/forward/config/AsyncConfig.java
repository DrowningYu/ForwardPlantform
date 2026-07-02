package com.bytd.forward.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AsyncConfig {

    /**
     * 用于脚本执行超时看门狗的调度线程池。
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService scriptWatchdogExecutor() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "script-watchdog");
            t.setDaemon(true);
            return t;
        });
    }
}
