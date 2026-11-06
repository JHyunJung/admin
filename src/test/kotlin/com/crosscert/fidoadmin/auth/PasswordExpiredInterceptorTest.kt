package com.crosscert.fidoadmin.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class PasswordExpiredInterceptorTest {

    val interceptor = PasswordExpiredInterceptor()

    private fun req(method: String, uri: String, expired: Boolean): MockHttpServletRequest {
        val r = MockHttpServletRequest(method, uri)
        r.requestURI = uri
        if (expired) r.getSession()!!.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90)
        return r
    }

    @Test fun passesWhenNotExpired() {
        val res = MockHttpServletResponse()
        assertThat(interceptor.preHandle(req("GET", "/", false), res, Any())).isTrue()
    }

    @Test fun passesWithoutSession() {
        val r = MockHttpServletRequest("GET", "/")
        assertThat(interceptor.preHandle(r, MockHttpServletResponse(), Any())).isTrue()
    }

    @Test fun redirectsOtherPathsWhenExpired() {
        for (uri in arrayOf("/", "/appids", "/select-tenant", "/system/fido-clients/reload")) {
            val res = MockHttpServletResponse()
            assertThat(interceptor.preHandle(req("GET", uri, true), res, Any())).`as`(uri).isFalse()
            assertThat(res.redirectedUrl).`as`(uri).isEqualTo("/me/password")
        }
    }

    @Test fun allowsPasswordPageStaticAndErrorWhenExpired() {
        for (uri in arrayOf("/me/password", "/logout", "/webjars/bootstrap/css/bootstrap.min.css",
            "/css/app.css", "/js/app.js", "/fonts/x.woff2", "/favicon.ico", "/error", "/error/500")) {
            assertThat(interceptor.preHandle(req("GET", uri, true), MockHttpServletResponse(), Any()))
                .`as`(uri).isTrue()
        }
    }

    @Test fun allowsPasswordPost() {
        assertThat(interceptor.preHandle(req("POST", "/me/password", true), MockHttpServletResponse(), Any())).isTrue()
    }

    @Test fun respectsContextPath() {
        val r = req("GET", "/admin/appids", true)
        r.contextPath = "/admin"
        val res = MockHttpServletResponse()
        assertThat(interceptor.preHandle(r, res, Any())).isFalse()
        assertThat(res.redirectedUrl).isEqualTo("/admin/me/password")
    }
}
