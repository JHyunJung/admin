package com.crosscert.fidoadmin.config

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
import com.crosscert.fidoadmin.auth.LoginFailureHandler
import com.crosscert.fidoadmin.auth.LoginSuccessHandler
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

/**
 * 컨테이너 오류 페이지(WebMvcConfig.errorPages)는 원래 요청의 메서드 그대로 /error/{code} 로 들어온다.
 * POST 가 403(CSRF 토큰 만료 등)·404·500 으로 끝나면 POST /error/403 이 되므로 GET 만 받으면 405 로 다시 깨진다.
 * MockMvc 는 오류 디스패치를 하지 않으므로 그 경로를 직접 부른다.
 */
@WebMvcTest(controllers = [ErrorPageController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, MenuRegistry::class,
    TenantContext::class, SelectedTenant::class)
class ErrorPageControllerWebTest {

    @Autowired lateinit var mvc: MockMvc

    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService
    @MockitoBean lateinit var companies: CompanyLookup

    @Test fun rendersErrorPagesForGet() {
        mvc.perform(get("/error/403")).andExpect(status().isOk()).andExpect(view().name("error/403"))
        mvc.perform(get("/error/404")).andExpect(status().isOk()).andExpect(view().name("error/404"))
        mvc.perform(get("/error/500")).andExpect(status().isOk()).andExpect(view().name("error/500"))
    }

    @Test fun rendersErrorPagesForPostAndOtherMethods() {
        mvc.perform(post("/error/403").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/403"))
        mvc.perform(post("/error/404").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/404"))
        mvc.perform(post("/error/500").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/500"))
        mvc.perform(delete("/error/403").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/403"))
    }

    @Test fun unknownCodeIsNotAnErrorPage() {
        mvc.perform(get("/error/418")).andExpect(status().isNotFound())
    }
}
