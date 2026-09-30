package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class LoginSuccessHandlerExpiryTest {

    LoginAttemptService attempts = mock(LoginAttemptService.class);
    PasswordExpiryPolicy expiry = mock(PasswordExpiryPolicy.class);
    LoginSuccessHandler handler = new LoginSuccessHandler(attempts, mock(AuditLogger.class), expiry);
    ManagerUserDetails user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void expiredGoesToPasswordPageAndMarksSession() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin")).thenReturn(true);
        when(expiry.expiryDays()).thenReturn(90);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/me/password");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isEqualTo(90);
    }

    @Test void notExpiredGoesHomeWithoutMark() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }

    @Test void staleFlagIsClearedOnNonExpiredLogin() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        req.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }

    @Test void dbFailureDuringExpiryCheckDoesNotBreakLogin() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin"))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }
}
