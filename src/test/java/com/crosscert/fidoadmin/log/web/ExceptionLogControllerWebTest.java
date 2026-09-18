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
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService;
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

@WebMvcTest(controllers = ExceptionLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class ExceptionLogControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean ExceptionLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaExceptions row(long idx) {
        CcfaExceptions e = new CcfaExceptions();
        e.setIdx(idx); e.setCompanyIdx(1L); e.setEType("AUTH"); e.setELevel("ERROR");
        e.setExceptionMessage("Invalid signature"); e.setExceptionDetailMessage("DETAIL_ONLY_IN_DETAIL");
        e.setExceptionData("CLOB_ONLY_IN_DETAIL"); e.setCreatedtime("2026-09-15 10:00:00");
        return e;
    }

    /** 검색 파라미터(문자열 일시 포함)가 폼에 바인딩되고, CLOB·상세 메시지는 목록에 나오지 않는다. */

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

    @Test void listBindsStringCreatedtimeAndHidesLongColumns() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(row(1L))));
        mvc.perform(get("/logs/exceptions").param("eType", "AUTH").param("eLevel", "ERROR")
                .param("message", "signature").param("createdtime", "2026-09-15").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/list"))
            .andExpect(content().string(containsString("Invalid signature")))
            .andExpect(content().string(not(containsString("DETAIL_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("CLOB_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("name=\"fromDate\""))));
        ArgumentCaptor<ExceptionLogSearchForm> captor = ArgumentCaptor.forClass(ExceptionLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        ExceptionLogSearchForm f = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(f.getEType()).isEqualTo("AUTH");
        org.assertj.core.api.Assertions.assertThat(f.getELevel()).isEqualTo("ERROR");
        org.assertj.core.api.Assertions.assertThat(f.getMessage()).isEqualTo("signature");
        org.assertj.core.api.Assertions.assertThat(f.getCreatedtime()).isEqualTo("2026-09-15");
    }

    @Test void superSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/exceptions").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsAllColumns() throws Exception {
        when(service.get(1L)).thenReturn(row(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/exceptions/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/detail"))
            .andExpect(content().string(containsString("DETAIL_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("CLOB_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("2026-09-15 10:00:00")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
