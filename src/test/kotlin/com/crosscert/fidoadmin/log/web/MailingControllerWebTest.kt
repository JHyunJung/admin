package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.log.service.MailingQueryService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [MailingController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class MailingControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: com.crosscert.fidoadmin.common.SelectedTenant
    @MockitoBean lateinit var service: MailingQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun row(idx: Long): CcfaMailing {
        val m = CcfaMailing()
        m.idx = idx; m.companyIdx = 1L; m.to = "ops@kb.local"; m.subject = "[FIDO] 장애 알림"
        m.content = "인증 실패율 증가"; m.status = "SENT"; m.smsTo = "010-0000-0000"
        m.smsContent = "인증 실패율 증가"; m.smsStatus = "SENT"; m.sendtime = "2026-09-15 10:05:00"
        return m
    }

    /**
     * TenantSelectionInterceptor 가 미선택 SUPER 를 /select-tenant 로 돌려보낸다.
     * 이 클래스가 보는 경로는 TENANT 영역이므로, SUPER 요청에는 미리 테넌트를 선택해 둔다.
     */
    lateinit var session: MockHttpSession

    @BeforeEach fun selectTenant() {
        session = MockHttpSession()
        val request = MockHttpServletRequest()
        request.setSession(session)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            selected.select(9L)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }


    @Test fun companyListRendersRecipientWithoutCompanyFilter() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(row(1L))))
        mvc.perform(get("/logs/mailing").param("status", "SENT").param("to", "ops").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/mailing/list"))
            .andExpect(content().string(containsString("ops@kb.local")))
            .andExpect(content().string(containsString("010-0000-0000")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
        val captor = argumentCaptor<MailingSearchForm>()
        verify(service).search(captor.capture(), any())
        org.assertj.core.api.Assertions.assertThat(captor.firstValue.status).isEqualTo("SENT")
        org.assertj.core.api.Assertions.assertThat(captor.firstValue.to).isEqualTo("ops")
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/logs/mailing").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    @Test fun detailShowsAllColumns() {
        whenever(service.get(1L)).thenReturn(row(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/logs/mailing/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/mailing/detail"))
            .andExpect(content().string(containsString("[FIDO] 장애 알림")))
            .andExpect(content().string(containsString("인증 실패율 증가")))
            .andExpect(content().string(containsString("2026-09-15 10:05:00")))
            .andExpect(content().string(containsString("KB국민은행")))
    }
}
