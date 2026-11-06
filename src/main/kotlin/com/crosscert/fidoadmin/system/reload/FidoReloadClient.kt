package com.crosscert.fidoadmin.system.reload

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 등록된 FIDO 서버(STATUS='ON')마다 `GET {SERVERURL}/api/command/reload` 를 보낸다.
 *
 * 서버 목록은 인증 없는 자가등록(/api/svc/reg)으로 채워지므로 http/https 만 보내고 리다이렉트는 따라가지 않는다.
 * 한 서버의 실패가 다음 서버를 막지 않는다. 어떤 경우에도 예외를 던지지 않고 결과로 돌려준다.
 */
@Component
class FidoReloadClient internal constructor(
    private val clients: CcfaFidoclientRepository,
    private val http: HttpClient,
    private val responseTimeout: Duration,
) {

    @Autowired
    constructor(clients: CcfaFidoclientRepository) : this(
        clients,
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build(),
        RESPONSE_TIMEOUT,
    )

    /**
     * 모든 서버에 요청을 먼저 띄운 뒤 서버 코드 순으로 결과를 모은다. 하나씩 보내고 기다리면
     * 수동 reload 가 서버 수 × 응답 타임아웃까지 걸린다. 결과 순서는 서버 코드 순 그대로다.
     */
    fun reloadAll(): List<ReloadResult> {
        val pending = ArrayList<Pending>()
        for (c in clients.findByStatusOrderByServercodeAsc(STATUS_ON)) {
            pending.add(start(c))
        }
        val results = ArrayList<ReloadResult>(pending.size)
        for (p in pending) {
            results.add(p.await())
        }
        return results
    }

    /** 띄워 둔 요청 하나. URL 이 틀려 보내지 못한 경우는 `failed` 에 결과가 미리 들어 있다. */
    private inner class Pending(private val client: CcfaFidoclient) {
        private val raw: String? = client.serverurl
        val start: Long = System.nanoTime()
        var future: CompletableFuture<HttpResponse<Void>>? = null
        var failed: ReloadResult? = null

        fun await(): ReloadResult {
            failed?.let { return it }
            try {
                // HttpRequest.timeout 은 헤더까지만 본다. 본문을 천천히 흘려 스레드를 붙잡지 못하게 전체를 자른다.
                // 요청들은 함께 띄웠으므로 각자 자기 시작 시각 기준으로 남은 시간만 기다린다.
                val remaining = maxOf(0L, responseTimeout.toMillis() - millisSince(start))
                val code = future!!.get(remaining, TimeUnit.MILLISECONDS).statusCode()
                return result(code >= 200 && code < 300, "HTTP $code")
            } catch (e: TimeoutException) {
                future?.cancel(true)
                return result(false, "request timed out")
            } catch (e: InterruptedException) {
                future?.cancel(true)
                Thread.currentThread().interrupt()
                return result(false, "중단됨")
            } catch (e: Exception) {
                val t: Throwable = if (e is ExecutionException && e.cause != null) e.cause!! else e
                val msg = if (t.message == null) t.javaClass.simpleName else t.javaClass.simpleName + ": " + t.message
                return result(false, oneLine(msg))
            }
        }

        private fun result(ok: Boolean, detail: String): ReloadResult =
            ReloadResult(client.servercode, raw, ok, detail, millisSince(start))
    }

    private fun start(c: CcfaFidoclient): Pending {
        val p = Pending(c)
        val raw = c.serverurl
        val base = raw?.javaTrim()?.replace(TRAILING_SLASHES, "") ?: ""
        val uri: URI? = try {
            if (base.isEmpty()) null else URI.create(base)
        } catch (e: IllegalArgumentException) {
            null
        }
        val scheme = uri?.scheme
        if (uri == null || scheme == null || !(scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) ||
            uri.host == null || uri.rawQuery != null ||
            uri.rawFragment != null || uri.rawUserInfo != null
        ) {
            p.failed = ReloadResult(c.servercode, raw, false, oneLine("지원하지 않는 URL: " + (raw?.javaTrim() ?: "")), 0)
            return p
        }
        try {
            val req = HttpRequest.newBuilder(URI.create("$base/api/command/reload"))
                .timeout(responseTimeout).GET().build()
            p.future = http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
        } catch (e: RuntimeException) {
            // 요청을 만들거나 띄우는 단계의 오류. 기존처럼 결과로만 돌려주고 다음 서버를 막지 않는다.
            val msg = if (e.message == null) e.javaClass.simpleName else e.javaClass.simpleName + ": " + e.message
            p.failed = ReloadResult(c.servercode, raw, false, oneLine(msg), millisSince(p.start))
        }
        return p
    }

    companion object {
        internal const val STATUS_ON = "ON"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        private val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val TRAILING_SLASHES = Regex("/+$")
        private val LINE_BREAKS = Regex("[\\r\\n]+")

        private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

        @JvmStatic
        internal fun oneLine(s: String?): String {
            if (s == null) return ""
            val flat = s.replace(LINE_BREAKS, " ")
            return if (flat.length > 200) flat.substring(0, 200) else flat
        }
    }
}
