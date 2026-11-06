package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import com.crosscert.fidoadmin.system.service.SystemInfoService
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

@WebMvcTest(controllers = [SystemInfoController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class SystemInfoControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: SystemInfoService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun info(key: String, value: String): CcfaSystemInfo { val i = CcfaSystemInfo(); i.propKey = key; i.propValue = value; return i }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/info").with(user(companyUser))).andExpect(status().isForbidden())
    }

    @Test fun listRendersRows() {
        whenever(service.defaultSort()).thenReturn(Sort.by("propKey"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(info("VERSION", "1.0.0"))))
        mvc.perform(get("/system/info").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/list"))
            .andExpect(content().string(containsString("VERSION")))
            .andExpect(content().string(containsString("/system/info/VERSION")))
    }

    @Test fun detailRenders() {
        whenever(service.get("VERSION")).thenReturn(info("VERSION", "1.0.0"))
        mvc.perform(get("/system/info/VERSION").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/detail"))
            .andExpect(content().string(containsString("1.0.0")))
    }

    @Test fun blankKeyShowsFormAgain() {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"))
    }

    @Test fun createRedirectsToDetail() {
        whenever(service.create(any())).thenReturn(info("NEW_KEY", "x"))
        whenever(service.idOf(any())).thenReturn("NEW_KEY")
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "NEW_KEY").param("propValue", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/info/NEW_KEY"))
    }

    /** propKey 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test fun invalidKeyCharacterShowsFormAgain() {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "a{x}").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")))
        verify(service, never()).create(any())
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test fun reservedKeyNewIsRejected() {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "new").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")))
        verify(service, never()).create(any())
    }
}
