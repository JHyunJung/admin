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

    public List<ReloadResult> reloadAll() {
        List<ReloadResult> results = new ArrayList<>();
        for (CcfaFidoclient c : clients.findByStatusOrderByServercodeAsc(STATUS_ON)) {
            results.add(send(c));
        }
        return results;
    }

    private ReloadResult send(CcfaFidoclient c) {
        long start = System.nanoTime();
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
            return new ReloadResult(c.getServercode(), raw, false, oneLine("지원하지 않는 URL: " + (raw == null ? "" : raw.trim())), 0);
        }
        CompletableFuture<HttpResponse<Void>> f = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/command/reload"))
                .timeout(responseTimeout).GET().build();
            f = http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
            // HttpRequest.timeout 은 헤더까지만 본다. 본문을 천천히 흘려 스레드를 붙잡지 못하게 전체를 자른다.
            int code = f.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS).statusCode();
            return new ReloadResult(c.getServercode(), raw, code >= 200 && code < 300, "HTTP " + code, millisSince(start));
        } catch (TimeoutException e) {
            f.cancel(true);
            return new ReloadResult(c.getServercode(), raw, false, "request timed out", millisSince(start));
        } catch (InterruptedException e) {
            if (f != null) f.cancel(true);
            Thread.currentThread().interrupt();
            return new ReloadResult(c.getServercode(), raw, false, "중단됨", millisSince(start));
        } catch (Exception e) {
            Throwable t = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage();
            return new ReloadResult(c.getServercode(), raw, false, oneLine(msg), millisSince(start));
        }
    }

    private static long millisSince(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }

    static String oneLine(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("[\\r\\n]+", " ");
        return flat.length() > 200 ? flat.substring(0, 200) : flat;
    }
}
