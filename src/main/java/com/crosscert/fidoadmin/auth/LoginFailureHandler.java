package com.crosscert.fidoadmin.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService attempts;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String userId = request.getParameter("username");
        if (exception instanceof BadCredentialsException && userId != null && !userId.isBlank()) {
            attempts.onFailure(userId);
        }
        // 잠금·비활성·비밀번호 오류를 화면에서 구분하면 계정 존재 여부와 상태가 노출된다.
        // 원인은 서버 로그에만 남기고 사용자에게는 동일한 메시지를 준다.
        log.info("로그인 실패: userId={} reason={}", forLog(userId), reasonOf(exception));
        setDefaultFailureUrl("/login?error");
        super.onAuthenticationFailure(request, response, exception);
    }

    /** 사용자 입력이므로 개행을 제거해 로그 위조를 막는다. */
    private static String forLog(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\r\\n]", "_");
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }

    private static String reasonOf(AuthenticationException e) {
        if (e instanceof LockedException) return "locked";
        if (e instanceof DisabledException) return "disabled";
        if (e instanceof BadCredentialsException) return "bad";
        return "error";
    }
}
