package com.crosscert.fidoadmin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 대시보드 집계의 테넌트 격리. COMPANY 역할은 쿼리스트링으로 다른 고객사를 지정해도
 * 자기 COMPANY_IDX 로 강제돼야 한다. 이 경로는 화면에서 가장 민감한 집계다.
 */
class StatisticsTenantScopeTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final StatisticsQueryService service = new StatisticsQueryService(jdbc);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private DashboardSearchForm form(Long requestedCompanyIdx) {
        DashboardSearchForm f = new DashboardSearchForm();
        f.setGroupby("day");
        f.setFromDate(LocalDate.now().minusDays(7));
        f.setToDate(LocalDate.now());
        f.setCompanyIdx(requestedCompanyIdx);
        return f;
    }

    @SuppressWarnings("unchecked")
    private Long capturedCompanyIdx() {
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).query(anyString(), params.capture(), any(org.springframework.jdbc.core.RowMapper.class));
        return (Long) params.getValue().get("companyIdx");
    }

    @Test
    void companyRoleCannotWidenScopeViaQueryString() {
        login(1L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        service.daily(form(2L)); // 다른 고객사를 요청해도

        assertThat(capturedCompanyIdx()).isEqualTo(1L); // 자기 것으로 강제
    }

    @Test
    void superCanFilterByChosenCompany() {
        login(0L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        service.daily(form(2L));

        assertThat(capturedCompanyIdx()).isEqualTo(2L);
    }

    @Test
    void superWithoutChoiceSeesAllCompanies() {
        login(0L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        service.daily(form(null));

        assertThat(capturedCompanyIdx()).isNull(); // SQL 의 :companyIdx IS NULL 분기
    }
}
