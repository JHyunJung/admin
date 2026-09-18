package com.crosscert.fidoadmin.fido2.web;

import static org.hamcrest.Matchers.containsString;
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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2CredentialParamsController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class Fido2CredentialParamsControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2CredentialParamsService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/credential-params").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superListRendersRows() throws Exception {
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setIdx(1L); p.setCredType("public-key"); p.setCredAlg(-7L); p.setStatus("T");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(p)));
        mvc.perform(get("/fido2/credential-params").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/list"))
            .andExpect(content().string(containsString("public-key")))
            .andExpect(content().string(containsString("-7")));
    }

    @Test void missingCredAlgShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "").param("status", "T"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Fido2CredentialParams saved = new Fido2CredentialParams(); saved.setIdx(4L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("4");
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "-8").param("status", "T"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/credential-params/4"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).param("credAlg", "-7"))
            .andExpect(status().isForbidden());
    }
}
