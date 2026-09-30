package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PasswordExpiredInterceptorTest {

    PasswordExpiredInterceptor interceptor = new PasswordExpiredInterceptor();

    private MockHttpServletRequest req(String method, String uri, boolean expired) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        if (expired) r.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        return r;
    }

    @Test void passesWhenNotExpired() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req("GET", "/", false), res, new Object())).isTrue();
    }

    @Test void passesWithoutSession() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/");
        assertThat(interceptor.preHandle(r, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test void redirectsOtherPathsWhenExpired() throws Exception {
        for (String uri : new String[] {"/", "/appids", "/select-tenant", "/system/fido-clients/reload"}) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(req("GET", uri, true), res, new Object())).as(uri).isFalse();
            assertThat(res.getRedirectedUrl()).as(uri).isEqualTo("/me/password");
        }
    }

    @Test void allowsPasswordPageStaticAndErrorWhenExpired() throws Exception {
        for (String uri : new String[] {"/me/password", "/logout", "/webjars/bootstrap/css/bootstrap.min.css",
            "/css/app.css", "/js/app.js", "/fonts/x.woff2", "/favicon.ico", "/error", "/error/500"}) {
            assertThat(interceptor.preHandle(req("GET", uri, true), new MockHttpServletResponse(), new Object()))
                .as(uri).isTrue();
        }
    }

    @Test void allowsPasswordPost() throws Exception {
        assertThat(interceptor.preHandle(req("POST", "/me/password", true), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test void respectsContextPath() throws Exception {
        MockHttpServletRequest r = req("GET", "/admin/appids", true);
        r.setContextPath("/admin");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(r, res, new Object())).isFalse();
        assertThat(res.getRedirectedUrl()).isEqualTo("/admin/me/password");
    }
}
