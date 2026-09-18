package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransactionConfirmationController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class TransactionConfirmationControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TransactionConfirmationQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static final String CONTENT_MARKER = "TC-CONTENT-MARKER-XYZ";

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private TransactionConfirmation tc() {
        TransactionConfirmation t = new TransactionConfirmation();
        t.setIdx(4L); t.setCompanyIdx(1L); t.setUserid("user001"); t.setAaid("0012#0001");
        t.setContenttype("text/plain"); t.setContent(CONTENT_MARKER); t.setCreatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return t;
    }

    @Test void listHidesContentClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(tc())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/transaction-confirmations").param("userid", "user001").param("aaid", "0012").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("text/plain")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString(CONTENT_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/transaction-confirmations/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<TransactionConfirmationSearchForm> captor = ArgumentCaptor.forClass(TransactionConfirmationSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAaid()).isEqualTo("0012");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/transaction-confirmations").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsContent() throws Exception {
        when(service.get(4L)).thenReturn(tc());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/transaction-confirmations/4").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/detail"))
            .andExpect(content().string(containsString(CONTENT_MARKER)))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
