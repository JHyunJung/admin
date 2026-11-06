package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.company.service.LicenseService
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
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
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [LicenseController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class LicenseControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: com.crosscert.fidoadmin.common.SelectedTenant
    @MockitoBean lateinit var service: LicenseService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

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


    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/licenses").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /**
     * 테넌트는 세션이 정하므로 목록은 더는 고객사 검색 select 나 고객사 컬럼을 보여주지 않는다.
     * (companyNames 는 상세 화면이 여전히 쓰므로 컨트롤러에는 남아 있다 — 목록 템플릿만 걷어냈다.)
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superListRendersRowsWithoutCompanyFilter() {
        val l = CcfaLicense(); l.idx = 1L; l.companyIdx = 1L; l.serviceName = "kbstar"; l.contactName = "홍길동"
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(l)))
        whenever(companies.names()).thenReturn(java.util.Map.of(1L, "KB국민은행"))
        mvc.perform(get("/licenses").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/license/list"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    /**
     * 폼에 고객사 select 가 없다. companyIdx 는 CrudService.create() 가 유효
     * 테넌트로 채우므로 폼이 이 값을 안 보내도(브라우저가 못 보내므로) 등록은 그대로 성공해야 한다
     * (LicenseForm.companyIdx 의 @NotNull 을 뺐다 — 안 그러면 select 가 없어졌으므로 모든 등록이
     * @Valid 단계에서 막힌다). 이전의 "고객사 미선택 시 폼 재표시" 테스트는 그래서 의미가 사라졌다.
     */
    @Test fun createRedirectsToDetail() {
        val saved = CcfaLicense(); saved.idx = 55L
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("55")
        mvc.perform(post("/licenses").session(session).with(user(superUser)).with(csrf()).param("serviceName", "kbstar"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/licenses/55"))
    }

    @Test fun detailShowsLicenseText() {
        val l = CcfaLicense(); l.idx = 1L; l.companyIdx = 1L; l.license = "LICENSE-SAMPLE-KEY"
        whenever(service.get(1L)).thenReturn(l)
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/licenses/1").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("LICENSE-SAMPLE-KEY")))
            .andExpect(content().string(containsString("KB국민은행")))
    }
}
