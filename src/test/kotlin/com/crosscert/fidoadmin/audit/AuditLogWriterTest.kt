package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus

/** 감사 INSERT 는 요청 스레드가 기다리지 않는다. 큐가 넘치면 동기로 되돌아가고, 종료 때는 큐를 비운다. */
class AuditLogWriterTest {

    val repository: CcfaAuditLogRepository = mock(CcfaAuditLogRepository::class.java)
    val tx: PlatformTransactionManager = mock(PlatformTransactionManager::class.java)
    var writer: AuditLogWriter? = null

    init {
        whenever(tx.getTransaction(anyOrNull())).thenReturn(mock(TransactionStatus::class.java))
    }

    @AfterEach fun stop() { writer?.shutdown() }

    @Test fun writeReturnsBeforeInsertAndInsertsOnWorkerInNewTransaction() {
        val writer = AuditLogWriter(repository, tx, 10).also { this.writer = it }
        val release = CountDownLatch(1)
        val thread = AtomicReference<String>()
        whenever(repository.save(any<CcfaAuditLog>())).thenAnswer { inv ->
            thread.set(Thread.currentThread().name)
            release.await(5, TimeUnit.SECONDS)
            inv.getArgument<CcfaAuditLog>(0)
        }

        writer.write(CcfaAuditLog())   // INSERT 가 막혀 있어도 바로 돌아온다

        release.countDown()
        verify(repository, timeout(2000)).save(any<CcfaAuditLog>())
        assertThat(thread.get()).startsWith("audit-writer-")
        verify(tx).getTransaction(argThat { d: TransactionDefinition ->
            d.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRES_NEW })
    }

    @Test fun fullQueueFallsBackToCallerThread() {
        val writer = AuditLogWriter(repository, tx, 1).also { this.writer = it }
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val caller = Thread.currentThread().name
        val fallbackThread = AtomicReference<String>()
        whenever(repository.save(any<CcfaAuditLog>())).thenAnswer { inv ->
            val row = inv.getArgument<CcfaAuditLog>(0)
            if ("first" == row.message) { started.countDown(); release.await(5, TimeUnit.SECONDS) }
            if ("third" == row.message) fallbackThread.set(Thread.currentThread().name)
            row
        }

        writer.write(row("first"))       // 작업 스레드가 잡고 멈춘다
        started.await(2, TimeUnit.SECONDS)
        writer.write(row("second"))      // 큐 1칸을 채운다
        writer.write(row("third"))       // 넘친다 → 호출 스레드에서 바로 저장

        assertThat(fallbackThread.get()).isEqualTo(caller)
        release.countDown()
    }

    @Test fun shutdownDrainsQueuedRows() {
        val writer = AuditLogWriter(repository, tx, 10).also { this.writer = it }
        for (i in 0 until 5) writer.write(row("r$i"))
        writer.shutdown()
        verify(repository, times(5)).save(any<CcfaAuditLog>())
    }

    /** 종료 뒤에 들어온 기록도 버리지 않고 호출 스레드에서 저장한다. */
    @Test fun writeAfterShutdownSavesOnCaller() {
        val writer = AuditLogWriter(repository, tx, 10).also { this.writer = it }
        writer.shutdown()
        writer.write(row("late"))
        verify(repository).save(any<CcfaAuditLog>())
    }

    @Test fun insertFailureIsSwallowed() {
        val writer = AuditLogWriter(repository, tx, 10).also { this.writer = it }
        whenever(repository.save(any<CcfaAuditLog>())).thenThrow(RuntimeException("ORA-00001"))
        writer.write(row("x"))
        writer.shutdown()
        verify(tx, timeout(2000)).rollback(any())
        verify(tx, never()).commit(any())
    }

    private companion object {
        fun row(message: String): CcfaAuditLog {
            val r = CcfaAuditLog()
            r.message = message
            return r
        }
    }
}
