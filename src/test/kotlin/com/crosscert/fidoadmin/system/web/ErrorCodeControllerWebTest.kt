package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable
import com.crosscert.fidoadmin.system.service.ErrorCodeService
import org.hamcrest.Matchers.containsString
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [ErrorCodeController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class ErrorCodeControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: ErrorCodeService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun code(c: String, msg: String): CcfaErrorTable { val e = CcfaErrorTable(); e.errorCode = c; e.errorMessage = msg; e.errorType = "ERROR"; return e }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/error-codes").with(user(companyUser))).andExpect(status().isForbidden())
    }

    @Test fun listRendersRows() {
        whenever(service.defaultSort()).thenReturn(Sort.by("errorCode"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(code("1498", "INVALID_SIGNATURE"))))
        mvc.perform(get("/system/error-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/list"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")))
            .andExpect(content().string(containsString("/system/error-codes/1498")))
    }

    @Test fun detailRenders() {
        whenever(service.get("1498")).thenReturn(code("1498", "INVALID_SIGNATURE"))
        mvc.perform(get("/system/error-codes/1498").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/detail"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")))
    }

    @Test fun codeOverByteLimitShowsFormAgain() {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf())
                .param("errorCode", "1".repeat(21)).param("errorMessage", "x"))   // VARCHAR2(20)
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")))
    }

    @Test fun createRedirectsToDetail() {
        whenever(service.create(any())).thenReturn(code("1499", "x"))
        whenever(service.idOf(any())).thenReturn("1499")
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "1499").param("errorMessage", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/error-codes/1499"))
    }

    /** errorCode 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test fun invalidCodeCharacterShowsFormAgain() {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "a{x}").param("errorMessage", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")))
        verify(service, never()).create(any())
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test fun reservedCodeNewIsRejected() {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "new").param("errorMessage", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")))
        verify(service, never()).create(any())
    }
}
