package com.crosscert.fidoadmin.common;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.CompanyService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@link TenantSelectionInterceptor} 가 TENANT 영역 요청을 실제로 가로채는지 확인한다.
 *
 * <p>{@code /users}(TENANT), {@code /companies}(SYSTEM), {@code /me/password}(PERSONAL),
 * {@code /select-tenant}, 그리고 미등록 경로({@code /xyz})까지 한 슬라이스에서 검증한다.
 */
@WebMvcTest(controllers = {
    com.crosscert.fidoadmin.fido.web.UserController.class,
    com.crosscert.fidoadmin.company.web.CompanyController.class,
    com.crosscert.fidoadmin.auth.PasswordChangeController.class,
    TenantSelectionController.class
})
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
    GlobalExceptionHandler.class, TenantContext.class, SelectedTenant.class, TenantSelectionInterceptor.class})
class TenantSelectionInterceptorWebTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserinfoService userinfoService;
    @MockitoBean com.crosscert.fidoadmin.fido.service.UserAccountService userAccountService;
    @MockitoBean CompanyService companyService;
    @MockitoBean CompanyLookup companies;
    @MockitoBean CcfaCompanyRepository repository;
    @MockitoBean com.crosscert.fidoadmin.auth.PasswordChangeService passwordChangeService;
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

    /** SUPER 로 로그인한 채, 세션에 미리 companyIdx 를 선택해 둔 상태로 요청을 보낸다. */
    private RequestPostProcessor superUserSelecting(long companyIdx, MockHttpSession session) throws Exception {
        when(repository.existsById(companyIdx)).thenReturn(true);
        mvc.perform(post("/select-tenant").with(user(superUser())).with(csrf())
                .session(session)
                .param("companyIdx", String.valueOf(companyIdx)));
        return SecurityMockMvcRequestPostProcessors.user(superUser());
    }

    @Test void 미선택_SUPER_가_테넌트_화면에_가면_선택_화면으로_보낸다() throws Exception {
        mvc.perform(get("/users").with(user(superUser())))
            .andExpect(redirectedUrl("/select-tenant"));
    }

    @Test void 선택한_SUPER_는_테넌트_화면을_본다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor selected = superUserSelecting(9L, session);
        when(userAccountService.search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/users").session(session).with(selected))
            .andExpect(status().isOk());
    }

    /** 시스템 영역은 선택과 무관하다. */
    @Test void 미선택_SUPER_도_시스템_화면은_본다() throws Exception {
        when(companyService.defaultSort()).thenReturn(Sort.by("idx"));
        when(companyService.search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/companies").with(user(superUser())))
            .andExpect(status().isOk());
    }

    @Test void 미선택_SUPER_도_선택_화면과_로그아웃은_된다() throws Exception {
        when(companies.all()).thenReturn(List.of());
        mvc.perform(get("/select-tenant").with(user(superUser()))).andExpect(status().isOk());
        mvc.perform(get("/me/password").with(user(superUser()))).andExpect(status().isOk());
    }

    /** COMPANY 는 항상 유효 테넌트가 있으므로 인터셉터에 걸리지 않는다. */
    @Test void COMPANY_는_영향을_받지_않는다() throws Exception {
        when(userAccountService.search(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/users").with(user(companyUser(5L))))
            .andExpect(status().isOk());
    }

    /**
     * areaOf 가 미등록 경로를 SYSTEM 으로 판정하는 한, 인터셉터는 미등록 경로에서
     * 절대 선택을 요구하지 않는다. areaOf 가 "전부 TENANT" 로 회귀하면(Task 4 에서
     * 이미 한 번 났던 Critical 버그) 이 테스트가 즉시 붉어진다.
     */
    @Test void 미선택_SUPER_가_미등록_경로를_요청하면_가로채지_않는다() throws Exception {
        mvc.perform(get("/xyz").with(user(superUser())))
            .andExpect(status().isNotFound());
    }
}
