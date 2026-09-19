package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = TenantSelectionController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
    GlobalExceptionHandler.class, TenantContext.class, SelectedTenant.class})
class TenantSelectionControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;

    @MockitoBean CompanyLookup companies;
    @MockitoBean CcfaCompanyRepository repository;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static ManagerUserDetails superUser() {
        return new ManagerUserDetails(1L, "super", null, "관리자", 0L, "전역", true, true);
    }

    static ManagerUserDetails companyUser(long companyIdx) {
        return new ManagerUserDetails(2L, "biz", null, "고객사담당자", companyIdx, "고객사", true, true);
    }

    @Test void SUPER_는_선택_화면을_본다() throws Exception {
        when(companies.all()).thenReturn(List.of());
        mvc.perform(get("/select-tenant").with(user(superUser())))
            .andExpect(status().isOk())
            .andExpect(view().name("tenant/select"));
    }

    /**
     * 화면에 IDX 를 드러내지 않는다. 내부 식별자를 운영자에게 보일 이유가 없다.
     *
     * <p>선택하려면 IDX 가 폼으로는 가야 하므로 hidden 필드에는 남는다. 그래서 "본문에
     * 9 가 없다" 로는 확인할 수 없고, 사람이 읽는 자리에 노출되지 않는지를 본다 —
     * 이전 화면은 "IDX 9" 라는 문구를 카드에 직접 찍었다.
     */
    @Test void 화면에_IDX_를_드러내지_않는다() throws Exception {
        CcfaCompany c = new CcfaCompany();
        c.setIdx(9L);
        c.setCompanyName("테스트고객사");
        when(companies.all()).thenReturn(List.of(c));

        String html = mvc.perform(get("/select-tenant").with(user(superUser())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("테스트고객사");
        assertThat(html).doesNotContain("IDX 9");
        assertThat(html).doesNotContainIgnoringCase(">IDX");
        // 선택 자체는 계속 가능해야 한다(hidden 으로는 남는다).
        assertThat(html).contains("name=\"companyIdx\"");
    }

    /** 카드가 아니라 목록(표)으로 보여준다. */
    @Test void 고객사를_목록으로_보여준다() throws Exception {
        CcfaCompany c = new CcfaCompany();
        c.setIdx(9L);
        c.setCompanyName("테스트고객사");
        when(companies.all()).thenReturn(List.of(c));

        String html = mvc.perform(get("/select-tenant").with(user(superUser())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<table");
        // 이름 검색이 계속 동작하려면 행에 표식과 이름이 남아 있어야 한다.
        assertThat(html).contains("data-tenant-card");
        assertThat(html).contains("data-name=\"테스트고객사\"");
    }

    /**
     * 행 클릭으로 선택하되, 스크립트 없이도 선택할 수 있어야 한다.
     *
     * <p>행 클릭은 admin.js 가 data-tenant-row 를 잡아 폼을 제출하는 방식이다. 그래서
     * 각 행은 여전히 폼이고 고객사 이름이 submit 버튼이다 — tr 에 onclick 만 다는 구현으로
     * 바뀌면 스크립트가 없는 환경과 키보드·스크린리더에서 선택할 길이 사라진다.
     * 그 회귀를 여기서 막는다(대시보드가 조회 버튼을 남겨둔 것과 같은 이유다).
     */
    @Test void 스크립트_없이도_선택할_수_있다() throws Exception {
        CcfaCompany c = new CcfaCompany();
        c.setIdx(9L);
        c.setCompanyName("테스트고객사");
        when(companies.all()).thenReturn(List.of(c));

        String html = mvc.perform(get("/select-tenant").with(user(superUser())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // 행 클릭 배선
        assertThat(html).contains("data-tenant-row");
        assertThat(html).contains("data-tenant-form");
        // 스크립트가 없을 때의 선택 수단: 행 안의 실제 폼과 submit 버튼
        assertThat(html).contains("<form method=\"post\" action=\"/select-tenant\"");
        assertThat(html).contains("type=\"submit\"");
    }

    /** COMPANY 는 고를 것이 없다. */
    @Test void COMPANY_는_대시보드로_보낸다() throws Exception {
        mvc.perform(get("/select-tenant").with(user(companyUser(5L))))
            .andExpect(redirectedUrl("/"));
    }

    @Test void 선택하면_돌아갈_곳으로_리다이렉트한다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/users"));
    }

    /**
     * 상세 화면의 ID 는 이전 테넌트의 것이라 새 테넌트에서는 404 다.
     * 목록으로 절상해서 보낸다.
     */
    @Test void 상세_화면에서_전환하면_목록으로_보낸다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users/123"))
            .andExpect(redirectedUrl("/users"));
    }

    /** 외부 URL 로 튕기지 않게 한다(오픈 리다이렉트 차단). */
    @Test void 외부_주소로는_리다이렉트하지_않는다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "https://evil.example/x"))
            .andExpect(redirectedUrl("/"));
    }

    @Test void 존재하지_않는_고객사는_거부한다() throws Exception {
        when(repository.existsById(99999L)).thenReturn(false);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "99999").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/select-tenant"));
    }

    /** IDX 0 은 SUPER 를 뜻하므로 테넌트가 될 수 없다. */
    @Test void 전역_고객사는_거부한다() throws Exception {
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "0").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/select-tenant"));
    }

    /**
     * POST 가드 회귀 테스트: COMPANY 는 테넌트를 바꿀 것이 없으므로 조작된 POST 요청도
     * 대시보드로 돌려보내야 한다.
     *
     * <p>리다이렉트 목적지만 확인하면 selected.select() 가 이미 실행된 뒤에도 통과한다
     * (컨트롤러가 가드 없이 selected.select() 를 호출한 다음에야 "redirect:/" 를 반환해도
     * 이 단언은 여전히 성공한다). 그래서 세션의 SelectedTenant 가 그대로인지도 직접 확인한다 —
     * 이것이 이 테스트의 핵심이다. 가드가 POST 에서만 빠지는 회귀(리뷰가 지목한 권한 상승
     * 경로)는 리다이렉트만 보는 단언으로는 절대 잡히지 않는다.
     */
    @Test void COMPANY_의_조작된_POST는_테넌트를_바꾸지_못하고_대시보드로_보낸다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(repository.existsById(9L)).thenReturn(true);

        mvc.perform(post("/select-tenant").with(user(companyUser(5L))).with(csrf())
                .session(session)
                .param("companyIdx", "9").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/"));

        assertThat(companyIdxSelectedIn(session)).isEmpty();
    }

    /**
     * 세션 스코프 빈은 요청 스레드에서만 프록시가 풀린다. MockMvc 요청이 끝나면
     * RequestContextHolder 바인딩도 사라지므로, 검사 시점에만 같은 세션을 임시로 걸어
     * 그 세션에 실제로 저장된 SelectedTenant 인스턴스에서 값을 읽어낸다.
     *
     * <p>바인딩을 건 채로 메서드 호출까지 끝내야 한다 — 프록시(selected)를 반환만 하고
     * 바인딩을 풀면, 호출자가 나중에 메서드를 호출하는 시점엔 이미 스레드에 세션이
     * 없어 ScopeNotActiveException 이 난다.
     */
    private Optional<Long> companyIdxSelectedIn(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return selected.companyIdx();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    // ---- 오픈 리다이렉트 경계 핀 테스트 (safeReturnTo) ----

    /** "//evil.com" 은 startsWith("//") 에 걸려 거부된다(스킴 상대 URL). */
    @Test void 슬래시_두_개는_거부한다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "//evil.com"))
            .andExpect(redirectedUrl("/"));
    }

    /** "https://evil.com" 은 "/" 로 시작하지 않아 거부된다. */
    @Test void 절대_URL은_거부한다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "https://evil.com"))
            .andExpect(redirectedUrl("/"));
    }

    /** "/\\evil.com" 은 "/" 로 시작하지만 areaOf 가 TENANT 가 아니므로 거부된다. */
    @Test void 백슬래시_경로는_거부한다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/\\evil.com"))
            .andExpect(redirectedUrl("/"));
    }

    /** "/users/../admin" 은 listPathFor 가 등록된 메뉴 경로로 치환하므로 경로 조작이 못 빠져나간다. */
    @Test void 경로_조작_시도는_등록된_목록_경로로_절상된다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users/../admin"))
            .andExpect(redirectedUrl("/users"));
    }

    /** "/users@evil.com" 은 withSlash 접두사 불일치로 areaOf 가 SYSTEM 을 반환해 거부된다. */
    @Test void 접두사_흉내_경로는_거부한다() throws Exception {
        when(repository.existsById(9L)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users@evil.com"))
            .andExpect(redirectedUrl("/"));
    }
}
