package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
import com.crosscert.fidoadmin.auth.LoginFailureHandler
import com.crosscert.fidoadmin.auth.LoginSuccessHandler
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.auth.PasswordChangeController
import com.crosscert.fidoadmin.auth.PasswordChangeService
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.company.service.CompanyService
import com.crosscert.fidoadmin.company.web.CompanyController
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido.service.UserAccountService
import com.crosscert.fidoadmin.fido.service.UserinfoService
import com.crosscert.fidoadmin.fido.web.UserController
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * [TenantSelectionInterceptor] 가 TENANT 영역 요청을 실제로 가로채는지 확인한다.
 *
 * `/users`(TENANT), `/companies`(SYSTEM), `/me/password`(PERSONAL),
 * `/select-tenant`, 그리고 미등록 경로(`/xyz`)까지 한 슬라이스에서 검증한다.
 */
@WebMvcTest(controllers = [
    UserController::class,
    CompanyController::class,
    PasswordChangeController::class,
    TenantSelectionController::class,
])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class,
    GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class, TenantSelectionInterceptor::class)
class TenantSelectionInterceptorWebTest {

    @Autowired lateinit var mvc: MockMvc

    @MockitoBean lateinit var userinfoService: UserinfoService
    @MockitoBean lateinit var userAccountService: UserAccountService
    @MockitoBean lateinit var companyService: CompanyService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var repository: CcfaCompanyRepository
    @MockitoBean lateinit var passwordChangeService: PasswordChangeService
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    /** SUPER 로 로그인한 채, 세션에 미리 companyIdx 를 선택해 둔 상태로 요청을 보낸다. */
    private fun superUserSelecting(companyIdx: Long, session: MockHttpSession): RequestPostProcessor {
        whenever(repository.existsById(companyIdx)).thenReturn(true)
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .session(session)
                .param("companyIdx", companyIdx.toString()))
        return SecurityMockMvcRequestPostProcessors.user(superUser())
    }

    @Test fun 미선택_SUPER_가_테넌트_화면에_가면_선택_화면으로_보낸다() {
        mvc.perform(get("/users").with(user(superUser())))
            .andExpect(redirectedUrl("/select-tenant"))
    }

    @Test fun 선택한_SUPER_는_테넌트_화면을_본다() {
        val session = MockHttpSession()
        val selected = superUserSelecting(9L, session)
        whenever(userAccountService.search(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PageImpl(listOf()))

        mvc.perform(get("/users").session(session).with(selected))
            .andExpect(status().isOk())
    }

    /** 시스템 영역은 선택과 무관하다. */
    @Test fun 미선택_SUPER_도_시스템_화면은_본다() {
        whenever(companyService.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(companyService.search(any(), any()))
            .thenReturn(PageImpl(listOf()))

        mvc.perform(get("/companies").with(user(superUser())))
            .andExpect(status().isOk())
    }

    @Test fun 미선택_SUPER_도_선택_화면과_로그아웃은_된다() {
        whenever(companies.all()).thenReturn(listOf())
        mvc.perform(get("/select-tenant").with(user(superUser()))).andExpect(status().isOk())
        mvc.perform(get("/me/password").with(user(superUser()))).andExpect(status().isOk())
    }

    /** COMPANY 는 항상 유효 테넌트가 있으므로 인터셉터에 걸리지 않는다. */
    @Test fun COMPANY_는_영향을_받지_않는다() {
        whenever(userAccountService.search(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PageImpl(listOf()))

        mvc.perform(get("/users").with(user(companyUser(5L))))
            .andExpect(status().isOk())
    }

    /**
     * areaOf 가 미등록 경로를 SYSTEM 으로 판정하는 한, 인터셉터는 미등록 경로에서
     * 절대 선택을 요구하지 않는다. areaOf 가 "전부 TENANT" 로 바뀌면
     * 이 테스트가 즉시 실패한다.
     */
    @Test fun 미선택_SUPER_가_미등록_경로를_요청하면_가로채지_않는다() {
        mvc.perform(get("/xyz").with(user(superUser())))
            .andExpect(status().isNotFound())
    }

    companion object {
        @JvmStatic
        fun superUser(): ManagerUserDetails =
            ManagerUserDetails(1L, "super", null, "관리자", 0L, "전역", true, true)

        @JvmStatic
        fun companyUser(companyIdx: Long): ManagerUserDetails =
            ManagerUserDetails(2L, "biz", null, "고객사담당자", companyIdx, "고객사", true, true)
    }
}
