package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private final LoginAttemptService attempts;
    private final AuditLogger audit;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        if (authentication != null && authentication.getPrincipal() instanceof ManagerUserDetails user) {
            attempts.onLogout(user.getUserId());
            audit.log(user, AuditType.LOGOUT, "로그아웃", request.getRemoteAddr(), request.getHeader("User-Agent"));
        }
        setDefaultTargetUrl("/login?logout");
        super.onLogoutSuccess(request, response, authentication);
    }
}
