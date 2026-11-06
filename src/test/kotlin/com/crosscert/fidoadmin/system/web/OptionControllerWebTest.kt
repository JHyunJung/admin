package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.entity.CcfaOptions
import com.crosscert.fidoadmin.system.service.OptionService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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

@WebMvcTest(controllers = [OptionController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class OptionControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: OptionService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun group(idx: Long, name: String): CcfaOption { val g = CcfaOption(); g.idx = idx; g.optionName = name; g.optionTitle = "상태"; return g }
    private fun item(idx: Long, value: String, title: String): CcfaOptions {
        val i = CcfaOptions(); i.idx = idx; i.optionIdx = 1L; i.optionValue = value; i.optionTitle = title; return i
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/options").with(user(companyUser))).andExpect(status().isForbidden())
        mvc.perform(post("/system/options/1/items").with(user(companyUser)).with(csrf()).param("optionValue", "x"))
            .andExpect(status().isForbidden())
    }

    @Test fun listRendersRows() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(group(1L, "STATUS"))))
        mvc.perform(get("/system/options").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/list"))
            .andExpect(content().string(containsString("STATUS")))
            .andExpect(content().string(containsString("/system/options/1")))
    }

    /** 상세는 그룹 정보 + 코드 목록 + 인라인 추가 폼 + 코드별 삭제 폼을 함께 그린다. */
    @Test fun detailListsItemsWithInlineForms() {
        whenever(service.get(1L)).thenReturn(group(1L, "STATUS"))
        whenever(service.items(1L)).thenReturn(listOf(item(11L, "use", "사용"), item(12L, "unuse", "미사용")))
        mvc.perform(get("/system/options/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/detail"))
            .andExpect(content().string(containsString("사용")))
            .andExpect(content().string(containsString("미사용")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items\"")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items/12/delete\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"delItem12\"")))
    }

    @Test fun addItemRedirectsToDetailAndCallsService() {
        whenever(service.addItem(eq(1L), any())).thenReturn(item(13L, "hold", "보류"))
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf())
                .param("optionValue", "hold").param("optionTitle", "보류").param("optionNote", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 추가되었습니다."))
        val captor = argumentCaptor<CcfaOptions>()
        verify(service).addItem(eq(1L), captor.capture())
        org.assertj.core.api.Assertions.assertThat(captor.lastValue.optionValue).isEqualTo("hold")
        org.assertj.core.api.Assertions.assertThat(captor.lastValue.optionTitle).isEqualTo("보류")
    }

    /** 코드 값이 비면 저장하지 않고 상세로 돌아가 오류 플래시를 보인다. */
    @Test fun addItemWithBlankValueRedirectsWithError() {
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf()).param("optionValue", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashError", "코드 값은 필수입니다(128바이트 이내)."))
        verify(service, never()).addItem(any(), any())
    }

    @Test fun removeItemRedirectsToDetail() {
        mvc.perform(post("/system/options/1/items/12/delete").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 삭제되었습니다."))
        verify(service).removeItem(1L, 12L)
    }

    @Test fun blankGroupNameShowsFormAgain() {
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/form"))
    }

    @Test fun createRedirectsToDetail() {
        whenever(service.create(any())).thenReturn(group(9L, "NEW"))
        whenever(service.idOf(any())).thenReturn("9")
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", "NEW").param("optionTitle", "신규"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/9"))
    }
}
