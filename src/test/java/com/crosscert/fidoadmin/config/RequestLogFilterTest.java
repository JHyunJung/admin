package com.crosscert.fidoadmin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    /** 요청 동안 MDC 에 req/user/tenant 가 있고, 응답 헤더로 같은 ID 가 나가며, 끝나면 MDC 는 비어 있다. */
    @Test void fillsMdcDuringRequestAndClearsAfter() throws Exception {
        ManagerUserDetails user = new ManagerUserDetails(1L, "kbadmin", "pw", "KB운영자", 1L, "KB국민은행", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        AtomicReference<String> seenReq = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenReq.set(MDC.get(RequestLogFilter.MDC_REQ));
            seenUser.set(MDC.get(RequestLogFilter.MDC_USER));
            seenTenant.set(MDC.get(RequestLogFilter.MDC_TENANT));
        };
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/appids"), response, chain);

        assertThat(seenReq.get()).hasSize(8);
        assertThat(seenUser.get()).isEqualTo("kbadmin");
        assertThat(seenTenant.get()).isEqualTo("1");
        assertThat(response.getHeader(RequestLogFilter.HEADER)).isEqualTo(seenReq.get());
        assertThat(MDC.get(RequestLogFilter.MDC_REQ)).isNull();
        assertThat(MDC.get(RequestLogFilter.MDC_USER)).isNull();
        assertThat(MDC.get(RequestLogFilter.MDC_TENANT)).isNull();
    }

    /** 시큐리티 체인 앞에서 돌므로 로그인 사용자는 세션에 저장된 컨텍스트에서 읽는다. */
    @Test void readsUserFromSessionSecurityContext() throws Exception {
        ManagerUserDetails user = new ManagerUserDetails(1L, "superuser", "pw", "관리자", 0L, "전역", true, true);
        var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/companies");
        request.getSession(true).setAttribute(
            org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenUser.set(MDC.get(RequestLogFilter.MDC_USER));
            seenTenant.set(MDC.get(RequestLogFilter.MDC_TENANT));
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenUser.get()).isEqualTo("superuser");
        assertThat(seenTenant.get()).isEqualTo("0");
    }

    /** 체인이 예외를 던져도 MDC 는 비운다 — 풀 스레드가 남의 ID 를 달고 다음 요청을 처리하면 안 된다. */
    @Test void clearsMdcEvenWhenChainThrows() {
        FilterChain chain = (req, res) -> { throw new IllegalStateException("boom"); };
        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/appids"), new MockHttpServletResponse(), chain);
        } catch (Exception expected) {
            // 예외는 그대로 위로 간다
        }
        assertThat(MDC.get(RequestLogFilter.MDC_REQ)).isNull();
    }

    /** 익명 요청(로그인 화면 등)은 req 만 있고 user/tenant 는 없다. */
    @Test void anonymousRequestHasOnlyRequestId() throws Exception {
        AtomicReference<String> seenUser = new AtomicReference<>("x");
        FilterChain chain = (req, res) -> seenUser.set(MDC.get(RequestLogFilter.MDC_USER));

        filter.doFilter(new MockHttpServletRequest("GET", "/login"), new MockHttpServletResponse(), chain);

        assertThat(seenUser.get()).isNull();
    }

    /** 정적 자원은 건너뛴다 — 화면 하나에 수십 건씩 찍히면 요청 로그가 묻힌다. */
    @Test void skipsStaticResources() throws Exception {
        for (String path : new String[] {"/css/admin.css", "/js/admin.js", "/webjars/bootstrap/x.js", "/favicon.ico", "/error"}) {
            assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", path))).as(path).isTrue();
        }
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/appids"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/logs/fido"))).isFalse();
    }

    /** 응답 헤더는 체인 실행 전에 실린다(뷰가 응답을 커밋한 뒤에는 헤더를 못 넣는다). */
    @Test void headerIsSetBeforeChainRuns() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> headerDuring = new AtomicReference<>();
        FilterChain chain = (req, res) -> headerDuring.set(((MockHttpServletResponse) res).getHeader(RequestLogFilter.HEADER));

        filter.doFilter(new MockHttpServletRequest("GET", "/appids"), response, chain);

        assertThat(headerDuring.get()).isNotNull().hasSize(8);
    }
}
