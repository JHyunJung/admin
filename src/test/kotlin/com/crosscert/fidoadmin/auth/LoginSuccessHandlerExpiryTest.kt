package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class LoginSuccessHandlerExpiryTest {

    val attempts: LoginAttemptService = mock(LoginAttemptService::class.java)
    val expiry: PasswordExpiryPolicy = mock(PasswordExpiryPolicy::class.java)
    val handler = LoginSuccessHandler(attempts, mock(AuditLogger::class.java), expiry)
    val user = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    @Test fun expiredGoesToPasswordPageAndMarksSession() {
        whenever(expiry.isExpiredOnLogin("kbadmin")).thenReturn(true)
        whenever(expiry.expiryDays()).thenReturn(90)
        val req = MockHttpServletRequest("POST", "/login")
        val res = MockHttpServletResponse()
        handler.onAuthenticationSuccess(req, res, UsernamePasswordAuthenticationToken(user, null, user.authorities))
        assertThat(res.redirectedUrl).isEqualTo("/me/password")
        assertThat(req.getSession()!!.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isEqualTo(90)
    }

    @Test fun notExpiredGoesHomeWithoutMark() {
        whenever(expiry.isExpiredOnLogin("kbadmin")).thenReturn(false)
        val req = MockHttpServletRequest("POST", "/login")
        val res = MockHttpServletResponse()
        handler.onAuthenticationSuccess(req, res, UsernamePasswordAuthenticationToken(user, null, user.authorities))
        assertThat(res.redirectedUrl).isEqualTo("/")
        assertThat(req.getSession()!!.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull()
    }

    @Test fun staleFlagIsClearedOnNonExpiredLogin() {
        whenever(expiry.isExpiredOnLogin("kbadmin")).thenReturn(false)
        val req = MockHttpServletRequest("POST", "/login")
        req.getSession()!!.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90)
        val res = MockHttpServletResponse()
        handler.onAuthenticationSuccess(req, res, UsernamePasswordAuthenticationToken(user, null, user.authorities))
        assertThat(res.redirectedUrl).isEqualTo("/")
        assertThat(req.getSession()!!.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull()
    }

    @Test fun dbFailureReadingExpiryDaysDoesNotBreakLogin() {
        whenever(expiry.isExpiredOnLogin("kbadmin")).thenReturn(true)
        whenever(expiry.expiryDays()).thenThrow(DataAccessResourceFailureException("down"))
        val req = MockHttpServletRequest("POST", "/login")
        val res = MockHttpServletResponse()
        handler.onAuthenticationSuccess(req, res, UsernamePasswordAuthenticationToken(user, null, user.authorities))
        assertThat(res.redirectedUrl).isEqualTo("/")
        assertThat(req.getSession()!!.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull()
    }

    @Test fun dbFailureDuringExpiryCheckDoesNotBreakLogin() {
        whenever(expiry.isExpiredOnLogin("kbadmin"))
            .thenThrow(DataAccessResourceFailureException("down"))
        val req = MockHttpServletRequest("POST", "/login")
        val res = MockHttpServletResponse()
        handler.onAuthenticationSuccess(req, res, UsernamePasswordAuthenticationToken(user, null, user.authorities))
        assertThat(res.redirectedUrl).isEqualTo("/")
        assertThat(req.getSession()!!.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull()
    }
}
