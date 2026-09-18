package com.crosscert.fidoadmin.common;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantSelectionController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
    GlobalExceptionHandler.class, TenantContext.class, SelectedTenant.class})
class TenantSelectionControllerWebTest {

    @Autowired MockMvc mvc;

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
