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

    /**
     * 모든 화면이 favicon 을 건다(브라우저의 /favicon.ico 404 를 없앤다).
     * 경로에는 콘텐츠 해시가 붙을 수 있으므로 파일명 앞부분으로 확인한다.
     */
    @Test void pagesLinkFavicon() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("rel=\"icon\"")))
            .andExpect(content().string(containsString("/favicon")))
            .andExpect(content().string(containsString("rel=\"apple-touch-icon\"")));
    }

    /**
     * favicon 은 로그인 전에도 받을 수 있어야 한다. 로그인으로 넘기면(302) 로그인 화면에 아이콘이 안 나온다.
     * 콘텐츠 해시가 붙은 이름(favicon-&lt;md5&gt;.ico)도 마찬가지다.
     */
    @Test void anonymousCanFetchFavicon() throws Exception {
        for (String path : new String[] {"/favicon.ico", "/favicon.png",
                "/favicon-662980f9344b8e32ef74d9e4e6b4135a.ico", "/favicon-6e37d015e36748e80bbb28466de80f17.png"}) {
            mvc.perform(get(path))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 302) throw new AssertionError(path + " 가 로그인으로 넘어간다(302). permitAll 대상이어야 한다.");
                });
        }
    }

    /** 폰트는 jar 안에서 서빙한다(외부 호출 금지). 로그인 전에도 받을 수 있어야 한다. */
    @Test void anonymousCanFetchBundledFont() throws Exception {
        mvc.perform(get("/fonts/Pretendard-Regular.subset.woff2"))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s == 302) throw new AssertionError("폰트가 로그인으로 넘어간다(302). permitAll 대상이어야 한다.");
            });
    }

    @Test void companyGetsForbiddenOnSuperUrl() throws Exception {
        mvc.perform(get("/companies").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isForbidden());
    }

    /**
     * 고객사 계정의 사이드바에 "가입 승인"이 렌더되지 않는다(설계서 8장 접근 제어).
     *
     * <p>{@code MenuRegistryTest} 는 레지스트리 <em>데이터</em>가 superOnly 인지만 본다.
     * 그러나 실제로 새는 곳은 렌더된 HTML 이다. 템플릿이 {@code itemsFor(isSuper)} 대신
     * {@code MenuRegistry.ALL} 을 쓰도록 바뀌면 데이터 테스트는 통과하지만 메뉴는 노출된다.
     * 여기서는 응답 본문을 직접 본다.
     */
    @Test void companySidebarHidesSignupApprovalMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/signups"))))
            .andExpect(content().string(not(containsString("가입 승인"))));
    }

    /** SUPER 의 사이드바에는 보인다. 위 테스트가 "아무것도 안 보여서" 통과하는 것을 막는다. */
    @Test void superSidebarShowsSignupApprovalMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/signups")))
            .andExpect(content().string(containsString("가입 승인")));
    }
}
