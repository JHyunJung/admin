package com.crosscert.fidoadmin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인증 없이 열린 서버 간 경로({@code /api/svc/**} FIDO 서버 자가등록, {@code /external/**} 라이선스 수신)의
 * 접근 IP 허용 목록.
 *
 * <p>두 경로는 호출자가 FIDO 서버뿐인데 로그인도 토큰도 없다(FIDO 서버 코드를 고치지 않는다는 전제).
 * 누구나 서버를 등록·OFF 하거나 라이선스 해시를 시도할 수 있으므로, 배치 환경에서 FIDO 서버의 대역을
 * {@code fido-admin.external-allowed-cidrs}(쉼표 구분 CIDR 또는 단일 IP)로 적어 그 밖은 403 으로 막는다.
 *
 * <p><b>비어 있으면(기본값) 모두 통과한다</b> — 기존 배포의 동작을 바꾸지 않는다. 프록시 뒤라면 remoteAddr 이
 * 프록시 IP 이므로 {@code server.forward-headers-strategy} 와 함께 설정해야 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // RequestLogFilter 뒤 — 거부 로그에도 요청 ID 가 붙는다
public class ExternalAccessFilter extends OncePerRequestFilter {

    static final String PROPERTY = "fido-admin.external-allowed-cidrs";

    private final List<IpAddressMatcher> allowed;

    public ExternalAccessFilter(@Value("${" + PROPERTY + ":}") String cidrs) {
        this.allowed = parse(cidrs);
        if (!allowed.isEmpty()) {
            log.info("서버 간 경로(/api/svc, /external) 접근 허용 대역 {}개: {}", allowed.size(), cidrs.trim());
        }
    }

    private static List<IpAddressMatcher> parse(String cidrs) {
        List<IpAddressMatcher> list = new ArrayList<>();
        if (cidrs == null) return list;
        for (String part : cidrs.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(new IpAddressMatcher(s));
        }
        return list;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return !(path.startsWith("/api/svc/") || path.startsWith("/external/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (allowed.isEmpty() || isAllowed(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("허용 대역 밖에서 서버 간 경로를 호출했다: ip={} {} {}", request.getRemoteAddr(), request.getMethod(), request.getRequestURI());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private boolean isAllowed(String remoteAddr) {
        if (remoteAddr == null) return false;
        for (IpAddressMatcher m : allowed) {
            if (m.matches(remoteAddr)) return true;
        }
        return false;
    }
}
