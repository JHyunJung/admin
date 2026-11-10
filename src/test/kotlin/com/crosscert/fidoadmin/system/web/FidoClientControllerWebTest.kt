package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.reload.ReloadResult
import com.crosscert.fidoadmin.system.service.FidoClientService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [FidoClientController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class FidoClientControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: FidoClientService
    @MockitoBean lateinit var reload: com.crosscert.fidoadmin.system.reload.FidoReloadService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun client(code: String, name: String): CcfaFidoclient {
        val c = CcfaFidoclient(); c.servercode = code; c.servername = name
        c.serverurl = "https://fido1.internal:8443"; c.status = "ON"; return c
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/fido-clients").with(user(companyUser))).andExpect(status().isForbidden())
    }

    @Test fun listRendersRows() {
        whenever(service.defaultSort()).thenReturn(Sort.by("servercode"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(client("FIDO01", "FIDO 서버 1"))))
        mvc.perform(get("/system/fido-clients").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/list"))
            .andExpect(content().string(containsString("FIDO 서버 1")))
            .andExpect(content().string(containsString("/system/fido-clients/FIDO01")))
    }

    @Test fun detailRenders() {
        whenever(service.get("FIDO01")).thenReturn(client("FIDO01", "FIDO 서버 1"))
        mvc.perform(get("/system/fido-clients/FIDO01").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/detail"))
            .andExpect(content().string(containsString("https://fido1.internal:8443")))
    }

    /** SERVERNAME·SERVERURL 은 NOT NULL. 비우면 저장 전에 폼에서 잡는다. */
    @Test fun missingRequiredFieldsShowFormAgain() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "").param("serverurl", "").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"))
    }

    @Test fun createRedirectsToDetail() {
        whenever(service.create(any())).thenReturn(client("FIDO03", "x"))
        whenever(service.idOf(any())).thenReturn("FIDO03")
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "FIDO 서버 3").param("serverurl", "https://fido3.internal:8443").param("status", "ON"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fido-clients/FIDO03"))
    }

    /** servercode 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test fun invalidCodeCharacterShowsFormAgain() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "a{x}").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")))
        verify(service, never()).create(any())
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test fun reservedCodeNewIsRejected() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "new").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")))
        verify(service, never()).create(any())
    }

    @Test fun manualReloadAllOk() {
        whenever(reload.reloadNow()).thenReturn(listOf(ReloadResult("FIDO01", "https://a", true, "HTTP 200", 5),
            ReloadResult("FIDO02", "https://b", true, "HTTP 200", 5)))
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(redirectedUrl("/system/fido-clients"))
            .andExpect(flash().attribute("flashSuccess", "FIDO 서버 2대에 reload 를 보냈습니다."))
    }

    @Test fun manualReloadPartialFailureListsFailures() {
        whenever(reload.reloadNow()).thenReturn(listOf(ReloadResult("FIDO01", "https://a", true, "HTTP 200", 5),
            ReloadResult("FIDO02", "https://b", false, "HTTP 500", 5)))
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(redirectedUrl("/system/fido-clients"))
            .andExpect(flash().attribute("flashError", "FIDO 서버 2대 중 1대 reload 실패 — FIDO02: HTTP 500"))
    }

    @Test fun manualReloadWithNoOnServers() {
        whenever(reload.reloadNow()).thenReturn(listOf())
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(flash().attribute("flashError", "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다."))
    }

    @Test fun manualReloadForbiddenForCompanyUser() {
        mvc.perform(post("/system/fido-clients/reload").with(user(companyUser)).with(csrf()))
            .andExpect(status().isForbidden())
        verify(reload, never()).reloadNow()
    }

    @Test fun listShowsReloadButton() {
        whenever(service.defaultSort()).thenReturn(Sort.by("servercode"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/system/fido-clients").with(user(superUser)))
            .andExpect(content().string(containsString("/system/fido-clients/reload")))
            .andExpect(content().string(containsString("지금 reload 보내기")))
            // 확인은 README 규칙대로 네이티브 confirm() 이 아니라 모달(data-confirm-*)이다.
            .andExpect(content().string(not(containsString("confirm("))))
            .andExpect(content().string(containsString("data-confirm-form=\"reloadForm\"")))
    }

    @Test fun reloadIsReservedServerCode() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "reload").param("servername", "x").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: reload")))
    }

    /** 예약값 ".." 거부(특성화). */
    @Test fun reservedCodeDotsIsRejected() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "..").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: ..")))
        verify(service, never()).create(any())
    }

    /** 예약값 "." 거부(특성화). */
    @Test fun reservedCodeSingleDotIsRejected() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", ".").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: .")))
        verify(service, never()).create(any())
    }

    /** 예약값 "NEW" 거부(특성화). */
    @Test fun reservedCodeUpperNewIsRejected() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "NEW").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: NEW")))
        verify(service, never()).create(any())
    }

    /** 예약값 "ReLoad" 거부(특성화). */
    @Test fun reservedCodeMixedReloadIsRejected() {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "ReLoad").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: ReLoad")))
        verify(service, never()).create(any())
    }
}
