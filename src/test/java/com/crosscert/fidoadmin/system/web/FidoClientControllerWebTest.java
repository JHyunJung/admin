package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
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
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.service.FidoClientService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FidoClientController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FidoClientControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FidoClientService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFidoclient client(String code, String name) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServername(name);
        c.setServerurl("https://fido1.internal:8443"); c.setStatus("ON"); return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/fido-clients").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("servercode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(client("FIDO01", "FIDO 서버 1"))));
        mvc.perform(get("/system/fido-clients").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/list"))
            .andExpect(content().string(containsString("FIDO 서버 1")))
            .andExpect(content().string(containsString("/system/fido-clients/FIDO01")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("FIDO01")).thenReturn(client("FIDO01", "FIDO 서버 1"));
        mvc.perform(get("/system/fido-clients/FIDO01").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/detail"))
            .andExpect(content().string(containsString("https://fido1.internal:8443")));
    }

    /** SERVERNAME·SERVERURL 은 NOT NULL. 비우면 저장 전에 폼에서 잡는다. */
    @Test void missingRequiredFieldsShowFormAgain() throws Exception {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "").param("serverurl", "").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(client("FIDO03", "x"));
        when(service.idOf(any())).thenReturn("FIDO03");
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "FIDO 서버 3").param("serverurl", "https://fido3.internal:8443").param("status", "ON"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fido-clients/FIDO03"));
    }

    /** servercode 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test void invalidCodeCharacterShowsFormAgain() throws Exception {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "a{x}").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")));
        verify(service, never()).create(any());
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test void reservedCodeNewIsRejected() throws Exception {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "new").param("servername", "n").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")));
        verify(service, never()).create(any());
    }
}
