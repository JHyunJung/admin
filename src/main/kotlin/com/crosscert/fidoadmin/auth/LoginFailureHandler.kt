package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.LogText
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
class LoginFailureHandler : SimpleUrlAuthenticationFailureHandler() {

    init {
        setDefaultFailureUrl("/login?error")
    }

    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse,
                                         exception: AuthenticationException) {
        val userId: String? = request.getParameter("username")
        // 비밀번호 오류 집계는 SerializedLoginAuthenticationProvider 가 계정 행 잠금 안에서 이미 했다.
        // 여기서 또 세면 한 번 틀린 것이 두 번으로 잡힌다.
        // 잠금·비활성·비밀번호 오류를 화면에서 구분하면 계정 존재 여부와 상태가 노출된다.
        // 원인은 서버 로그에만 남기고 사용자에게는 동일한 메시지를 준다.
        log.info("로그인 실패: userId={} reason={}", forLog(userId), reasonOf(exception))
        super.onAuthenticationFailure(request, response, exception)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LoginFailureHandler::class.java)

        /** 사용자 입력이므로 개행·제어문자를 지워 로그 위조를 막는다([LogText]). */
        fun forLog(value: String?): String? {
            val cleaned = LogText.oneLine(value) ?: return null
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
