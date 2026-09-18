package com.crosscert.fidoadmin.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class DashboardControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatisticsQueryService stats;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    @Test void rendersTotalsAndSerializedRows() throws Exception {
        when(stats.groupbys()).thenReturn(List.of("day"));
        when(stats.serviceNames(any())).thenReturn(List.of("kbstar"));
        when(stats.daily(any())).thenReturn(List.of(new DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 0, 0, 10, 0, 0, 0)));
        var user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        mvc.perform(get("/").with(user(user)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")))
            .andExpect(content().string(containsString("\"date\":\"2026-09-01\"")));
    }

    /** 요약 카드는 지표마다 색 띠와 아이콘으로 구분한다. */
    @Test void summaryCardsAreColorCoded() throws Exception {
        when(stats.groupbys()).thenReturn(List.of("day"));
        when(stats.serviceNames(any())).thenReturn(List.of("kbstar"));
        when(stats.daily(any())).thenReturn(List.of(new DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 5, 1, 10, 2, 3, 1)));
        var user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        mvc.perform(get("/").with(user(user)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("fa-stat")))
            .andExpect(content().string(containsString("fa-stat-auth")))
            .andExpect(content().string(containsString("fa-stat-reg")))
            .andExpect(content().string(containsString("fa-stat-dereg")))
            .andExpect(content().string(containsString("fa-stat-tc")))
            // 카드마다 아이콘이 붙는다
            .andExpect(content().string(containsString("bi bi-shield-check")))
            // 네 지표의 수치는 그대로 남는다
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")));
    }
}
