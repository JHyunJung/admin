package com.crosscert.fidoadmin.company.web;

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
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.LicenseService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
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

@WebMvcTest(controllers = LicenseController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class LicenseControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean LicenseService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/licenses").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superListRendersRowsWithCompanyName() throws Exception {
        CcfaLicense l = new CcfaLicense(); l.setIdx(1L); l.setCompanyIdx(1L); l.setServiceName("kbstar"); l.setContactName("홍길동");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(l)));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/licenses").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/license/list"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void createWithoutCompanyShowsFormAgain() throws Exception {
        mvc.perform(post("/licenses").with(user(superUser)).with(csrf()).param("serviceName", "kbstar"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/license/form"))
            .andExpect(content().string(containsString("고객사를 선택하세요.")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        CcfaLicense saved = new CcfaLicense(); saved.setIdx(55L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("55");
        mvc.perform(post("/licenses").with(user(superUser)).with(csrf()).param("companyIdx", "1").param("serviceName", "kbstar"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/licenses/55"));
    }

    @Test void detailShowsLicenseText() throws Exception {
        CcfaLicense l = new CcfaLicense(); l.setIdx(1L); l.setCompanyIdx(1L); l.setLicense("LICENSE-SAMPLE-KEY");
        when(service.get(1L)).thenReturn(l);
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/licenses/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("LICENSE-SAMPLE-KEY")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
