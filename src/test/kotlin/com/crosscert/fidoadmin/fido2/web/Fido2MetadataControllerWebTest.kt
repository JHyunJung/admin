package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
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

@WebMvcTest(controllers = [Fido2MetadataController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class Fido2MetadataControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: Fido2MetadataService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun sample(): Fido2Metadata {
        val m = Fido2Metadata()
        m.idx = 1L; m.description = "YubiKey 5 Series"; m.aaguid = "cb69481e-8ff7-4039-93ec-0a2729a154a8"
        m.protocolfamily = "fido2"; m.icon = "ICON-MARKER-SHOULD-NOT-RENDER"
        m.attestationrootcertificates = "ROOTCERT-MARKER-SHOULD-NOT-RENDER"
        return m
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/fido2/metadata").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** 목록은 CLOB(ICON, ATTESTATIONROOTCERTIFICATES)을 싣지 않는다. */
    @Test fun superListRendersRowsWithoutClob() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(sample())))
        mvc.perform(get("/fido2/metadata").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/list"))
            .andExpect(content().string(containsString("YubiKey 5 Series")))
            .andExpect(content().string(not(containsString("ICON-MARKER-SHOULD-NOT-RENDER"))))
            .andExpect(content().string(not(containsString("ROOTCERT-MARKER-SHOULD-NOT-RENDER"))))
    }

    @Test fun detailShowsClob() {
        whenever(service.get(1L)).thenReturn(sample())
        mvc.perform(get("/fido2/metadata/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/detail"))
            .andExpect(content().string(containsString("ICON-MARKER-SHOULD-NOT-RENDER")))
    }

    @Test fun blankAaguidShowsFormAgain() {
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "설명").param("aaguid", "").param("issecondfactoronly", "false"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/form"))
    }

    @Test fun createRedirectsToDetail() {
        val saved = Fido2Metadata(); saved.idx = 3L
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("3")
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "Samsung Pass").param("aaguid", "53414d53-554e-4700-0000-000000000000")
                .param("issecondfactoronly", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/metadata/3"))
    }
}
