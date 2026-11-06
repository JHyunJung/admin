package com.crosscert.fidoadmin.fido.web

import org.mockito.kotlin.argumentCaptor
import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
import com.crosscert.fidoadmin.auth.LoginFailureHandler
import com.crosscert.fidoadmin.auth.LoginSuccessHandler
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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

@WebMvcTest(controllers = [TransactionConfirmationController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class TransactionConfirmationControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: TransactionConfirmationQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    companion object {
        const val CONTENT_MARKER = "TC-CONTENT-MARKER-XYZ"
    }

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun tc(): TransactionConfirmation {
        val t = TransactionConfirmation()
        t.idx = 4L; t.companyIdx = 1L; t.userid = "user001"; t.aaid = "0012#0001"
        t.contenttype = "text/plain"; t.content = CONTENT_MARKER; t.createdtime = LocalDateTime.of(2026, 9, 17, 10, 0)
        return t
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


    /** 목록에서 검색 select 뿐 아니라 고객사 컬럼도 뺐다(세션이 테넌트를 정한다). */
    @Test fun listHidesContentClob() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(tc())))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        mvc.perform(get("/transaction-confirmations").param("userid", "user001").param("aaid", "0012").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("text/plain")))
            .andExpect(content().string(not(containsString(CONTENT_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/transaction-confirmations/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
        val captor = argumentCaptor<TransactionConfirmationSearchForm>()
        verify(service).search(captor.capture(), any<Pageable>())
        assertThat(captor.lastValue.userid).isEqualTo("user001")
        assertThat(captor.lastValue.aaid).isEqualTo("0012")
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superUserDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/transaction-confirmations").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    @Test fun detailShowsContent() {
        whenever(service.get(4L)).thenReturn(tc())
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/transaction-confirmations/4").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/detail"))
            .andExpect(content().string(containsString(CONTENT_MARKER)))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
    }
}
