package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService attempts;
    private final AuditLogger audit;
    private final PasswordExpiryPolicy expiry;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        ManagerUserDetails user = (ManagerUserDetails) authentication.getPrincipal();
        attempts.onSuccess(user.getUserId());
        log.info("로그인 성공: userId={} company={} ip={}", user.getUserId(), user.getCompanyIdx(), request.getRemoteAddr());
        audit.log(user, AuditType.LOGIN, "로그인 성공", request.getRemoteAddr(), request.getHeader("User-Agent"));
        boolean expired;
        try {
            expired = expiry.isExpiredOnLogin(user.getUserId());
        } catch (DataAccessException e) {
            log.warn("비밀번호 만료 판정 실패 — 이번 로그인은 만료가 아닌 것으로 봅니다: userId={}", user.getUserId(), e);
            expired = false;
        }
        if (expired) {
            request.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, expiry.expiryDays());
            log.info("비밀번호 만료: userId={} → 변경 화면", user.getUserId());
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, "/me/password");
            return;
        }
        // 세션 고정 방지로 속성이 옮겨 오므로, 만료가 아닌 로그인은 남은 표시를 지운다.
        HttpSession old = request.getSession(false);
        if (old != null) old.removeAttribute(PasswordExpiredInterceptor.SESSION_ATTR);
        setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
