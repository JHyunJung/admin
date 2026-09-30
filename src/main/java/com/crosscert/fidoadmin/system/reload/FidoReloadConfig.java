package com.crosscert.fidoadmin.system.reload;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class FidoReloadConfig {

    /** 스레드 1, 대기열 50. 넘치면 execute 가 거부 예외를 던지고 리스너가 WARN 후 버린다. */
    @Bean(name = "fidoReloadExecutor")
    public Executor fidoReloadExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("fido-reload-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }
}
