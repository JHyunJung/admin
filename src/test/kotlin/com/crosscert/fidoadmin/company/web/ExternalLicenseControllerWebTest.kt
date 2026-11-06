package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import java.util.Optional
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** FIDO 서버가 로그인 없이 부르는 경로다. 인증·CSRF·테넌트 선택 어느 것에도 걸리면 안 된다. */
@WebMvcTest(controllers = [ExternalLicenseController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class ExternalLicenseControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var licenses: CcfaLicenseRepository
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    private fun license(body: String?): CcfaLicense { val l = CcfaLicense(); l.license = body; return l }

    @Test fun getWithoutLoginReturnsLicenseText() {
        whenever(licenses.findFirstByHashvalueOrderByIdxAsc("abc123")).thenReturn(Optional.of(license("LICENSE-BODY")))
        mvc.perform(get("/external/license/abc123"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andExpect(content().string("LICENSE-BODY"))
    }

    @Test fun postWithoutLoginOrCsrfAlsoWorks() {
        whenever(licenses.findFirstByHashvalueOrderByIdxAsc("abc123")).thenReturn(Optional.of(license("LICENSE-BODY")))
        mvc.perform(post("/external/license/abc123"))
            .andExpect(status().isOk())
            .andExpect(content().string("LICENSE-BODY"))
    }

    @Test fun unknownHashReturnsEmptyOk() {
        whenever(licenses.findFirstByHashvalueOrderByIdxAsc("nope")).thenReturn(Optional.empty())
        mvc.perform(get("/external/license/nope"))
            .andExpect(status().isOk())
            .andExpect(content().string(""))
    }

    @Test fun nullLicenseColumnReturnsEmptyOk() {
        whenever(licenses.findFirstByHashvalueOrderByIdxAsc("h")).thenReturn(Optional.of(license(null)))
        mvc.perform(get("/external/license/h")).andExpect(status().isOk()).andExpect(content().string(""))
    }

    @Test fun filenameWithDotIsCapturedWhole() {
        whenever(licenses.findFirstByHashvalueOrderByIdxAsc("abc.lic")).thenReturn(Optional.of(license("DOT")))
        mvc.perform(get("/external/license/abc.lic"))
            .andExpect(status().isOk())
            .andExpect(content().string("DOT"))
    }
}
