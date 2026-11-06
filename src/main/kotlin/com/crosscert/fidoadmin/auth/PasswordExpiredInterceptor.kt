package com.crosscert.fidoadmin.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 비밀번호가 만료된 세션은 변경 화면 밖으로 나가지 못한다. 표시는 [LoginSuccessHandler] 가 두고
 * 변경 성공 시 `PasswordChangeController` 가 지운다. 상태가 없어 WebMvcConfig 에서 new 로 만든다.
 * 로그아웃(POST /logout)은 Spring Security 필터가 먼저 처리하므로 여기까지 오지 않는다.
 */
class PasswordExpiredInterceptor : HandlerInterceptor {

    @Throws(Exception::class)
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val session = request.getSession(false)
        if (session == null || session.getAttribute(SESSION_ATTR) == null) return true
        val path = request.requestURI.substring(request.contextPath.length)
        for (allowed in ALLOWED_PREFIXES) {
            if (path.startsWith(allowed)) return true
        }
        response.sendRedirect(request.contextPath + "/me/password")
        return false
    }

    companion object {
        const val SESSION_ATTR: String = "PASSWORD_EXPIRED"

        private val ALLOWED_PREFIXES: List<String> = listOf(
            "/me/password", "/logout", "/error", "/webjars/", "/css/", "/js/", "/fonts/", "/favicon")
    }
}
