package com.crosscert.fidoadmin.config

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import jakarta.servlet.FilterChain
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

class RequestLogFilterTest {

    private val filter = RequestLogFilter()

    @AfterEach fun clear() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    /** 요청 동안 MDC 에 req/user/tenant 가 있고, 응답 헤더로 같은 ID 가 나가며, 끝나면 MDC 는 비어 있다. */
    @Test fun fillsMdcDuringRequestAndClearsAfter() {
        val user = ManagerUserDetails(1L, "kbadmin", "pw", "KB운영자", 1L, "KB국민은행", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val seenReq = AtomicReference<String>()
        val seenUser = AtomicReference<String>()
        val seenTenant = AtomicReference<String>()
        val chain = FilterChain { _, _ ->
            seenReq.set(MDC.get(RequestLogFilter.MDC_REQ))
            seenUser.set(MDC.get(RequestLogFilter.MDC_USER))
            seenTenant.set(MDC.get(RequestLogFilter.MDC_TENANT))
        }
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest("GET", "/appids"), response, chain)

        assertThat(seenReq.get()).hasSize(8)
        assertThat(seenUser.get()).isEqualTo("kbadmin")
        assertThat(seenTenant.get()).isEqualTo("1")
        assertThat(response.getHeader(RequestLogFilter.HEADER)).isEqualTo(seenReq.get())
        assertThat(MDC.get(RequestLogFilter.MDC_REQ)).isNull()
        assertThat(MDC.get(RequestLogFilter.MDC_USER)).isNull()
        assertThat(MDC.get(RequestLogFilter.MDC_TENANT)).isNull()
    }

    /** 시큐리티 체인 앞에서 돌므로 로그인 사용자는 세션에 저장된 컨텍스트에서 읽는다. */
    @Test fun readsUserFromSessionSecurityContext() {
        val user = ManagerUserDetails(1L, "superuser", "pw", "관리자", 0L, "전역", true, true)
        val ctx = SecurityContextHolder.createEmptyContext()
        ctx.authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        val request = MockHttpServletRequest("GET", "/companies")
        request.getSession(true)!!.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx)
        val seenUser = AtomicReference<String>()
        val seenTenant = AtomicReference<String>()
        val chain = FilterChain { _, _ ->
            seenUser.set(MDC.get(RequestLogFilter.MDC_USER))
            seenTenant.set(MDC.get(RequestLogFilter.MDC_TENANT))
        }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(seenUser.get()).isEqualTo("superuser")
        assertThat(seenTenant.get()).isEqualTo("0")
    }

    /** 체인이 예외를 던져도 MDC 는 비운다 — 풀 스레드가 남의 ID 를 달고 다음 요청을 처리하면 안 된다. */
    @Test fun clearsMdcEvenWhenChainThrows() {
        val chain = FilterChain { _, _ -> throw IllegalStateException("boom") }
        try {
            filter.doFilter(MockHttpServletRequest("GET", "/appids"), MockHttpServletResponse(), chain)
        } catch (expected: Exception) {
            // 예외는 그대로 위로 간다
        }
        assertThat(MDC.get(RequestLogFilter.MDC_REQ)).isNull()
    }

    /** 익명 요청(로그인 화면 등)은 req 만 있고 user/tenant 는 없다. */
    @Test fun anonymousRequestHasOnlyRequestId() {
        val seenUser = AtomicReference<String>("x")
        val chain = FilterChain { _, _ -> seenUser.set(MDC.get(RequestLogFilter.MDC_USER)) }

        filter.doFilter(MockHttpServletRequest("GET", "/login"), MockHttpServletResponse(), chain)

        assertThat(seenUser.get()).isNull()
    }

    /** 정적 자원은 건너뛴다 — 화면 하나에 수십 건씩 찍히면 요청 로그가 묻힌다. */
    @Test fun skipsStaticResources() {
        for (path in arrayOf("/css/admin.css", "/js/admin.js", "/webjars/bootstrap/x.js", "/favicon.ico", "/error")) {
            assertThat(filter.shouldNotFilter(MockHttpServletRequest("GET", path))).`as`(path).isTrue()
        }
        assertThat(filter.shouldNotFilter(MockHttpServletRequest("GET", "/appids"))).isFalse()
        assertThat(filter.shouldNotFilter(MockHttpServletRequest("GET", "/logs/fido"))).isFalse()
    }

    /** 응답 헤더는 체인 실행 전에 실린다(뷰가 응답을 커밋한 뒤에는 헤더를 못 넣는다). */
    @Test fun headerIsSetBeforeChainRuns() {
        val response = MockHttpServletResponse()
        val headerDuring = AtomicReference<String>()
        val chain = FilterChain { _, res -> headerDuring.set((res as MockHttpServletResponse).getHeader(RequestLogFilter.HEADER)) }

        filter.doFilter(MockHttpServletRequest("GET", "/appids"), response, chain)

        assertThat(headerDuring.get()).isNotNull().hasSize(8)
    }
}
