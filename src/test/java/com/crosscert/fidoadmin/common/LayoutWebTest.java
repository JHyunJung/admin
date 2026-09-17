package com.crosscert.fidoadmin.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.dashboard.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class LayoutWebTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;
    // Task 11 에서 DashboardController 가 통계 조회·고객사 조회를 주입받도록 바뀌었다.
    @MockitoBean com.crosscert.fidoadmin.dashboard.StatisticsQueryService stats;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;

    static ManagerUserDetails user(long companyIdx) {
        return new ManagerUserDetails(1L, "u", null, "홍길동", companyIdx, "KB", true, true);
    }

    @Test void anonymousRedirectsToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void superSeesSystemMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/system/props")))
            .andExpect(content().string(containsString("홍길동")));
    }

    @Test void companyDoesNotSeeSystemMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/system/props"))))
            .andExpect(content().string(containsString("/appids")));
    }

    /** 사이드바 메뉴에 Bootstrap Icons 아이콘이 렌더링되고, 아이콘 폰트 CSS 가 실린다. */
    @Test void sidebarRendersMenuIcons() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap-icons")))
            .andExpect(content().string(containsString("bi bi-speedometer2")))
            .andExpect(content().string(containsString("bi bi-sliders")));
    }

    @Test void companyGetsForbiddenOnSuperUrl() throws Exception {
        mvc.perform(get("/companies").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isForbidden());
    }
}
