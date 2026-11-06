package com.crosscert.fidoadmin.system.reload

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function

class FidoReloadClientTest {

    lateinit var server: HttpServer
    lateinit var base: String
    val hits: MutableList<String> = CopyOnWriteArrayList()
    val barrier = CountDownLatch(2)
    val repo: CcfaFidoclientRepository = mock()
    lateinit var client: FidoReloadClient

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok/api/command/reload") { ex -> hits.add(ex.requestMethod + " " + ex.requestURI)
            val b = "reloaded".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close() }
        server.createContext("/fail/api/command/reload") { ex -> hits.add("fail"); ex.sendResponseHeaders(500, -1); ex.close() }
        server.createContext("/slow/api/command/reload") { ex ->
            try { Thread.sleep(1500) } catch (ignored: InterruptedException) {}
            ex.sendResponseHeaders(200, -1); ex.close() }
        server.createContext("/redirect/api/command/reload") { ex ->
            ex.responseHeaders.add("Location", "$base/ok/api/command/reload"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/trickle/api/command/reload") { ex ->
            try {
                ex.sendResponseHeaders(200, 0)
                for (i in 0 until 15) { ex.responseBody.write('x'.code); ex.responseBody.flush(); Thread.sleep(200) }
            } catch (ignored: Exception) {}
            ex.close() }
        // 두 요청이 모두 도착해야 응답한다. 순차 전송이면 첫 요청이 응답 타임아웃(500ms)에 걸려 실패한다.
        server.createContext("/barrier/api/command/reload") { ex ->
            try { barrier.countDown(); barrier.await(2, TimeUnit.SECONDS) } catch (ignored: InterruptedException) {}
            ex.sendResponseHeaders(200, -1); ex.close() }
        // 기본 실행기는 단일 스레드라 동시 요청을 받지 못한다(barrier 가 영영 열리지 않는다).
        server.executor = Executors.newCachedThreadPool()
        server.start()
        base = "http://127.0.0.1:" + server.address.port
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build()
        client = FidoReloadClient(repo, http, Duration.ofMillis(500))
    }

    @AfterEach fun stop() { server.stop(0) }

    private fun c(code: String, url: String?): CcfaFidoclient {
        val c = CcfaFidoclient(); c.servercode = code; c.serverurl = url; c.status = "ON"; return c
    }

    private val servercode = Function<ReloadResult, String?> { it.servercode }
    private val ok = Function<ReloadResult, Boolean> { it.ok }

    @Test fun sendsGetToEachOnServerAndReportsSuccess() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/ok")))
        val r = client.reloadAll()
        assertThat(hits).containsExactly("GET /ok/api/command/reload")
        assertThat(r).singleElement().satisfies({ x ->
            assertThat(x.servercode).isEqualTo("A")
            assertThat(x.ok).isTrue()
            assertThat(x.detail).isEqualTo("HTTP 200")
        })
    }

    /**
     * 서버 N대에 순서대로 보내고 하나씩 기다리면 수동 reload 가 N × 응답 타임아웃까지 걸린다.
     * 요청을 모두 띄운 뒤 기다려야 한다. 결과 순서(서버 코드 순)는 그대로다.
     */
    @Test fun sendsToAllServersConcurrently() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/barrier"), c("B", "$base/barrier")))
        val r = client.reloadAll()
        assertThat(r).extracting(servercode).containsExactly("A", "B")
        assertThat(r).extracting(ok).containsExactly(true, true)
    }

    @Test fun trimsTrailingSlashAndWhitespace() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "  $base/ok/  ")))
        assertThat(client.reloadAll()[0].ok).isTrue()
        assertThat(hits).containsExactly("GET /ok/api/command/reload")
    }

    @Test fun non2xxIsFailureAndNextServerStillRuns() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/fail"), c("B", "$base/ok")))
        val r = client.reloadAll()
        assertThat(r).extracting(ok).containsExactly(false, true)
        assertThat(r[0].detail).isEqualTo("HTTP 500")
    }

    @Test fun timeoutIsFailure() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/slow")))
        val r = client.reloadAll()[0]
        assertThat(r.ok).isFalse()
        assertThat(r.detail).containsIgnoringCase("timed out")
    }

    @Test fun redirectIsNotFollowed() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/redirect")))
        val r = client.reloadAll()[0]
        assertThat(r.ok).isFalse()
        assertThat(r.detail).isEqualTo("HTTP 302")
        assertThat(hits).isEmpty()
    }

    @Test fun badSchemeBlankAndMalformedUrlsAreFailuresWithoutThrowing() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(
            c("A", "ftp://x/"), c("B", ""), c("C", null), c("D", "http://bad host"), c("E", "$base/ok")))
        val r = client.reloadAll()
        assertThat(r).extracting(ok).containsExactly(false, false, false, false, true)
        assertThat(r[0].detail).isEqualTo("지원하지 않는 URL: ftp://x/")
    }

    @Test fun trickledBodyIsBoundedByResponseTimeout() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/trickle")))
        val t0 = System.nanoTime()
        val r = client.reloadAll()[0]
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(2000)
        assertThat(r.ok).isFalse()
        assertThat(r.detail).containsIgnoringCase("timed out")
    }

    @Test fun queryFragmentUserInfoAndHostlessUrlsAreRejected() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(
            c("A", "http://h/x?y"), c("B", "http://h/x#f"), c("C", "http://u:p@h/"), c("D", "http:///x")))
        assertThat(client.reloadAll()).extracting(ok).containsExactly(false, false, false, false)
        assertThat(hits).isEmpty()
    }

    @Test fun uppercaseSchemeIsAccepted() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "HTTP://" + base.substring(7) + "/ok")))
        assertThat(client.reloadAll()[0].ok).isTrue()
    }

    @Test fun noOnServersMeansNoResults() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf())
        assertThat(client.reloadAll()).isEmpty()
    }

    @Test fun detailIsSingleLineAndCapped() {
        assertThat(FidoReloadClient.oneLine("a\nb\r\nc")).isEqualTo("a b c")
        assertThat(FidoReloadClient.oneLine("x".repeat(300))).hasSize(200)
        assertThat(FidoReloadClient.oneLine(null)).isEmpty()
    }
}
