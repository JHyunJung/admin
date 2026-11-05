package com.crosscert.fidoadmin.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.common.MenuRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨테이너 오류 페이지(WebMvcConfig.errorPages)는 원래 요청의 메서드 그대로 /error/{code} 로 들어온다.
 * POST 가 403(CSRF 토큰 만료 등)·404·500 으로 끝나면 POST /error/403 이 되므로 GET 만 받으면 405 로 다시 깨진다.
 * MockMvc 는 오류 디스패치를 하지 않으므로 그 경로를 직접 부른다.
 */
@WebMvcTest(controllers = ErrorPageController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, MenuRegistry.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class ErrorPageControllerWebTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;

    @Test void rendersErrorPagesForGet() throws Exception {
        mvc.perform(get("/error/403")).andExpect(status().isOk()).andExpect(view().name("error/403"));
        mvc.perform(get("/error/404")).andExpect(status().isOk()).andExpect(view().name("error/404"));
        mvc.perform(get("/error/500")).andExpect(status().isOk()).andExpect(view().name("error/500"));
    }

    @Test void rendersErrorPagesForPostAndOtherMethods() throws Exception {
        mvc.perform(post("/error/403").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/403"));
        mvc.perform(post("/error/404").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/404"));
        mvc.perform(post("/error/500").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/500"));
        mvc.perform(delete("/error/403").with(csrf())).andExpect(status().isOk()).andExpect(view().name("error/403"));
    }

    @Test void unknownCodeIsNotAnErrorPage() throws Exception {
        mvc.perform(get("/error/418")).andExpect(status().isNotFound());
    }
}
