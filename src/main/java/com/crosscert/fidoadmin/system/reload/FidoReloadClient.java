package com.crosscert.fidoadmin.system.reload;

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 등록된 FIDO 서버(STATUS='ON')마다 {@code GET {SERVERURL}/api/command/reload} 를 보낸다.
 *
 * <p>서버 목록은 인증 없는 자가등록(/api/svc/reg)으로 채워지므로 http/https 만 보내고 리다이렉트는 따라가지 않는다.
 * 한 서버의 실패가 다음 서버를 막지 않는다. 어떤 경우에도 예외를 던지지 않고 결과로 돌려준다.
 */
@Component
public class FidoReloadClient {

    static final String STATUS_ON = "ON";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private final CcfaFidoclientRepository clients;
    private final HttpClient http;
    private final Duration responseTimeout;

    @Autowired
    public FidoReloadClient(CcfaFidoclientRepository clients) {
        this(clients, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build(), RESPONSE_TIMEOUT);
    }

    FidoReloadClient(CcfaFidoclientRepository clients, HttpClient http, Duration responseTimeout) {
        this.clients = clients;
        this.http = http;
        this.responseTimeout = responseTimeout;
    }

    /**
     * 모든 서버에 요청을 먼저 띄운 뒤 서버 코드 순으로 결과를 모은다. 하나씩 보내고 기다리면
     * 수동 reload 가 서버 수 × 응답 타임아웃까지 걸린다. 결과 순서는 서버 코드 순 그대로다.
     */
    public List<ReloadResult> reloadAll() {
        List<Pending> pending = new ArrayList<>();
        for (CcfaFidoclient c : clients.findByStatusOrderByServercodeAsc(STATUS_ON)) {
            pending.add(start(c));
        }
        List<ReloadResult> results = new ArrayList<>(pending.size());
        for (Pending p : pending) {
            results.add(p.await());
        }
        return results;
    }

    /** 띄워 둔 요청 하나. URL 이 틀려 보내지 못한 경우는 {@code failed} 에 결과가 미리 들어 있다. */
    private final class Pending {
        private final CcfaFidoclient client;
        private final String raw;
        private final long start = System.nanoTime();
        private CompletableFuture<HttpResponse<Void>> future;
        private ReloadResult failed;

        Pending(CcfaFidoclient client) {
            this.client = client;
            this.raw = client.getServerurl();
        }

        ReloadResult await() {
            if (failed != null) return failed;
            try {
                // HttpRequest.timeout 은 헤더까지만 본다. 본문을 천천히 흘려 스레드를 붙잡지 못하게 전체를 자른다.
                // 요청들은 함께 띄웠으므로 각자 자기 시작 시각 기준으로 남은 시간만 기다린다.
                long remaining = Math.max(0, responseTimeout.toMillis() - millisSince(start));
                int code = future.get(remaining, TimeUnit.MILLISECONDS).statusCode();
                return result(code >= 200 && code < 300, "HTTP " + code);
            } catch (TimeoutException e) {
                future.cancel(true);
                return result(false, "request timed out");
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return result(false, "중단됨");
            } catch (Exception e) {
                Throwable t = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage();
                return result(false, oneLine(msg));
            }
        }

        private ReloadResult result(boolean ok, String detail) {
            return new ReloadResult(client.getServercode(), raw, ok, detail, millisSince(start));
        }
    }

    private Pending start(CcfaFidoclient c) {
        Pending p = new Pending(c);
        String raw = c.getServerurl();
        String base = raw == null ? "" : raw.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = base.isEmpty() ? null : URI.create(base);
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        String scheme = uri == null ? null : uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
            || uri.getHost() == null || uri.getRawQuery() != null
            || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            p.failed = new ReloadResult(c.getServercode(), raw, false, oneLine("지원하지 않는 URL: " + (raw == null ? "" : raw.trim())), 0);
            return p;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/command/reload"))
                .timeout(responseTimeout).GET().build();
            p.future = http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (RuntimeException e) {
            // 요청을 만들거나 띄우는 단계의 오류. 기존처럼 결과로만 돌려주고 다음 서버를 막지 않는다.
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
            p.failed = new ReloadResult(c.getServercode(), raw, false, oneLine(msg), millisSince(p.start));
        }
        return p;
    }

    private static long millisSince(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }

    static String oneLine(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("[\\r\\n]+", " ");
        return flat.length() > 200 ? flat.substring(0, 200) : flat;
    }
}
