package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FidoReloadClientTest {

    HttpServer server;
    String base;
    List<String> hits = new CopyOnWriteArrayList<>();
    CcfaFidoclientRepository repo = mock(CcfaFidoclientRepository.class);
    FidoReloadClient client;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/api/command/reload", ex -> { hits.add(ex.getRequestMethod() + " " + ex.getRequestURI());
            byte[] b = "reloaded".getBytes(); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        server.createContext("/fail/api/command/reload", ex -> { hits.add("fail"); ex.sendResponseHeaders(500, -1); ex.close(); });
        server.createContext("/slow/api/command/reload", ex -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/redirect/api/command/reload", ex -> {
            ex.getResponseHeaders().add("Location", base + "/ok/api/command/reload"); ex.sendResponseHeaders(302, -1); ex.close(); });
        server.createContext("/trickle/api/command/reload", ex -> {
            try {
                ex.sendResponseHeaders(200, 0);
                for (int i = 0; i < 15; i++) { ex.getResponseBody().write('x'); ex.getResponseBody().flush(); Thread.sleep(200); }
            } catch (Exception ignored) {}
            ex.close(); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        client = new FidoReloadClient(repo, http, Duration.ofMillis(500));
    }

    @AfterEach void stop() { server.stop(0); }

    private CcfaFidoclient c(String code, String url) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServerurl(url); c.setStatus("ON"); return c;
    }

    @Test void sendsGetToEachOnServerAndReportsSuccess() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(hits).containsExactly("GET /ok/api/command/reload");
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.servercode()).isEqualTo("A");
            assertThat(x.ok()).isTrue();
            assertThat(x.detail()).isEqualTo("HTTP 200");
        });
    }

    @Test void trimsTrailingSlashAndWhitespace() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", "  " + base + "/ok/  ")));
        assertThat(client.reloadAll().get(0).ok()).isTrue();
        assertThat(hits).containsExactly("GET /ok/api/command/reload");
    }

    @Test void non2xxIsFailureAndNextServerStillRuns() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/fail"), c("B", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(r).extracting(ReloadResult::ok).containsExactly(false, true);
        assertThat(r.get(0).detail()).isEqualTo("HTTP 500");
    }

    @Test void timeoutIsFailure() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/slow")));
        ReloadResult r = client.reloadAll().get(0);
        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).containsIgnoringCase("timed out");
    }

    @Test void redirectIsNotFollowed() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/redirect")));
        ReloadResult r = client.reloadAll().get(0);
        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).isEqualTo("HTTP 302");
        assertThat(hits).isEmpty();
    }

    @Test void badSchemeBlankAndMalformedUrlsAreFailuresWithoutThrowing() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(
            c("A", "ftp://x/"), c("B", ""), c("C", null), c("D", "http://bad host"), c("E", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(r).extracting(ReloadResult::ok).containsExactly(false, false, false, false, true);
        assertThat(r.get(0).detail()).isEqualTo("지원하지 않는 URL: ftp://x/");
    }

    @Test void trickledBodyIsBoundedByResponseTimeout() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/trickle")));
        long t0 = System.nanoTime();
        ReloadResult r = client.reloadAll().get(0);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(2000);
        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).containsIgnoringCase("timed out");
    }

    @Test void queryFragmentUserInfoAndHostlessUrlsAreRejected() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(
            c("A", "http://h/x?y"), c("B", "http://h/x#f"), c("C", "http://u:p@h/"), c("D", "http:///x")));
        assertThat(client.reloadAll()).extracting(ReloadResult::ok).containsExactly(false, false, false, false);
        assertThat(hits).isEmpty();
    }

    @Test void uppercaseSchemeIsAccepted() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", "HTTP://" + base.substring(7) + "/ok")));
        assertThat(client.reloadAll().get(0).ok()).isTrue();
    }

    @Test void noOnServersMeansNoResults() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of());
        assertThat(client.reloadAll()).isEmpty();
    }

    @Test void detailIsSingleLineAndCapped() {
        assertThat(FidoReloadClient.oneLine("a\nb\r\nc")).isEqualTo("a b c");
        assertThat(FidoReloadClient.oneLine("x".repeat(300))).hasSize(200);
        assertThat(FidoReloadClient.oneLine(null)).isEmpty();
    }
}
