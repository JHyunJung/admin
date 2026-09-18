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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = FidoLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FidoLogControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean FidoLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private FidoLogs log(long idx, String json) {
        FidoLogs l = new FidoLogs();
        l.setIdx(idx); l.setCompanyIdx(1L); l.setSerialcode("SN-0001"); l.setServicename("kbstar");
        l.setJsondata(json); l.setCreatedtime(LocalDateTime.of(2026, 9, 16, 10, 0));
        return l;
    }

    /** COMPANY 역할: 고객사 select 가 없고, CLOB(JSONDATA) 은 목록에 나오지 않는다. */

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

    @Test void companyListHidesCompanyFilterAndClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(log(5L, "MARKER_JSON_ONLY_IN_DETAIL"))));
        mvc.perform(get("/logs/fido").param("servicename", "kbstar").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/list"))
            .andExpect(content().string(containsString("SN-0001")))
            .andExpect(content().string(not(containsString("MARKER_JSON_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        ArgumentCaptor<FidoLogSearchForm> captor = ArgumentCaptor.forClass(FidoLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getServicename()).isEqualTo("kbstar");
    }

    /**
     * Task 10: 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 만으로는 상단 고객사 전환 드롭다운의 hidden 필드와 구별되지 않으므로
     * 검색폼 select 태그로 특정한다.
     */
    @Test void superListDoesNotShowCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/fido").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }

    /**
     * 상세는 JSONDATA 를 정리해 보여준다. th:text 는 따옴표를 &quot; 로 이스케이프하므로
     * 정리된 형태 `"op" : "Auth"` 는 HTML 에서 `&quot;op&quot; : &quot;Auth&quot;` 로 나타난다.
     */
    @Test void detailPrettyPrintsJson() throws Exception {
        when(service.get(5L)).thenReturn(log(5L, "{\"op\":\"Auth\",\"result\":\"1200\"}"));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/fido/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/detail"))
            .andExpect(content().string(containsString("&quot;op&quot; : &quot;Auth&quot;")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
