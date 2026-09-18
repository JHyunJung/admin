package com.crosscert.fidoadmin.fido.web;

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
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.service.AppidService;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(controllers = AppidController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class AppidControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean AppidService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Appid appid(long idx) {
        Appid a = new Appid(); a.setIdx(idx); a.setCompanyIdx(1L); a.setAppid("https://kbstar.com/facets.json");
        a.setServicename("kbstar"); a.setStatus("use"); a.setDevice("android"); a.setDeviceDefault("T");
        return a;
    }

    private CcfaCompany kb() { CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행"); return c; }

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


    /** Task 10: 목록에서 검색 select 뿐 아니라 고객사 컬럼도 뺐다(세션이 테넌트를 정한다). */
    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(appid(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/appids").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/list"))
            .andExpect(content().string(containsString("https://kbstar.com/facets.json")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    /**
     * Task 10: 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 만으로는 상단 고객사 전환 드롭다운의 hidden 필드와 구별되지 않으므로
     * 검색폼 select 태그로 특정한다.
     */
    @Test void superUserDoesNotSeeCompanyFilterOnList() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(companies.all()).thenReturn(List.of(kb()));
        mvc.perform(get("/appids").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))));
    }

    @Test void blankAppidShowsFormAgain() throws Exception {
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Appid saved = appid(5L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("5");
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "https://kbstar.com/facets.json").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appids/5"));
    }

    @Test void detailRendersAllColumns() throws Exception {
        when(service.get(3L)).thenReturn(appid(3L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/appids/3").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/detail"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(containsString("KB국민은행")));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/appids").with(user(companyUser)).param("appid", "x")).andExpect(status().isForbidden());
    }
}
