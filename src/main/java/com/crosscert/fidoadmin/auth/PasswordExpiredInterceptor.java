package com.crosscert.fidoadmin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 비밀번호가 만료된 세션은 변경 화면 밖으로 나가지 못한다. 표시는 {@link LoginSuccessHandler} 가 두고
 * 변경 성공 시 {@code PasswordChangeController} 가 지운다. 상태가 없어 WebMvcConfig 에서 new 로 만든다.
 * 로그아웃(POST /logout)은 Spring Security 필터가 먼저 처리하므로 여기까지 오지 않는다.
 */
public class PasswordExpiredInterceptor implements HandlerInterceptor {

    public static final String SESSION_ATTR = "PASSWORD_EXPIRED";

    private static final List<String> ALLOWED_PREFIXES = List.of(
        "/me/password", "/logout", "/error", "/webjars/", "/css/", "/js/", "/fonts/", "/favicon");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_ATTR) == null) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String allowed : ALLOWED_PREFIXES) {
            if (path.startsWith(allowed)) return true;
        }
        response.sendRedirect(request.getContextPath() + "/me/password");
        return false;
    }
}
