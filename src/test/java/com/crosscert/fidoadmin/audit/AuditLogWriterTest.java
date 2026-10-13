package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** 감사 INSERT 는 요청 스레드가 기다리지 않는다. 큐가 넘치면 동기로 되돌아가고, 종료 때는 큐를 비운다. */
class AuditLogWriterTest {

    CcfaAuditLogRepository repository = mock(CcfaAuditLogRepository.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    AuditLogWriter writer;

    AuditLogWriterTest() {
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @AfterEach void stop() { if (writer != null) writer.shutdown(); }

    @Test void writeReturnsBeforeInsertAndInsertsOnWorkerInNewTransaction() throws Exception {
        writer = new AuditLogWriter(repository, tx, 10);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(inv -> {
            thread.set(Thread.currentThread().getName());
            release.await(5, TimeUnit.SECONDS);
            return inv.getArgument(0);
        });

        writer.write(new CcfaAuditLog());   // INSERT 가 막혀 있어도 바로 돌아온다

        release.countDown();
        verify(repository, timeout(2000)).save(any());
        assertThat(thread.get()).startsWith("audit-writer-");
        verify(tx).getTransaction(org.mockito.ArgumentMatchers.argThat(
            (TransactionDefinition d) -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test void fullQueueFallsBackToCallerThread() throws Exception {
        writer = new AuditLogWriter(repository, tx, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        String caller = Thread.currentThread().getName();
        AtomicReference<String> fallbackThread = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(inv -> {
            CcfaAuditLog row = inv.getArgument(0);
            if ("first".equals(row.getMessage())) { started.countDown(); release.await(5, TimeUnit.SECONDS); }
            if ("third".equals(row.getMessage())) fallbackThread.set(Thread.currentThread().getName());
            return row;
        });

        writer.write(row("first"));       // 작업 스레드가 잡고 멈춘다
        started.await(2, TimeUnit.SECONDS);
        writer.write(row("second"));      // 큐 1칸을 채운다
        writer.write(row("third"));       // 넘친다 → 호출 스레드에서 바로 저장

        assertThat(fallbackThread.get()).isEqualTo(caller);
        release.countDown();
    }

    @Test void shutdownDrainsQueuedRows() {
        writer = new AuditLogWriter(repository, tx, 10);
        for (int i = 0; i < 5; i++) writer.write(row("r" + i));
        writer.shutdown();
        verify(repository, org.mockito.Mockito.times(5)).save(any());
    }

    /** 종료 뒤에 들어온 기록도 버리지 않고 호출 스레드에서 저장한다. */
    @Test void writeAfterShutdownSavesOnCaller() {
        writer = new AuditLogWriter(repository, tx, 10);
        writer.shutdown();
        writer.write(row("late"));
        verify(repository).save(any());
    }

    @Test void insertFailureIsSwallowed() {
        writer = new AuditLogWriter(repository, tx, 10);
        when(repository.save(any())).thenThrow(new RuntimeException("ORA-00001"));
        writer.write(row("x"));
        writer.shutdown();
        verify(tx, timeout(2000)).rollback(any());
        verify(tx, never()).commit(any());
    }

    private static CcfaAuditLog row(String message) {
        CcfaAuditLog r = new CcfaAuditLog();
        r.setMessage(message);
        return r;
    }
}
