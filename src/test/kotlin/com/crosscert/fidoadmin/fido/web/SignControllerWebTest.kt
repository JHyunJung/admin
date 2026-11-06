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
import com.crosscert.fidoadmin.fido.entity.Sign
import com.crosscert.fidoadmin.fido.service.SignQueryService
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

@WebMvcTest(controllers = [SignController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class SignControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: SignQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    companion object {
        const val PLAINTEXT_MARKER = "PLAINTEXT-MARKER-XYZ"
        const val SIGNATURE_MARKER = "SIGNATURE-MARKER-XYZ"
    }

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun sign(): Sign {
        val s = Sign()
        s.idx = 5L; s.companyIdx = 1L; s.userid = "user001"; s.assertion = "assertion-sample-001-" + "a".repeat(40)
        s.dn = "CN=user001"; s.plaintext = PLAINTEXT_MARKER; s.data = "data-001"; s.signature = SIGNATURE_MARKER
        s.memo = "이체"; s.del = "N"; s.createtime = LocalDateTime.of(2026, 9, 17, 10, 0)
        return s
    }

    /** 목록에는 CLOB(PLAINTEXT)·서명값이 실리지 않는다. ASSERTION 은 앞 32자만. */

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

    @Test fun listHidesPlaintextAndSignature() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(sign())))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        mvc.perform(get("/signs").param("userid", "user001").param("del", "N").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/list"))
            .andExpect(content().string(containsString("CN=user001")))
            .andExpect(content().string(containsString("assertion-sample-001-aaaaaaaaaaa")))
            .andExpect(content().string(not(containsString("a".repeat(40)))))
            .andExpect(content().string(not(containsString(PLAINTEXT_MARKER))))
            .andExpect(content().string(not(containsString(SIGNATURE_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/signs/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
        val captor = argumentCaptor<SignSearchForm>()
        verify(service).search(captor.capture(), any<Pageable>())
        assertThat(captor.lastValue.userid).isEqualTo("user001")
        assertThat(captor.lastValue.del).isEqualTo("N")
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superUserDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/signs").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    /** 상세에서만 PLAINTEXT·서명값 전체가 보인다. */
    @Test fun detailShowsPlaintextAndSignature() {
        whenever(service.get(5L)).thenReturn(sign())
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/signs/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/detail"))
            .andExpect(content().string(containsString(PLAINTEXT_MARKER)))
            .andExpect(content().string(containsString(SIGNATURE_MARKER)))
            .andExpect(content().string(containsString("a".repeat(40))))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
    }
}
