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
        when(stats.serviceNames()).thenReturn(List.of("kbstar"));
        when(stats.daily(any())).thenReturn(List.of(new DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 0, 0, 10, 0, 0, 0)));
        var user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        mvc.perform(get("/").with(user(user)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")))
            .andExpect(content().string(containsString("\"date\":\"2026-09-01\"")));
    }

    /**
     * 필터는 조회 버튼 없이 바뀌는 즉시 조회한다(admin.js 의 data-auto-submit).
     * 자동 제출 동작 자체는 브라우저 몫이라 여기서는 표식이 붙어 있는지만 고정한다 —
     * 표식이 빠지면 화면은 멀쩡한데 날짜를 바꿔도 아무 일이 없는 상태로 돌아간다.
     *
     * <p>조회 버튼은 함께 남는다. 스크립트가 로드되지 않는 환경의 유일한 조회 수단이다.
     */
    @Test void 필터는_자동_조회_표식을_가진다() throws Exception {
        when(stats.groupbys()).thenReturn(List.of("day"));
        when(stats.serviceNames()).thenReturn(List.of("kbstar"));
        when(stats.daily(any())).thenReturn(List.of());
        var user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        mvc.perform(get("/").with(user(user)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-auto-submit")))
            .andExpect(content().string(containsString("조회")));
    }

    /** 요약 카드는 지표마다 색 띠와 아이콘으로 구분한다. */
    @Test void summaryCardsAreColorCoded() throws Exception {
        when(stats.groupbys()).thenReturn(List.of("day"));
        when(stats.serviceNames()).thenReturn(List.of("kbstar"));
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
