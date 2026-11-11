package com.crosscert.fidoadmin.config

import jakarta.servlet.FilterChain
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * /api/svc/\*\*(FIDO 서버 자가등록)과 /external/\*\*(라이선스 수신)는 인증 없이 열려 있다.
 * 호출자는 FIDO 서버뿐이므로 접근 IP 허용 목록(CIDR)을 둘 수 있게 한다.
 * 목록이 비어 있으면(기본값) 지금처럼 모두 통과한다 — 기존 배포의 동작은 그대로다.
 */
class ExternalAccessFilterTest {

    private fun passes(cidrs: String?, path: String, remoteAddr: String): Boolean {
        val filter = ExternalAccessFilter(cidrs)
        val request = MockHttpServletRequest("POST", path)
        request.remoteAddr = remoteAddr
        val response = MockHttpServletResponse()
        val reached = AtomicBoolean(false)
        val chain = FilterChain { _, _ -> reached.set(true) }
        filter.doFilter(request, response, chain)
        if (!reached.get()) assertThat(response.status).isEqualTo(403)
        return reached.get()
    }

    @Test fun emptyAllowListLetsEveryoneThroughAsBefore() {
        assertThat(passes("", "/api/svc/reg/fido-1", "203.0.113.9")).isTrue()
        assertThat(passes(null, "/external/license/abc", "203.0.113.9")).isTrue()
    }

    @Test fun listedAddressPasses() {
        assertThat(passes("10.0.0.0/8, 192.168.1.5", "/api/svc/reg/fido-1", "10.20.30.40")).isTrue()
        assertThat(passes("10.0.0.0/8, 192.168.1.5", "/external/license/abc", "192.168.1.5")).isTrue()
        assertThat(passes("::1", "/api/svc/deReg/fido-1", "0:0:0:0:0:0:0:1")).isTrue()
    }

    @Test fun unlistedAddressIsForbidden() {
        assertThat(passes("10.0.0.0/8", "/api/svc/reg/fido-1", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/external/license/abc", "192.168.1.5")).isFalse()
    }

    @Test fun otherPathsAreNotAffected() {
        assertThat(passes("10.0.0.0/8", "/login", "203.0.113.9")).isTrue()
        assertThat(passes("10.0.0.0/8", "/users/1", "203.0.113.9")).isTrue()
        assertThat(passes("10.0.0.0/8", "/%6Cogin", "203.0.113.9")).isTrue()
    }

    /**
     * 컨트롤러 매핑은 디코딩한 경로로 맞춘다. 판정을 원문 URI 로 하면 `/%61pi/svc/..` 처럼 접두사를
     * 인코딩한 요청이 이 필터를 건너뛰고 컨트롤러에 닿는다(로컬 재현: 원문 403, 인코딩 200).
     */
    @Test fun encodedPrefixIsStillForbidden() {
        assertThat(passes("10.0.0.0/8", "/%61pi/svc/reg/fido-1", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/api/%73vc/deReg/fido-1", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/%65xternal/license/abc", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/%2Fexternal/license/abc", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/api/svc;x=1/reg/fido-1", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "//api/svc/reg/fido-1", "203.0.113.9")).isFalse()
    }

    @Test fun encodedPrefixUnderContextPathIsStillForbidden() {
        val filter = ExternalAccessFilter("10.0.0.0/8")
        val request = MockHttpServletRequest("GET", "/admin/%65xternal/license/abc")
        request.contextPath = "/admin"
        request.remoteAddr = "203.0.113.9"
        val response = MockHttpServletResponse()
        val reached = AtomicBoolean(false)
        filter.doFilter(request, response, FilterChain { _, _ -> reached.set(true) })
        assertThat(reached.get()).isFalse()
        assertThat(response.status).isEqualTo(403)
    }

    @Test fun malformedEncodingFallsBackToRawPath() {
        assertThat(passes("10.0.0.0/8", "/api/svc/reg/%zz", "203.0.113.9")).isFalse()
        assertThat(passes("10.0.0.0/8", "/users/%zz", "203.0.113.9")).isTrue()
    }

    @Test fun encodedPrefixFromListedAddressPasses() {
        assertThat(passes("10.0.0.0/8", "/%61pi/svc/reg/fido-1", "10.20.30.40")).isTrue()
    }
}
