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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = CriteriaController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class CriteriaControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean CriteriaQueryService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Criteria criteria() {
        Criteria c = new Criteria();
        c.setIdx(1L); c.setAaid("0012#0001"); c.setVendorids("0012"); c.setUserverification(2L); c.setKeyprotection(2L);
        c.setMatcherprotection(2L); c.setAttachmenthnumber(1L); c.setTcdisplay(1L); c.setTcdisplaycontenttype("text/plain");
        c.setAuthenticationalgorithms("1"); c.setAssertionschemes("UAFV1TLV"); c.setAttestationtypes("15879");
        c.setAuthenticatorversion(1L); c.setMetahash("hash-0001");
        c.setJsondata("{\"aaid\":\"0012#0001\",\"description\":\"Sample fingerprint\"}");
        c.setCreatetime(LocalDateTime.of(2026, 9, 1, 9, 0)); c.setUpdatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return c;
    }

    /** COMPANY_IDX 가 없는 테이블의 화면은 SUPER 전용. URL 매처가 403 으로 막는다. */
    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/criteria").with(user(companyUser))).andExpect(status().isForbidden());
        mvc.perform(get("/criteria/1").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superSeesListWithoutJsonAndWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        mvc.perform(get("/criteria").param("aaid", "0012").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("hash-0001")))
            .andExpect(content().string(not(containsString("Sample fingerprint"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/criteria/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<CriteriaSearchForm> captor = ArgumentCaptor.forClass(CriteriaSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAaid()).isEqualTo("0012");
    }

    /** 상세의 JSONDATA 는 JsonPretty 로 정리되어 "key" : "value" 형태(이스케이프된 &quot;)로 나온다. */
    @Test void detailShowsPrettyJson() throws Exception {
        when(service.get(1L)).thenReturn(criteria());
        mvc.perform(get("/criteria/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")))
            .andExpect(content().string(containsString("UAFV1TLV")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
