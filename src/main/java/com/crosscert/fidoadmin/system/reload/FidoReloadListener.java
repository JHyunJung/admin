package com.crosscert.fidoadmin.system.reload;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 설정 변경이 커밋된 뒤 FIDO 서버들에 reload 를 보낸다. 커밋 전에 보내면 FIDO 서버가 바뀌기 전 값을
 * 다시 읽을 수 있어서 커밋 뒤에만, 저장을 기다리게 하지 않도록 전용 실행기에서 보낸다.
 *
 * <p>대기 중인 전송이 이미 있으면 새 이벤트는 합쳐진다(reload 는 멱등이고 항상 모든 서버에 가므로
 * 마지막 커밋 뒤 한 번이면 충분하다). 실행이 시작되면 곧바로 대기 표시를 내려, 실행 중 커밋된 변경은 한 번 더 돈다.
 *
 * <p>감사 로그는 남기지 않는다. 비동기 스레드에는 로그인 사용자가 없고, 원인이 된 변경은 이미 감사 로그에 있다.
 */
@Slf4j
@Component
public class FidoReloadListener {

    private final FidoReloadClient client;
    private final Executor executor;
    private final AtomicBoolean pending = new AtomicBoolean(false);

    public FidoReloadListener(FidoReloadClient client, @Qualifier("fidoReloadExecutor") Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FidoConfigChanged event) {
        if (!pending.compareAndSet(false, true)) {
            log.debug("FIDO 서버 reload 가 이미 대기 중이라 합칩니다: reason={}", event.reason());
            return;
        }
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            pending.set(false);
            // reload 는 멱등이다. 다음 변경이나 수동 전송이 대신한다.
            log.warn("FIDO 서버 reload 대기열이 가득 차 버립니다: reason={}", event.reason());
        }
    }

    private void send(FidoConfigChanged event) {
        pending.set(false);
        try {
            for (ReloadResult r : client.reloadAll()) {
                if (r.ok()) {
                    log.info("FIDO 서버 reload 성공: reason={} server={} {} {}ms", event.reason(), r.servercode(), r.detail(), r.elapsedMs());
                } else {
                    log.warn("FIDO 서버 reload 실패: reason={} server={} url={} {}", event.reason(), r.servercode(), FidoReloadClient.oneLine(r.serverurl()), r.detail());
                }
            }
        } catch (RuntimeException e) {
            log.warn("FIDO 서버 reload 중 오류: reason={}", event.reason(), e);
        }
    }
}
