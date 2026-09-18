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
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AppserverController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class AppserverControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AppserverService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Appserver server(long idx) {
        Appserver a = new Appserver(); a.setIdx(idx); a.setCompanyIdx(1L); a.setMemberCode("KB01");
        a.setMemberId("kbsvr01"); a.setType("use"); a.setNote("스타뱅킹 서버");
        return a;
    }

    @Test void listRendersMemberCode() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(server(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/appservers").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/list"))
            .andExpect(content().string(containsString("KB01")))
            .andExpect(content().string(containsString("kbsvr01")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void blankMemberIdShowsFormAgain() throws Exception {
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "KB01").param("memberId", "").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"));
    }

    /** MEMBER_CODE 는 VARCHAR2(32). 33바이트는 저장 전에 폼에서 거부되어야 한다. */
    @Test void memberCodeOverByteLimitIsRejectedWithMessage() throws Exception {
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "a".repeat(33)).param("memberId", "id").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(server(9L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "KB01").param("memberId", "kbsvr01").param("type", "use"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appservers/9"));
    }

    @Test void detailRendersColumns() throws Exception {
        when(service.get(2L)).thenReturn(server(2L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/appservers/2").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("스타뱅킹 서버")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
