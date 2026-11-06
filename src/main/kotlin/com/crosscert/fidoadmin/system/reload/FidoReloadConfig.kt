package com.crosscert.fidoadmin.system.reload

import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class FidoReloadConfig {

    /** 스레드 1, 대기열 50. 넘치면 execute 가 거부 예외를 던지고 리스너가 WARN 후 버린다. */
    @Bean(name = ["fidoReloadExecutor"])
    fun fidoReloadExecutor(): Executor {
        val ex = ThreadPoolTaskExecutor()
        ex.corePoolSize = 1
        ex.maxPoolSize = 1
        ex.queueCapacity = 50
        ex.setThreadNamePrefix("fido-reload-")
        ex.setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
        ex.setWaitForTasksToCompleteOnShutdown(false)
        ex.initialize()
        return ex
    }
}
