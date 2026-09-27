package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService attempts;
    private final AuditLogger audit;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        ManagerUserDetails user = (ManagerUserDetails) authentication.getPrincipal();
        attempts.onSuccess(user.getUserId());
        log.info("로그인 성공: userId={} company={} ip={}", user.getUserId(), user.getCompanyIdx(), request.getRemoteAddr());
        audit.log(user, AuditType.LOGIN, "로그인 성공", request.getRemoteAddr(), request.getHeader("User-Agent"));
        setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
