package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CCFA_AUDIT_LOG INSERT. 요청 스레드는 기다리지 않는다 — 행은 {@link AuditLogger} 가 요청 스레드에서 다 만들어
 * (사용자·IP·시각·해시) 넘기고, 여기서는 전용 스레드 하나가 저장만 한다. DB 에 남는 행은 동기 저장과 같다.
 *
 * <p>대기열은 {@value #QUEUE_CAPACITY} 건이다. 넘치면 호출 스레드에서 바로 저장한다(유실보다 지연이 낫다).
 * 종료 때는 대기열을 비울 때까지 기다린다. 이 빈은 리포지터리에 의존하므로 DB 연결이 닫히기 전에 종료된다.
 * 프로세스가 강제로 죽으면 대기열에 남은 건은 유실된다 — 비동기화의 대가다.
 *
 * <p>저장은 언제나 새 트랜잭션이다(REQUIRES_NEW). 호출자 트랜잭션이 롤백돼도 감사 기록은 남는다.
 * 실패는 로그로만 남기고 호출자에게 던지지 않는다.
 */
@Slf4j
@Component
public class AuditLogWriter {

    static final int QUEUE_CAPACITY = 500;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final CcfaAuditLogRepository repository;
    private final TransactionTemplate requiresNew;
    private final ThreadPoolExecutor executor;

    @Autowired
    public AuditLogWriter(CcfaAuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this(repository, transactionManager, QUEUE_CAPACITY);
    }

    AuditLogWriter(CcfaAuditLogRepository repository, PlatformTransactionManager transactionManager, int queueCapacity) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            r -> {
                Thread t = new Thread(r, "audit-writer-" + seq.incrementAndGet());
                t.setDaemon(false);
                return t;
            },
            // 대기열이 찼거나 종료된 뒤면 호출 스레드에서 바로 저장한다. CallerRunsPolicy 는 종료 뒤의 작업을 버린다.
            (r, ex) -> r.run());
    }

    public void write(CcfaAuditLog row) {
        executor.execute(() -> save(row));
    }

    private void save(CcfaAuditLog row) {
        try {
            requiresNew.executeWithoutResult(status -> repository.save(row));
        } catch (RuntimeException e) {
            log.error("감사 로그 저장 실패: type={} message={}", row.getType(), row.getMessage(), e);
        }
    }

    /** 대기열을 비우고 끝낸다. 이후 들어오는 write 는 호출 스레드에서 바로 저장된다. */
    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.error("감사 로그 대기열을 {}초 안에 비우지 못했습니다. 남은 {}건 유실", SHUTDOWN_WAIT_SECONDS,
                    executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("감사 로그 대기열 종료 대기 중 인터럽트. 남은 {}건 유실", executor.shutdownNow().size());
        }
    }
}
