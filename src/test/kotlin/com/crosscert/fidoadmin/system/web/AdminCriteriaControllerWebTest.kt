package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import com.crosscert.fidoadmin.system.service.AdminCriteriaService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [AdminCriteriaController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class AdminCriteriaControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: AdminCriteriaService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun criteria(idx: Long, json: String): CcfaCriteria {
        val c = CcfaCriteria(); c.idx = idx; c.aaid = "0012#0001"; c.metahash = "hash-0001"; c.jsondata = json
        return c
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/criteria").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** 목록에는 CLOB(JSONDATA) 이 실리지 않는다. */
    @Test fun listRendersRowsWithoutJson() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(criteria(1L, "{\"marker\":\"JSON-LIST-MARKER\"}"))))
        mvc.perform(get("/system/criteria").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("JSON-LIST-MARKER"))))
    }

    /** th:text 는 따옴표를 &quot; 로 이스케이프한다. */
    @Test fun detailShowsPrettyJson() {
        whenever(service.get(1L)).thenReturn(criteria(1L, "{\"aaid\":\"0012#0001\"}"))
        mvc.perform(get("/system/criteria/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")))
    }

    @Test fun invalidJsonShowsFormAgain() {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0001").param("jsondata", "{not json"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"))
            .andExpect(content().string(containsString("올바른 JSON(객체 또는 배열)이어야 합니다.")))
        verify(service, never()).create(any())
    }

    @Test fun blankAaidShowsFormAgain() {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf()).param("aaid", "").param("jsondata", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"))
    }

    @Test fun validJsonCreateRedirectsToDetail() {
        whenever(service.create(any())).thenReturn(criteria(9L, "{}"))
        whenever(service.idOf(any())).thenReturn("9")
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0009").param("metahash", "h").param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/criteria/9"))
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/system/criteria").with(user(superUser)).param("aaid", "x")).andExpect(status().isForbidden())
    }
}
