package com.crosscert.fidoadmin.company.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    /**
     * Task 10: 테넌트는 이제 세션이 정하므로, SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * 고객사를 여전히 골라야 하는 곳은 등록 폼(할당형 PK)뿐이다 — superMustChooseCompanyOnCreate 참고.
     * "name=\"companyIdx\"" 만으로는 상단 고객사 전환 드롭다운의 hidden 필드와 구별되지 않으므로
     * 검색폼 select 태그로 특정한다.
     */
    @Test void superUserDoesNotSeeCompanyFilterOnList() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/fds-policies").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))));
    }

    @Test void blankAndCountryShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "").param("orCountry", "KR"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("AND 국가는 필수입니다.")));
    }

    /**
     * Task 11: 고객사 select 가 사라진 뒤로는 SUPER 도 폼 값 없이 유효 테넌트(세션이 고른 9)로
     * 등록한다. 예전에는 폼 companyIdx 가 비어 있으면 "고객사를 선택하세요." 로 거부했지만,
     * 유효 테넌트는 항상 값이 있으므로 그 상태 자체가 더는 나타나지 않는다.
     */
    @Test void superCreatesUsingSelectedTenantWithoutFormValue() throws Exception {
        when(service.create(any())).thenReturn(policy(9L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/fds-policies").session(session).with(user(superUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fds-policies/9"));
        org.mockito.ArgumentCaptor<CcfaFdsPolicy> captor = org.mockito.ArgumentCaptor.forClass(CcfaFdsPolicy.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(9L);
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(policy(1L));
        when(service.idOf(any())).thenReturn("1");
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fds-policies/1"));
    }

    /**
     * Task 10 부터 목록은 고객사명을 보여주지 않는다(검색폼과 함께 고객사 컬럼도 뺐다 — 세션이
     * 테넌트를 정하므로 행마다 소속을 나열할 이유가 없다). 그래서 이 테스트는 더는 "#IDX 로 대체
     * 표시되는지"를 볼 수 없고, 대신 목록 행이 "null" 을 새지 않고 렌더되는지만 지킨다.
     * "#IDX" 대체 표시 자체는 editFormFallsBackToHashCompanyIdxWhenNameMissing 이 계속 지킨다.
     */
    @Test void listRendersRowWithoutLeakingNull() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(policy(9L))));
        mvc.perform(get("/fds-policies").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("null"))))
            .andExpect(content().string(containsString("KR")));
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
