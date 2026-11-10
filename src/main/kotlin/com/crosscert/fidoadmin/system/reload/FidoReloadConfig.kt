package com.crosscert.fidoadmin.system.reload

import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class FidoReloadConfig {

    /**
     * 스레드 1, 대기열 50. 리스너가 대기 중 표시(`pending`)로 요청을 합치므로 대기열에는 많아야 1개만 쌓여
     * 용량이 차는 일은 없다. execute 가 거부 예외를 던지는 것은 실행기가 종료된 뒤뿐이고, 그때 리스너가 WARN 후 버린다.
     */
    @Bean(name = ["fidoReloadExecutor"])
    fun fidoReloadExecutor(): Executor {
        val ex = ThreadPoolTaskExecutor()
        ex.corePoolSize = 1
        ex.maxPoolSize = 1
        ex.queueCapacity = 50
        ex.setThreadNamePrefix("fido-reload-")
        ex.setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
        ex.setWaitForTasksToCompleteOnShutdown(false)
        return ex
    }
}
