package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class LoginSuccessHandler(
    private val attempts: LoginAttemptService,
    private val audit: AuditLogger,
    private val expiry: PasswordExpiryPolicy,
) : SavedRequestAwareAuthenticationSuccessHandler() {

    @Throws(ServletException::class, IOException::class)
    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse,
                                         authentication: Authentication) {
        val user = authentication.principal as ManagerUserDetails
        attempts.onSuccess(user.userId)
        log.info("로그인 성공: userId={} company={} ip={}", user.userId, user.companyIdx, request.remoteAddr)
        audit.log(user, AuditType.LOGIN, "로그인 성공", request.remoteAddr, request.getHeader("User-Agent"))
        var expired: Boolean
        var expiryDays: Int? = null
        try {
            expired = expiry.isExpiredOnLogin(user.userId)
            if (expired) expiryDays = expiry.expiryDays()
        } catch (e: DataAccessException) {
            log.warn("비밀번호 만료 판정 실패 — 이번 로그인은 만료가 아닌 것으로 봅니다: userId={}", user.userId, e)
            expired = false
        }
        if (expired) {
            request.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, expiryDays)
            log.info("비밀번호 만료: userId={} → 변경 화면", user.userId)
            clearAuthenticationAttributes(request)
            redirectStrategy.sendRedirect(request, response, "/me/password")
            return
        }
        // 세션 고정 방지로 속성이 옮겨 오므로, 만료가 아닌 로그인은 남은 표시를 지운다.
        val old = request.getSession(false)
        old?.removeAttribute(PasswordExpiredInterceptor.SESSION_ATTR)
        setDefaultTargetUrl("/")
        super.onAuthenticationSuccess(request, response, authentication)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LoginSuccessHandler::class.java)
    }
}
