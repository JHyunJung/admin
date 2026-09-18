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
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.service.SignQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(controllers = SignController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class SignControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean SignQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static final String PLAINTEXT_MARKER = "PLAINTEXT-MARKER-XYZ";
    static final String SIGNATURE_MARKER = "SIGNATURE-MARKER-XYZ";

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Sign sign() {
        Sign s = new Sign();
        s.setIdx(5L); s.setCompanyIdx(1L); s.setUserid("user001"); s.setAssertion("assertion-sample-001-" + "a".repeat(40));
        s.setDn("CN=user001"); s.setPlaintext(PLAINTEXT_MARKER); s.setData("data-001"); s.setSignature(SIGNATURE_MARKER);
        s.setMemo("이체"); s.setDel("N"); s.setCreatetime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return s;
    }

    /** 목록에는 CLOB(PLAINTEXT)·서명값이 실리지 않는다. ASSERTION 은 앞 32자만. */

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

    @Test void listHidesPlaintextAndSignature() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(sign())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/signs").param("userid", "user001").param("del", "N").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/list"))
            .andExpect(content().string(containsString("CN=user001")))
            .andExpect(content().string(containsString("assertion-sample-001-aaaaaaaaaaa")))
            .andExpect(content().string(not(containsString("a".repeat(40)))))
            .andExpect(content().string(not(containsString(PLAINTEXT_MARKER))))
            .andExpect(content().string(not(containsString(SIGNATURE_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/signs/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<SignSearchForm> captor = ArgumentCaptor.forClass(SignSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getDel()).isEqualTo("N");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/signs").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    /** 상세에서만 PLAINTEXT·서명값 전체가 보인다. */
    @Test void detailShowsPlaintextAndSignature() throws Exception {
        when(service.get(5L)).thenReturn(sign());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/signs/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/detail"))
            .andExpect(content().string(containsString(PLAINTEXT_MARKER)))
            .andExpect(content().string(containsString(SIGNATURE_MARKER)))
            .andExpect(content().string(containsString("a".repeat(40))))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
