package com.crosscert.fidoadmin.fido.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException;
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataParser;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = CriteriaController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class CriteriaControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean CriteriaQueryService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    /**
     * /criteria 는 TENANT 영역이라 미선택 SUPER 는 TenantSelectionInterceptor 가
     * /select-tenant 로 돌려보낸다. SUPER 요청에는 미리 테넌트를 선택해 둔다.
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

    /**
     * 화면의 용도가 고객사별 AAID 토글이라 COMPANY 역할도 연다.
     * 예전에는 SUPER 전용이라 403 이었다 — 그 제약이 풀렸음을 못 박는다.
     */
    @Test void companyRoleCanView() throws Exception {
        when(service.disabledAaids()).thenReturn(java.util.Set.of());
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        when(service.get(1L)).thenReturn(criteria());

        mvc.perform(get("/criteria").with(user(companyUser))).andExpect(status().isOk());
        mvc.perform(get("/criteria/1").with(user(companyUser))).andExpect(status().isOk());
    }

    @Test void superSeesListWithoutJsonAndWithoutCompanyFilter() throws Exception {
        when(service.disabledAaids()).thenReturn(java.util.Set.of());
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        mvc.perform(get("/criteria").param("aaid", "0012").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("hash-0001")))
            .andExpect(content().string(not(containsString("Sample fingerprint"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(containsString("/criteria/new")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<CriteriaSearchForm> captor = ArgumentCaptor.forClass(CriteriaSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAaid()).isEqualTo("0012");
    }

    /** 상세의 JSONDATA 는 JsonPretty 로 정리되어 "key" : "value" 형태(이스케이프된 &quot;)로 나온다. */
    @Test void detailShowsPrettyJson() throws Exception {
        when(service.get(1L)).thenReturn(criteria());
        mvc.perform(get("/criteria/1").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")))
            .andExpect(content().string(containsString("UAFV1TLV")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }

    /** 전체 토글 후 검색·페이지 상태를 유지한 채 목록으로 돌아간다. */
    @Test void bulkStatusRedirectsBackWithSearchStateAndMessage() throws Exception {
        when(service.changeStatusAll(false)).thenReturn(4);

        mvc.perform(post("/criteria/status").with(user(companyUser)).with(csrf())
                .param("enabled", "false").param("aaid", "0012").param("page", "1").param("size", "20"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/criteria?aaid=0012&page=1&size=20"))
            .andExpect(flash().attribute("statusMessage", "전체 비활성: 4건을 변경했습니다."));

        verify(service).changeStatusAll(false);
    }

    @Test void companyDoesNotSeeCreateButton() throws Exception {
        when(service.disabledAaids()).thenReturn(java.util.Set.of());
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        mvc.perform(get("/criteria").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/criteria/new"))));
    }

    @Test void superOpensCreateForm() throws Exception {
        mvc.perform(get("/criteria/new").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString("name=\"jsondata\"")));
    }

    @Test void companyCannotOpenCreateForm() throws Exception {
        mvc.perform(get("/criteria/new").with(user(companyUser)))
            .andExpect(status().isForbidden());
    }

    @Test void companyCannotPostCreate() throws Exception {
        mvc.perform(post("/criteria").with(user(companyUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().isForbidden());
        verify(service, never()).create(anyString());
    }

    @Test void superCreateRedirectsToDetail() throws Exception {
        Criteria saved = criteria();
        saved.setIdx(30L);
        when(service.create("{\"aaid\":\"0012#0009\"}")).thenReturn(saved);
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/criteria/30"))
            .andExpect(flash().attribute("flashSuccess", "등록되었습니다. 모든 고객사에서 비활성 상태로 시작합니다."));
    }

    @Test void parseErrorShowsFormWithMessageAndKeepsInput() throws Exception {
        when(service.create("{bad")).thenThrow(new CriteriaMetadataException(CriteriaMetadataParser.INVALID));
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{bad"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString(CriteriaMetadataParser.INVALID)))
            .andExpect(content().string(containsString("{bad")));
    }

    @Test void blankJsonShowsFormAgain() throws Exception {
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "   "))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"));
        verify(service, never()).create(anyString());
    }
}
