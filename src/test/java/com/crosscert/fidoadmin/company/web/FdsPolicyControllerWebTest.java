package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.FdsPolicyService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = FdsPolicyController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FdsPolicyControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean FdsPolicyService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFdsPolicy policy(long companyIdx) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(companyIdx); p.setAndCountry("KR"); p.setOrCountry("US"); p.setAndTerm("30");
        return p;
    }

    /**
     * TenantSelectionInterceptor(Task 6)가 미선택 SUPER 를 /select-tenant 로 돌려보낸다.
     * 이 클래스가 보는 경로는 TENANT 영역이므로, SUPER 요청에는 미리 테넌트를 선택해 둔다.
     */
    MockHttpSession session;

    @BeforeEach void selectTenant() {
        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(9L);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }


    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(policy(1L))));
        mvc.perform(get("/fds-policies").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/list"))
            .andExpect(content().string(containsString("KR")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/fds-policies").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void blankAndCountryShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "").param("orCountry", "KR"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("AND 국가는 필수입니다.")));
    }

    /** SUPER 는 고객사를 골라야 한다. COMPANY 는 기반이 강제하므로 검사하지 않는다. */
    @Test void superMustChooseCompanyOnCreate() throws Exception {
        mvc.perform(post("/fds-policies").session(session).with(user(superUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("고객사를 선택하세요.")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(policy(1L));
        when(service.idOf(any())).thenReturn("1");
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fds-policies/1"));
    }

    /** 고객사명이 조회되지 않는 IDX 는 목록에 "null" 대신 "#IDX" 를 보여준다. */
    @Test void listFallsBackToHashCompanyIdxWhenNameMissing() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(policy(9L))));
        mvc.perform(get("/fds-policies").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("null"))))
            .andExpect(content().string(containsString("#9 (9)")));
    }

    /** 수정 폼에서도 고객사명이 없으면 "null" 대신 "#IDX" 를 보여준다. */
    @Test void editFormFallsBackToHashCompanyIdxWhenNameMissing() throws Exception {
        when(service.get(9L)).thenReturn(policy(9L));
        mvc.perform(get("/fds-policies/9/edit").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("null"))))
            .andExpect(content().string(containsString("#9 (9)")));
    }

    @Test void detailRendersAllColumns() throws Exception {
        when(service.get(1L)).thenReturn(policy(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/fds-policies/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/detail"))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(containsString("US")));
    }
}
