package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.stereotype.Component

@Component
class AppLogoutSuccessHandler(
    private val attempts: LoginAttemptService,
    private val audit: AuditLogger,
) : SimpleUrlLogoutSuccessHandler() {

    @Throws(IOException::class, ServletException::class)
    override fun onLogoutSuccess(request: HttpServletRequest, response: HttpServletResponse,
                                 authentication: Authentication?) {
        val user = authentication?.principal
        if (user is ManagerUserDetails) {
            attempts.onLogout(user.userId)
            log.info("로그아웃: userId={}", user.userId)
            audit.log(user, AuditType.LOGOUT, "로그아웃", request.remoteAddr, request.getHeader("User-Agent"))
        }
        setDefaultTargetUrl("/login?logout")
        super.onLogoutSuccess(request, response, authentication)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AppLogoutSuccessHandler::class.java)
    }
}
