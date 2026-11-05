package com.crosscert.fidoadmin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.NoTenantSelectedException;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
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
    private final SelectedTenant selected = new SelectedTenant();
    private final StatisticsQueryService service = new StatisticsQueryService(jdbc, new TenantContext(selected));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private DashboardSearchForm form() {
        DashboardSearchForm f = new DashboardSearchForm();
        f.setFromDate(LocalDate.now().minusDays(7));
        f.setToDate(LocalDate.now());
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

        service.daily(form()); // 폼에는 고객사 칸이 없다 — 세션 테넌트만 쓴다

        assertThat(capturedCompanyIdx()).isEqualTo(1L); // 자기 것으로 강제
    }

    /** SUPER 의 필터는 더 이상 폼 파라미터가 아니라 세션에서 고른 테넌트로 걸린다. */
    @Test
    void superCanFilterByChosenCompany() {
        login(0L);
        selected.select(2L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        service.daily(form());

        assertThat(capturedCompanyIdx()).isEqualTo(2L);
    }

    /** `?fromDate=` 처럼 빈 값이 오면 필드 기본값이 null 로 덮여 NPE(500)가 났다. */
    @Test
    void blankDatesFallBackInsteadOfThrowing() {
        login(1L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        DashboardSearchForm f = form();
        f.setFromDate(null);
        f.setToDate(null);

        service.daily(f); // 예외가 나지 않아야 한다

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).query(anyString(), params.capture(), any(org.springframework.jdbc.core.RowMapper.class));
        assertThat(params.getValue().get("fromKey")).isNotNull();
        assertThat(params.getValue().get("toKey")).isNotNull();
    }

    /**
     * 예전에는 미선택 SUPER 가 전체 고객사 합산을 볼 수 있었다.
     * 이제는 유효 테넌트가 없으므로 조회 자체가 예외로 막힌다.
     */
    @Test
    void 미선택_SUPER_는_집계를_볼_수_없다() {
        login(0L);

        assertThatThrownBy(() -> service.daily(form()))
            .isInstanceOf(NoTenantSelectedException.class);
    }

    /**
     * 날짜는 로그 시간 구간인 GROUPBY('yyyy/MM/dd/HH')로 거른다. 종료일은 다음 날 0시 키 미만이다.
     * 마지막 집계일 조회도 테넌트를 걸고 GROUPBY 에서 날짜를 읽는다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void 기간은_GROUPBY_키로_거르고_마지막_집계일도_테넌트를_건다() {
        login(1L);
        when(jdbc.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());
        DashboardSearchForm f = form();
        f.setFromDate(LocalDate.of(2026, 10, 1));
        f.setToDate(LocalDate.of(2026, 10, 31));
        service.daily(f);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> dailyParams = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).query(sql.capture(), dailyParams.capture(), any(org.springframework.jdbc.core.RowMapper.class));
        assertThat(sql.getValue()).contains("GROUPBY >= :fromKey AND GROUPBY < :toKey");
        assertThat(dailyParams.getValue()).containsEntry("fromKey", "2026/10/01/00").containsEntry("toKey", "2026/11/01/00");

        when(jdbc.queryForObject(anyString(), any(Map.class), any(Class.class))).thenReturn("2026/10/15/14");
        assertThat(service.lastStatDate(f)).contains(LocalDate.of(2026, 10, 15));
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).queryForObject(anyString(), params.capture(), any(Class.class));
        assertThat(params.getValue().get("companyIdx")).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 집계가_없으면_마지막_집계일이_비어_있다() {
        login(1L);
        when(jdbc.queryForObject(anyString(), any(Map.class), any(Class.class))).thenReturn(null);
        assertThat(service.lastStatDate(form())).isEmpty();
    }
}
