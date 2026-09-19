package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.dashboard.DashboardController;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class LayoutWebTest {

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;

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

    /**
     * Task 10 부터 사이드바·상단바가 {@code selectedCompanyName}·{@code selectableCompanies}
     * (둘 다 CompanyLookup 을 거친다)로 테넌트 선택 여부를 가린다. 목을 기본값(null/빈 리스트)으로
     * 두면 COMPANY 계정도 "미선택"으로 보여 TENANT 메뉴가 비활성화되므로, 모든 테스트가 공유하는
     * 기본값을 여기서 채운다. 특정 테넌트 이름이 필요한 SUPER 케이스는 superSelecting() 이 덮어쓴다.
     */
    @BeforeEach void stubCompanyLookupDefaults() {
        when(companies.name(anyLong())).thenReturn("KB");
        CcfaCompany kb = new CcfaCompany(); kb.setIdx(9L); kb.setCompanyName("KB국민은행");
        when(companies.all()).thenReturn(List.of(kb));
    }

    /**
     * SUPER 가 레이아웃 전체를 보려면 유효 테넌트가 있어야 한다(Task 6, TenantSelectionInterceptor).
     * 이 슬라이스에는 TenantSelectionController 가 없으므로 세션 스코프 빈에 직접 선택값을 넣는다.
     * 세션 빈은 요청 스레드에서만 프록시가 풀리므로, 세팅하는 동안만 RequestContextHolder 를 임시로 건다.
     *
     * <p>Task 10 부터 사이드바가 {@code selectedCompanyName}(= {@code companies.name(idx)})으로
     * 테넌트 선택 여부를 가린다. 목이 기본값(null)을 주면 "미선택"으로 보여 TENANT 메뉴가 비활성화되므로
     * 여기서 함께 이름을 채워 둔다.
     */
    private MockHttpSession superSelecting(long companyIdx) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(companyIdx);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        when(companies.name(companyIdx)).thenReturn("KB국민은행");
        return session;
    }

    @Test void anonymousRedirectsToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void superSeesSystemMenu() throws Exception {
        mvc.perform(get("/").session(superSelecting(9L)).with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
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

    /**
     * 본인 계정 메뉴(MenuArea.PERSONAL)는 사이드바가 아니라 헤더의 사용자 드롭다운에 있다.
     * 업무 메뉴와 성격이 달라 옮겼다.
     *
     * <p>"어딘가에 /me/password 가 있다" 로는 옮겨졌는지 알 수 없다(옮기기 전에도 통과한다).
     * 그래서 드롭다운 컨테이너와 그 안의 항목을 함께 본다.
     */
    @Test void 개인_메뉴는_헤더_드롭다운에_있다() throws Exception {
        String html = mvc.perform(get("/").session(superSelecting(9L))
                .with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"account-menu\"");

        String dropdown = html.substring(html.indexOf("id=\"account-menu\""));
        dropdown = dropdown.substring(0, dropdown.indexOf("</div>", dropdown.indexOf("</ul>")));
        assertThat(dropdown).as("개인 메뉴가 헤더 드롭다운 안에 있어야 한다").contains("/me/password");
        assertThat(dropdown).as("로그아웃도 같은 드롭다운으로 모았다").contains("/logout");
    }

    /** 사이드바에는 개인 메뉴를 그리지 않는다(헤더로 옮긴 것이 사이드바에도 남으면 중복이다). */
    @Test void 사이드바에는_개인_메뉴가_없다() throws Exception {
        String html = mvc.perform(get("/").session(superSelecting(9L))
                .with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String sidebar = html.substring(html.indexOf("fa-sidebar"), html.indexOf("fa-main"));
        assertThat(sidebar).as("사이드바에 개인 메뉴가 남아 있다").doesNotContain("/me/password");
        assertThat(sidebar).as("사이드바에 '내 정보' 그룹이 남아 있다").doesNotContain("내 정보");
        // 업무 메뉴는 그대로 있어야 한다 — 위 단언이 사이드바를 통째로 비워도 통과하지 않게 한다.
        assertThat(sidebar).contains("/appids");
    }

    /** 사이드바 메뉴에 Bootstrap Icons 아이콘이 렌더링되고, 아이콘 폰트 CSS 가 실린다. */
    @Test void sidebarRendersMenuIcons() throws Exception {
        mvc.perform(get("/").session(superSelecting(9L)).with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
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
        mvc.perform(get("/").session(superSelecting(9L)).with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
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
        mvc.perform(get("/").session(superSelecting(9L)).with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/signups")))
            .andExpect(content().string(containsString("가입 승인")));
    }

    /** TENANT 화면(대시보드)을 보는 SUPER 는 상단 고객사 전환 드롭다운을 본다. */
    @Test void 테넌트_화면에는_선택기가_보인다() throws Exception {
        mvc.perform(get("/").session(superSelecting(9L)).with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("tenant-switcher")));
    }

    /**
     * SYSTEM 화면에서 SUPER 가 "시스템 관리" 뱃지를 보는지는 이 슬라이스에 SYSTEM 영역
     * 컨트롤러가 없어(DashboardController 만 로드) 여기서 검증할 수 없다.
     * {@code CompanyControllerWebTest.시스템_화면에는_시스템_뱃지가_보인다} 가 대신 검증한다.
     */

    /** COMPANY 계정은 자기 고객사가 고정이므로 선택기를 보지 않는다. */
    @Test void COMPANY_에게는_선택기가_보이지_않는다() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("tenant-switcher"))));
    }
}
