package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.crosscert.fidoadmin.company.service.FdsMonitorQueryService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FdsMonitorController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FdsMonitorControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FdsMonitorQueryService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private FdsMonitorRow row(long idx) {
        LocalDateTime t = LocalDateTime.of(2026, 9, 27, 10, 0, 0);
        return FdsMonitorRow.of(idx, "kbstar", "SN-0001", t, t.minusSeconds(2), t, 3);
    }

    /** 정책값으로 조회하고, 시리얼·간격·정책 기준이 보인다. IDX 는 FIDO 로그 상세로 간다. */
    @Test void listUsesPolicyTermAndRendersRows() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(eq(LocalDate.of(2026, 9, 27)), any(), eq(30), any()))
            .thenReturn(new PageImpl<>(List.of(row(5L))));

        mvc.perform(get("/fds-monitor").param("logDate", "2026-09-27").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-monitor/list"))
            .andExpect(content().string(containsString("SN-0001")))
            .andExpect(content().string(containsString("2.000")))
            .andExpect(content().string(containsString("0.000")))
            .andExpect(content().string(containsString("정책 반복 주기: 30초")))
            .andExpect(content().string(containsString("/logs/fido/2026-09-27/5")));
    }

    /** 화면 입력이 정책값보다 우선한다. 그 조회에 한해서다. */
    @Test void screenTermOverridesPolicy() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(any(), any(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/fds-monitor").param("logDate", "2026-09-27").param("term", "5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("이 조회는 5초 기준")));

        verify(service).search(eq(LocalDate.of(2026, 9, 27)), any(), eq(5), any());
    }

    /** 정책에도 입력에도 기간이 없으면 조회하지 않고 안내와 정책 화면 링크를 보여 준다. */
    @Test void noTermShowsGuidanceInsteadOfList() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.empty());

        mvc.perform(get("/fds-monitor").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("FDS 정책에 반복 주기를 설정하세요")))
            .andExpect(content().string(containsString("/fds-policies")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    @Test void nonNumericTermIsFormErrorNotServerError() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));

        mvc.perform(get("/fds-monitor").param("term", "abc").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("is-invalid")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    /** 하루치 테이블이라 86400초를 넘는 기준은 의미가 없다. */
    @Test void termAboveOneDayIsFormError() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));

        mvc.perform(get("/fds-monitor").param("term", "86401").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("is-invalid")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    /** 검색폼에 고객사 선택이 없다. 테넌트는 세션이 정한다. */
    @Test void noCompanySelectorInForm() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(any(), any(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/fds-monitor").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }
}
