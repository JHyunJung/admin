package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.javaIsBlank
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class LoginFailureHandler(
    private val attempts: LoginAttemptService,
) : SimpleUrlAuthenticationFailureHandler() {

    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse,
                                         exception: AuthenticationException) {
        val userId: String? = request.getParameter("username")
        if (exception is BadCredentialsException && userId != null && !userId.javaIsBlank()) {
            attempts.onFailure(userId)
        }
        // 잠금·비활성·비밀번호 오류를 화면에서 구분하면 계정 존재 여부와 상태가 노출된다.
        // 원인은 서버 로그에만 남기고 사용자에게는 동일한 메시지를 준다.
        log.info("로그인 실패: userId={} reason={}", forLog(userId), reasonOf(exception))
        setDefaultFailureUrl("/login?error")
        super.onAuthenticationFailure(request, response, exception)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LoginFailureHandler::class.java)
        private val NEWLINES = Regex("[\\r\\n]")

        /** 사용자 입력이므로 개행을 제거해 로그 위조를 막는다. */
        fun forLog(value: String?): String? {
            if (value == null) return null
            val cleaned = value.replace(NEWLINES, "_")
            return if (cleaned.length <= 64) cleaned else cleaned.substring(0, 64)
        }

        fun reasonOf(e: AuthenticationException): String = when (e) {
            is LockedException -> "locked"
            is DisabledException -> "disabled"
            is BadCredentialsException -> "bad"
            else -> "error"
        }
    }
}
