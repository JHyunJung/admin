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
    /** trickle 응답을 쓰던 중 클라이언트가 연결을 끊으면 열린다(쓰기 예외). */
    val trickleAborted = CountDownLatch(1)
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
            } catch (aborted: java.io.IOException) {
                trickleAborted.countDown()
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

    /** 링크로컬(클라우드 메타데이터)·와일드카드 주소로는 보내지 않는다. 숫자 표기·DNS 이름도 풀어서 본다. */
    @Test fun linkLocalAndWildcardHostsAreBlockedWithoutSending() {
        val metadata = java.net.InetAddress.getByAddress("metadata.internal", byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()))
        val blocking = FidoReloadClient(repo, HttpClient.newHttpClient(), Duration.ofMillis(500)) { host ->
            if (host == "metadata.internal") arrayOf(metadata) else java.net.InetAddress.getAllByName(host)
        }
        val urls = listOf("http://169.254.169.254", "http://2852039166/latest", "http://[fe80::1]:8080",
            "http://0.0.0.0:" + server.address.port, "http://metadata.internal/computeMetadata")
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(urls.mapIndexed { i, u -> c("X$i", u) })
        val r = blocking.reloadAll()
        assertThat(r).extracting(ok).containsOnly(false)
        assertThat(r).allSatisfy({ x -> assertThat(x.detail).startsWith("차단된 주소: ") })
        assertThat(r[0].detail).isEqualTo("차단된 주소: http://169.254.169.254")
        assertThat(hits).isEmpty()
    }

    /** FIDO 서버가 어드민과 같은 장비면 루프백으로 등록된다. 루프백은 막지 않는다. */
    @Test fun loopbackIsStillAllowed() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "$base/ok")))
        assertThat(client.reloadAll()[0].ok).isTrue()
        assertThat(hits).containsExactly("GET /ok/api/command/reload")
    }

    /** 호스트 검사(DNS 해석)가 느린 서버가 있어도 다른 서버의 전송은 기다리지 않고, 느린 쪽은 응답 시간 예산에서 끊긴다. */
    @Test fun slowHostCheckDoesNotDelayOtherServers() {
        val slowDns = FidoReloadClient(repo, HttpClient.newHttpClient(), Duration.ofMillis(500)) { host ->
            if (host == "slow-dns.internal") { Thread.sleep(3000); arrayOf() } else java.net.InetAddress.getAllByName(host)
        }
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "http://slow-dns.internal"), c("B", "$base/ok")))
        val started = System.nanoTime()
        val r = slowDns.reloadAll()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertThat(r[0].ok).isFalse()
        assertThat(r[0].detail).isEqualTo("request timed out")
        assertThat(r[1].ok).isTrue()
        assertThat(tookMs).isLessThan(2000)
    }

    /**
     * 검사 실행기는 스레드 4개다. 해석이 멎은 서버 6대면 2대는 대기열에서 기다리다 응답 시간 예산이 끝난다.
     * 포기한 대기 작업은 나중에 차례가 와도 DNS 를 부르지 않아야 한다(실행기를 붙잡아 뒤 reload 를 막지 않는다).
     */
    @Test fun abandonedQueuedHostChecksDoNotResolve() {
        val release = CountDownLatch(1)
        val resolved = java.util.concurrent.atomic.AtomicInteger()
        val stuckDns = FidoReloadClient(repo, HttpClient.newHttpClient(), Duration.ofMillis(300)) { host ->
            if (host.startsWith("stuck")) { resolved.incrementAndGet(); release.await(5, TimeUnit.SECONDS); arrayOf() }
            else java.net.InetAddress.getAllByName(host)
        }
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn((1..6).map { c("S$it", "http://stuck$it.internal") })
        val r = stuckDns.reloadAll()
        assertThat(r).extracting(ok).containsOnly(false)
        release.countDown()
        Thread.sleep(300)   // 대기 작업이 차례를 받아 빠져나갈 시간
        assertThat(resolved.get()).isLessThanOrEqualTo(4)
    }

    @Test fun unresolvableHostIsNotBlockedHereAndFailsAtSendAsBefore() {
        whenever(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(listOf(c("A", "http://no-such-host.invalid:1")))
        val r = client.reloadAll()[0]
        assertThat(r.ok).isFalse()
        assertThat(r.detail).doesNotStartWith("차단된 주소")
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
        // 결과만 먼저 돌려주고 연결은 남겨 두면 안 된다 — 실제 전송도 취소돼 서버 쪽 쓰기가 끊겨야 한다.
        assertThat(trickleAborted.await(2, TimeUnit.SECONDS)).isTrue()
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
