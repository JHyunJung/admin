package com.crosscert.fidoadmin.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import com.crosscert.fidoadmin.common.javaTrim
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

/**
 * 인증 없이 열린 서버 간 경로(/api/svc/\*\* FIDO 서버 자가등록, /external/\*\* 라이선스 수신)의
 * 접근 IP 허용 목록.
 *
 * 두 경로는 호출자가 FIDO 서버뿐인데 로그인도 토큰도 없다(FIDO 서버 코드를 고치지 않는다는 전제).
 * 누구나 서버를 등록·OFF 하거나 라이선스 해시를 시도할 수 있으므로, 배치 환경에서 FIDO 서버의 대역을
 * `fido-admin.external-allowed-cidrs`(쉼표 구분 CIDR 또는 단일 IP)로 적어 그 밖은 403 으로 막는다.
 *
 * **비어 있으면(기본값) 모두 통과한다** — 기존 배포의 동작을 바꾸지 않는다. 프록시 뒤라면 remoteAddr 이
 * 프록시 IP 이므로 `server.forward-headers-strategy` 와 함께 설정해야 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // RequestLogFilter 뒤 — 거부 로그에도 요청 ID 가 붙는다
class ExternalAccessFilter(@Value("\${" + PROPERTY + ":}") cidrs: String?) : OncePerRequestFilter() {

    private val allowed: List<IpAddressMatcher> = parse(cidrs)

    init {
        if (allowed.isNotEmpty()) {
            log.info("서버 간 경로(/api/svc, /external) 접근 허용 대역 {}개: {}", allowed.size, cidrs!!.javaTrim())
        }
    }

    /**
     * 컨트롤러·Spring Security 는 **디코딩·정규화한** 경로로 매핑을 맞춘다. 원문 URI 로만 판정하면
     * `/%61pi/svc/..` 처럼 접두사를 인코딩한 요청이 이 필터를 건너뛰고 컨트롤러에 닿는다.
     * 그래서 원문과 정규화 경로(디코딩, `;` 파라미터 제거, `//` 축약) 중 하나라도 서버 간 경로면 거른다 —
     * 원문 판정에 걸리던 요청은 그대로 걸리므로 거르는 범위는 넓어지기만 한다.
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        val ctx = request.contextPath ?: ""
        val raw = if (uri.startsWith(ctx)) uri.substring(ctx.length) else uri
        return !(isServerPath(raw) || isServerPath(normalizedPath(request)))
    }

    private fun isServerPath(path: String): Boolean = path.startsWith("/api/svc/") || path.startsWith("/external/")

    /** 잘못된 인코딩 등으로 정규화가 실패하면 원문 판정만 남는다(그 요청은 뒤의 방화벽·디스패처가 거절한다). */
    private fun normalizedPath(request: HttpServletRequest): String =
        try {
            UrlPathHelper.defaultInstance.getPathWithinApplication(request)
        } catch (e: IllegalArgumentException) {
            ""
        }

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (allowed.isEmpty() || isAllowed(request.remoteAddr)) {
            chain.doFilter(request, response)
            return
        }
        log.warn("허용 대역 밖에서 서버 간 경로를 호출했다: ip={} {} {}", request.remoteAddr, request.method, request.requestURI)
        response.sendError(HttpServletResponse.SC_FORBIDDEN)
    }

    private fun isAllowed(remoteAddr: String?): Boolean {
        if (remoteAddr == null) return false
        for (m in allowed) {
            if (m.matches(remoteAddr)) return true
        }
        return false
    }

    companion object {
        internal const val PROPERTY = "fido-admin.external-allowed-cidrs"

        private val log = LoggerFactory.getLogger(ExternalAccessFilter::class.java)

        private fun parse(cidrs: String?): List<IpAddressMatcher> {
            val list = ArrayList<IpAddressMatcher>()
            if (cidrs == null) return list
            for (part in cidrs.split(",")) {
                val s = part.javaTrim()
                if (s.isNotEmpty()) list.add(IpAddressMatcher(s))
            }
            return list
        }
    }
}
