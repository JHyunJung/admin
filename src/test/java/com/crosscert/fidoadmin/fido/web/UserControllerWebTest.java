package com.crosscert.fidoadmin.fido.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class UserControllerWebTest {

    static final String FULL_PUBKEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-FULL";
    static final String FULL_CERT = "MIIB-CERT-SAMPLE-FULL-VALUE-DO-NOT-SHOW";

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean UserinfoService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Userinfo userinfo(long idx) {
        Userinfo u = new Userinfo(); u.setIdx(idx); u.setCompanyIdx(1L); u.setServicename("kbstar"); u.setUserid("user001");
        u.setAaid("0012#0001"); u.setStatus("O"); u.setSigncounter(12L); u.setKeyid("keyid-001");
        u.setPubkey(FULL_PUBKEY); u.setCertificate(FULL_CERT); u.setRegtime(LocalDateTime.of(2026, 9, 1, 10, 0));
        return u;
    }

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


    @Test void listNeverExposesPubkeyOrCertificate() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(userinfo(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/users").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/list"))
            .andExpect(content().string(containsString("user001")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(not(containsString("MFkwEwYHKoZIzj0C"))))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void detailShowsOnlyFirst16CharsOfSensitiveColumns() throws Exception {
        when(service.get(1L)).thenReturn(userinfo(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/detail"))
            .andExpect(content().string(containsString("MFkwEwYHKoZIzj0C")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(containsString("MIIB-CERT-SAMPLE")))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(containsString("/users/1/status")));
    }

    /** 상세 화면은 항목을 구획 카드로 나눠 보여준다. 모든 항목은 그대로 남는다. */
    @Test void detailSplitsRowsIntoTitledSections() throws Exception {
        when(service.get(1L)).thenReturn(userinfo(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("fa-section-title")))
            .andExpect(content().string(containsString("기본 정보")))
            .andExpect(content().string(containsString("인증기기 정보")))
            .andExpect(content().string(containsString("상태 · 이력")))
            // 구획을 나눠도 항목은 하나도 사라지지 않는다
            .andExpect(content().string(containsString("USERID")))
            .andExpect(content().string(containsString("AAID")))
            .andExpect(content().string(containsString("KEYID")))
            .andExpect(content().string(containsString("서명횟수")))
            .andExpect(content().string(containsString("등록일시")))
            .andExpect(content().string(containsString("생성일시")));
    }

    /**
     * 구획 프래그먼트 정의가 본문에 그대로 한 번 더 찍히면 안 된다.
     * (th:fragment 를 단 tbody 를 main 안에 두면 카드 밖에 평문으로 중복 출력된다.)
     */
    @Test void detailRendersEachRowExactlyOnce() throws Exception {
        when(service.get(1L)).thenReturn(userinfo(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        String html = mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(countOf(html, "<th>USERID</th>")).isEqualTo(1);
        assertThat(countOf(html, "<th>AAID</th>")).isEqualTo(1);
        assertThat(countOf(html, "user001")).isEqualTo(1);
        assertThat(countOf(html, "0012#0001")).isEqualTo(1);
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    @Test void detailHasNoEditOrDeleteLinks() throws Exception {
        when(service.get(1L)).thenReturn(userinfo(1L));
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(content().string(not(containsString("/users/1/edit"))))
            .andExpect(content().string(not(containsString("/users/1/delete"))));
    }

    @Test void statusChangeRedirectsWithFlash() throws Exception {
        when(service.changeStatus(1L, "X")).thenReturn(userinfo(1L));
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "X"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashSuccess", "상태가 변경되었습니다."));
        verify(service).changeStatus(eq(1L), eq("X"));
    }

    @Test void invalidStatusRedirectsWithError() throws Exception {
        when(service.changeStatus(1L, "Z")).thenThrow(new IllegalArgumentException("허용되지 않는 상태입니다: Z"));
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "Z"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashError", "허용되지 않는 상태입니다: Z"));
    }

    @Test void statusChangeWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/users/1/status").with(user(companyUser)).param("status", "X")).andExpect(status().isForbidden());
    }

    /**
     * Task 10: 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 만으로는 상단 고객사 전환 드롭다운의 hidden 필드와 구별되지 않으므로
     * 검색폼 select 태그로 특정한다.
     */
    @Test void superUserDoesNotSeeCompanyFilterOnList() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/users").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))));
    }

    @Test void head16CutsAndPassesNull() {
        org.assertj.core.api.Assertions.assertThat(UserView.head16(FULL_PUBKEY)).isEqualTo("MFkwEwYHKoZIzj0C");
        org.assertj.core.api.Assertions.assertThat(UserView.head16("short")).isEqualTo("short");
        org.assertj.core.api.Assertions.assertThat(UserView.head16(null)).isNull();
    }
}
