package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mockito;

/** 커밋 뒤에만, 한 번, 실행기에서 돈다. 롤백이면 보내지 않는다. 실패는 호출자에게 새지 않는다. */
@SpringJUnitConfig(FidoReloadListenerTest.Config.class)
class FidoReloadListenerTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean FidoReloadClient client() { return mock(FidoReloadClient.class); }
        @Bean Executor fidoReloadExecutor() { return Runnable::run; }
        @Bean FidoReloadListener listener(FidoReloadClient c, Executor fidoReloadExecutor) { return new FidoReloadListener(c, fidoReloadExecutor); }
        @Bean PlatformTransactionManager txManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition def) {}
                @Override protected void doCommit(DefaultTransactionStatus s) {}
                @Override protected void doRollback(DefaultTransactionStatus s) {}
            };
        }
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager tx;
    @Autowired FidoReloadClient client;

    @BeforeEach void reset() { Mockito.reset(client); when(client.reloadAll()).thenReturn(List.of()); }

    @Test void sendsOnceAfterCommit() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            publisher.publishEvent(new FidoConfigChanged("APPID 수정 1"));
            verify(client, never()).reloadAll();   // 커밋 전에는 보내지 않는다
        });
        verify(client, times(1)).reloadAll();
    }

    @Test void doesNotSendOnRollback() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            publisher.publishEvent(new FidoConfigChanged("APPID 수정 1"));
            s.setRollbackOnly();
        });
        verify(client, never()).reloadAll();
    }

    @Test void sendsWhenPublishedOutsideTransaction() {
        publisher.publishEvent(new FidoConfigChanged("수동"));
        verify(client, times(1)).reloadAll();
    }

    @Test void clientFailureDoesNotPropagate() {
        when(client.reloadAll()).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> publisher.publishEvent(new FidoConfigChanged("x"))).doesNotThrowAnyException();
    }

    @Test void rejectedExecutionDoesNotPropagate() {
        Executor rejecting = r -> { throw new TaskRejectedException("full"); };
        FidoReloadListener l = new FidoReloadListener(client, rejecting);
        assertThatCode(() -> l.on(new FidoConfigChanged("x"))).doesNotThrowAnyException();
        verify(client, never()).reloadAll();
    }

    @Test void pendingReloadsAreCoalesced() {
        java.util.List<Runnable> tasks = new java.util.ArrayList<>();
        FidoReloadListener l = new FidoReloadListener(client, tasks::add);
        l.on(new FidoConfigChanged("1")); l.on(new FidoConfigChanged("2")); l.on(new FidoConfigChanged("3"));
        org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        verify(client, times(1)).reloadAll();
        l.on(new FidoConfigChanged("4"));
        org.assertj.core.api.Assertions.assertThat(tasks).hasSize(2);
    }

    @Test void rejectionResetsPendingSoNextEventIsSubmitted() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Executor rejecting = r -> { calls.incrementAndGet(); throw new TaskRejectedException("full"); };
        FidoReloadListener l = new FidoReloadListener(client, rejecting);
        l.on(new FidoConfigChanged("a")); l.on(new FidoConfigChanged("b"));
        org.assertj.core.api.Assertions.assertThat(calls.get()).isEqualTo(2);
    }
}
