package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [Fido2CredentialParamsController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class Fido2CredentialParamsControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: Fido2CredentialParamsService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/fido2/credential-params").with(user(companyUser))).andExpect(status().isForbidden())
    }

    @Test fun superListRendersRows() {
        val p = Fido2CredentialParams()
        p.idx = 1L; p.credType = "public-key"; p.credAlg = -7L; p.status = "T"
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(p)))
        mvc.perform(get("/fido2/credential-params").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/list"))
            .andExpect(content().string(containsString("public-key")))
            .andExpect(content().string(containsString("-7")))
    }

    @Test fun missingCredAlgShowsFormAgain() {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "").param("status", "T"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/form"))
    }

    @Test fun createRedirectsToDetail() {
        val saved = Fido2CredentialParams(); saved.idx = 4L
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("4")
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "-8").param("status", "T"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/credential-params/4"))
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).param("credAlg", "-7"))
            .andExpect(status().isForbidden())
    }
}
