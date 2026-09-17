package com.crosscert.fidoadmin.fido2.web;

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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2MetadataController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class Fido2MetadataControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2MetadataService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Fido2Metadata sample() {
        Fido2Metadata m = new Fido2Metadata();
        m.setIdx(1L); m.setDescription("YubiKey 5 Series"); m.setAaguid("cb69481e-8ff7-4039-93ec-0a2729a154a8");
        m.setProtocolfamily("fido2"); m.setIcon("ICON-MARKER-SHOULD-NOT-RENDER");
        m.setAttestationrootcertificates("ROOTCERT-MARKER-SHOULD-NOT-RENDER");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/metadata").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 CLOB(ICON, ATTESTATIONROOTCERTIFICATES)을 싣지 않는다. */
    @Test void superListRendersRowsWithoutClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(sample())));
        mvc.perform(get("/fido2/metadata").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/list"))
            .andExpect(content().string(containsString("YubiKey 5 Series")))
            .andExpect(content().string(not(containsString("ICON-MARKER-SHOULD-NOT-RENDER"))))
            .andExpect(content().string(not(containsString("ROOTCERT-MARKER-SHOULD-NOT-RENDER"))));
    }

    @Test void detailShowsClob() throws Exception {
        when(service.get(1L)).thenReturn(sample());
        mvc.perform(get("/fido2/metadata/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/detail"))
            .andExpect(content().string(containsString("ICON-MARKER-SHOULD-NOT-RENDER")));
    }

    @Test void blankAaguidShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "설명").param("aaguid", "").param("issecondfactoronly", "false"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Fido2Metadata saved = new Fido2Metadata(); saved.setIdx(3L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("3");
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "Samsung Pass").param("aaguid", "53414d53-554e-4700-0000-000000000000")
                .param("issecondfactoronly", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/metadata/3"));
    }
}
