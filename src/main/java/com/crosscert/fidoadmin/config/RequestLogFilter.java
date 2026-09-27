package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 추적 ID 를 만들어 MDC 에 넣고, 끝나면 한 줄 남긴다.
 *
 * <p>MDC 의 {@code req}/{@code user}/{@code tenant} 는 logback-spring.xml 의 패턴이 모든 줄에
 * 붙인다 — 한 요청이 남긴 로그를 파일에서 {@code req=} 로 묶어 볼 수 있다. 같은 ID 를
 * {@code X-Request-Id} 응답 헤더로도 돌려주어 운영자가 화면 오류를 신고할 때 붙일 수 있다.
 *
 * <p>정상 요청은 DEBUG(local/qa 에서만 보인다), 4xx/5xx 또는 {@link #SLOW_MS} 를 넘긴 요청은
 * WARN 이다 — 운영 파일에는 문제 있는 요청만 남는다. 정적 자원은 건너뛴다.
 *
 * <p>{@code @Component} 로 등록하므로 Spring Boot 가 서블릿 필터로 자동 등록한다.
 * SecurityConfig 에 끼우지 않는 이유: @WebMvcTest 슬라이스가 SecurityConfig 를 import 하는데,
 * 거기 의존이 늘면 슬라이스마다 이 필터의 의존까지 준비해야 한다.
 *
 * <p>시큐리티 필터체인보다 <b>앞</b>에서 돈다. 뒤에 두면 로그인 POST 처럼 시큐리티 체인 안에서
 * 끝나는 요청은 여기까지 오지 않아 "로그인 성공" 줄에 요청 ID 가 없다. 대신 이 시점에는
 * SecurityContextHolder 가 아직 비어 있으므로, 로그인 사용자는 세션에 저장된 컨텍스트
 * ({@code SPRING_SECURITY_CONTEXT})에서 직접 읽는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String MDC_REQ = "req";
    public static final String MDC_USER = "user";
    public static final String MDC_TENANT = "tenant";
    public static final String HEADER = "X-Request-Id";
    static final long SLOW_MS = 1000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/")
            || path.startsWith("/webjars/") || path.startsWith("/favicon") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String id = newId();
        long started = System.nanoTime();
        MDC.put(MDC_REQ, id);
        putPrincipal(request);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            int status = response.getStatus();
            String line = "{} {} -> {} ({} ms)";
            if (status >= 400 || ms >= SLOW_MS) {
                log.warn(line, request.getMethod(), request.getRequestURI(), status, ms);
            } else {
                log.debug(line, request.getMethod(), request.getRequestURI(), status, ms);
            }
            // 스레드는 풀에서 재사용된다. 안 지우면 다음 요청이 남의 ID 를 달고 나간다.
            MDC.remove(MDC_REQ);
            MDC.remove(MDC_USER);
            MDC.remove(MDC_TENANT);
        }
    }

    /** 로그인 사용자와 그 소속(SUPER 는 0). 선택한 테넌트는 세션에 있어 여기서 읽지 않는다 — 소속만 적는다. */
    private static void putPrincipal(HttpServletRequest request) {
        Authentication auth = authenticationOf(request);
        if (auth != null && auth.getPrincipal() instanceof ManagerUserDetails user) {
            MDC.put(MDC_USER, user.getUserId());
            if (user.getCompanyIdx() != null) MDC.put(MDC_TENANT, String.valueOf(user.getCompanyIdx()));
        }
    }

    /**
     * 시큐리티 체인 앞이라 SecurityContextHolder 는 비어 있다. 세션에 저장된 컨텍스트를 먼저 보고,
     * (테스트처럼) 홀더에 직접 넣은 경우도 받는다. 세션은 새로 만들지 않는다.
     */
    private static Authentication authenticationOf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null
            && session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
               instanceof SecurityContext ctx && ctx.getAuthentication() != null) {
            return ctx.getAuthentication();
        }
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** 8자리면 하루치 로그 안에서 겹칠 일이 없고 눈으로 옮겨 적기에도 짧다. */
    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
