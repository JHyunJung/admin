package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.service.AdminCriteriaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminCriteriaController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class AdminCriteriaControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminCriteriaService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaCriteria criteria(long idx, String json) {
        CcfaCriteria c = new CcfaCriteria(); c.setIdx(idx); c.setAaid("0012#0001"); c.setMetahash("hash-0001"); c.setJsondata(json);
        return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/criteria").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록에는 CLOB(JSONDATA) 이 실리지 않는다. */
    @Test void listRendersRowsWithoutJson() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria(1L, "{\"marker\":\"JSON-LIST-MARKER\"}"))));
        mvc.perform(get("/system/criteria").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("JSON-LIST-MARKER"))));
    }

    /** th:text 는 따옴표를 &quot; 로 이스케이프한다. */
    @Test void detailShowsPrettyJson() throws Exception {
        when(service.get(1L)).thenReturn(criteria(1L, "{\"aaid\":\"0012#0001\"}"));
        mvc.perform(get("/system/criteria/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")));
    }

    @Test void invalidJsonShowsFormAgain() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0001").param("jsondata", "{not json"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"))
            .andExpect(content().string(containsString("올바른 JSON(객체 또는 배열)이어야 합니다.")));
        verify(service, never()).create(any());
    }

    @Test void blankAaidShowsFormAgain() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf()).param("aaid", "").param("jsondata", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"));
    }

    @Test void validJsonCreateRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(criteria(9L, "{}"));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0009").param("metahash", "h").param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/criteria/9"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).param("aaid", "x")).andExpect(status().isForbidden());
    }
}
