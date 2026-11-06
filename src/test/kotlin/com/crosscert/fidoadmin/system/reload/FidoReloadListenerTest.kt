package com.crosscert.fidoadmin.system.reload

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskRejectedException
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** 커밋 뒤에만, 한 번, 실행기에서 돈다. 롤백이면 보내지 않는다. 실패는 호출자에게 새지 않는다. */
@SpringJUnitConfig(FidoReloadListenerTest.Config::class)
class FidoReloadListenerTest {

    @Configuration
    @EnableTransactionManagement
    class Config {
        @Bean fun client(): FidoReloadClient = mock()
        @Bean fun fidoReloadExecutor(): Executor = Executor { it.run() }
        @Bean fun listener(c: FidoReloadClient, fidoReloadExecutor: Executor): FidoReloadListener = FidoReloadListener(c, fidoReloadExecutor)
        @Bean fun txManager(): PlatformTransactionManager =
            object : AbstractPlatformTransactionManager() {
                override fun doGetTransaction(): Any = Any()
                override fun doBegin(tx: Any, def: TransactionDefinition) {}
                override fun doCommit(s: DefaultTransactionStatus) {}
                override fun doRollback(s: DefaultTransactionStatus) {}
            }
    }

    @Autowired lateinit var publisher: ApplicationEventPublisher
    @Autowired lateinit var tx: PlatformTransactionManager
    @Autowired lateinit var client: FidoReloadClient

    @BeforeEach fun reset() { Mockito.reset(client); whenever(client.reloadAll()).thenReturn(listOf()) }

    @Test fun sendsOnceAfterCommit() {
        TransactionTemplate(tx).executeWithoutResult {
            publisher.publishEvent(FidoConfigChanged("APPID 수정 1"))
            verify(client, never()).reloadAll()   // 커밋 전에는 보내지 않는다
        }
        verify(client, times(1)).reloadAll()
    }

    @Test fun doesNotSendOnRollback() {
        TransactionTemplate(tx).executeWithoutResult { s ->
            publisher.publishEvent(FidoConfigChanged("APPID 수정 1"))
            s.setRollbackOnly()
        }
        verify(client, never()).reloadAll()
    }

    @Test fun sendsWhenPublishedOutsideTransaction() {
        publisher.publishEvent(FidoConfigChanged("수동"))
        verify(client, times(1)).reloadAll()
    }

    @Test fun clientFailureDoesNotPropagate() {
        whenever(client.reloadAll()).thenThrow(IllegalStateException("boom"))
        assertThatCode { publisher.publishEvent(FidoConfigChanged("x")) }.doesNotThrowAnyException()
    }

    @Test fun rejectedExecutionDoesNotPropagate() {
        val rejecting = Executor { throw TaskRejectedException("full") }
        val l = FidoReloadListener(client, rejecting)
        assertThatCode { l.on(FidoConfigChanged("x")) }.doesNotThrowAnyException()
        verify(client, never()).reloadAll()
    }

    @Test fun pendingReloadsAreCoalesced() {
        val tasks = ArrayList<Runnable>()
        val l = FidoReloadListener(client) { tasks.add(it) }
        l.on(FidoConfigChanged("1")); l.on(FidoConfigChanged("2")); l.on(FidoConfigChanged("3"))
        Assertions.assertThat(tasks).hasSize(1)
        tasks[0].run()
        verify(client, times(1)).reloadAll()
        l.on(FidoConfigChanged("4"))
        Assertions.assertThat(tasks).hasSize(2)
    }

    @Test fun rejectionResetsPendingSoNextEventIsSubmitted() {
        val calls = AtomicInteger()
        val rejecting = Executor { calls.incrementAndGet(); throw TaskRejectedException("full") }
        val l = FidoReloadListener(client, rejecting)
        l.on(FidoConfigChanged("a")); l.on(FidoConfigChanged("b"))
        Assertions.assertThat(calls.get()).isEqualTo(2)
    }
}
