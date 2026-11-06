package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.log.entity.CcfaExceptions
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService
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

@WebMvcTest(controllers = [ExceptionLogController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class ExceptionLogControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: com.crosscert.fidoadmin.common.SelectedTenant
    @MockitoBean lateinit var service: ExceptionLogQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun row(idx: Long): CcfaExceptions {
        val e = CcfaExceptions()
        e.idx = idx; e.companyIdx = 1L; e.eType = "AUTH"; e.eLevel = "ERROR"
        e.exceptionMessage = "Invalid signature"; e.exceptionDetailMessage = "DETAIL_ONLY_IN_DETAIL"
        e.exceptionData = "CLOB_ONLY_IN_DETAIL"; e.createdtime = "2026-09-15 10:00:00"
        return e
    }

    /** 검색 파라미터(문자열 일시 포함)가 폼에 바인딩되고, CLOB·상세 메시지는 목록에 나오지 않는다. */

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

    @Test fun listBindsStringCreatedtimeAndHidesLongColumns() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(row(1L))))
        mvc.perform(get("/logs/exceptions").param("eType", "AUTH").param("eLevel", "ERROR")
                .param("message", "signature").param("createdtime", "2026-09-15").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/list"))
            .andExpect(content().string(containsString("Invalid signature")))
            .andExpect(content().string(not(containsString("DETAIL_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("CLOB_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("name=\"fromDate\""))))
        val captor = argumentCaptor<ExceptionLogSearchForm>()
        verify(service).search(captor.capture(), any())
        val f = captor.firstValue
        org.assertj.core.api.Assertions.assertThat(f.eType).isEqualTo("AUTH")
        org.assertj.core.api.Assertions.assertThat(f.eLevel).isEqualTo("ERROR")
        org.assertj.core.api.Assertions.assertThat(f.message).isEqualTo("signature")
        org.assertj.core.api.Assertions.assertThat(f.createdtime).isEqualTo("2026-09-15")
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/logs/exceptions").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    @Test fun detailShowsAllColumns() {
        whenever(service.get(1L)).thenReturn(row(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/logs/exceptions/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/detail"))
            .andExpect(content().string(containsString("DETAIL_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("CLOB_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("2026-09-15 10:00:00")))
            .andExpect(content().string(containsString("KB국민은행")))
    }
}
