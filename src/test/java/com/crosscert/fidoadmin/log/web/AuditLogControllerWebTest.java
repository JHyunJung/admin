package com.crosscert.fidoadmin.log.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.service.AuditLogQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class AuditLogControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuditLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);

    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        CcfaAuditLog r = new CcfaAuditLog(); r.setIdx(5L); r.setMessage("APPID UPDATE 1"); r.setType("UPDATE");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(r)));
        mvc.perform(get("/logs/audit").param("type", "UPDATE").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("APPID UPDATE 1")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        ArgumentCaptor<AuditLogSearchForm> captor = ArgumentCaptor.forClass(AuditLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getType()).isEqualTo("UPDATE");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/audit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }
}
