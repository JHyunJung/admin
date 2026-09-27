package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FdsMonitorQueryServiceTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    CcfaFdsPolicyRepository policies = mock(CcfaFdsPolicyRepository.class);
    FidoLogTable tables = mock(FidoLogTable.class);
    FdsMonitorQueryService service =
        new FdsMonitorQueryService(jdbc, new TenantContext(new SelectedTenant()), policies, tables);

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void policy(String andTerm, String orTerm) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(1L); p.setAndTerm(andTerm); p.setOrTerm(orTerm);
        when(policies.findById(1L)).thenReturn(Optional.of(p));
    }

    // ---- 정책 해석 ----

    @Test void andTermAloneIsUsed() {
        policy("30", null);
        assertThat(service.policyTerm()).contains(30);
    }

    @Test void orTermAloneIsUsed() {
        policy(null, "45");
        assertThat(service.policyTerm()).contains(45);
    }

    /** 둘 다 있으면 짧은 쪽 — 더 민감한 기준이 결과를 더 많이 잡는다. */
    @Test void bothTermsPickTheShorter() {
        policy("60", "20");
        assertThat(service.policyTerm()).contains(20);
    }

    @Test void noPolicyRowMeansAbsent() {
        when(policies.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.policyTerm()).isEmpty();
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장하고, 운영자가 "0" 이나 "30초" 를 넣어 둘 수 있다. 전부 없음이다. */
    @Test void unparsableOrNonPositiveTermsAreAbsent() {
        assertThat(FdsMonitorQueryService.parseTerm(null)).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("   ")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("0")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("-5")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("30초")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm(" 30 ")).contains(30);
    }

    @Test void policyTermReadsTheEffectiveTenant() {
        policy("10", null);
        service.policyTerm();
        org.mockito.Mockito.verify(policies).findById(1L);
    }

    // ---- 조회 ----

    private void table(boolean exists) {
        when(tables.nameFor(any(LocalDate.class))).thenReturn("FIDO_LOGS_20260927");
        when(tables.exists("FIDO_LOGS_20260927")).thenReturn(exists);
    }

    private void rowsExist(long total) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(total);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    }

    /** 그날 로그가 없으면 테이블 자체가 없다. 오류가 아니라 빈 목록이다. */
    @Test void missingTableYieldsEmptyPage() {
        table(false);

        var page = service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /**
     * 창 함수로 직전·직후 시각을 붙이고, 테넌트 조건은 창 함수 <b>안쪽</b>에 있어야 한다.
     * 바깥에 두면 다른 고객사의 요청이 PREV/NEXT 로 섞여 들어온다.
     */
    @Test void sqlUsesWindowFunctionsWithTenantFilterInside() {
        table(true);
        rowsExist(3);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String s = sql.getValue();
        assertThat(s).contains("FIDO_LOGS_20260927")
            .contains("LAG(CREATEDTIME)").contains("LEAD(CREATEDTIME)")
            .contains("PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX")
            .contains("NUMTODSINTERVAL(:term, 'SECOND')");
        // 테넌트 조건이 LAG 보다 뒤(안쪽 WHERE)에, 창 함수 결과를 거르는 바깥 WHERE 보다 앞에 있다.
        int tenantAt = s.indexOf("COMPANY_IDX = :companyIdx");
        assertThat(tenantAt).isGreaterThan(s.indexOf("LAG(CREATEDTIME)"));
        assertThat(tenantAt).isLessThan(s.indexOf("PREV_TIME IS NOT NULL"));
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(1L);
        assertThat(params.getValue().getValue("term")).isEqualTo(30);
        assertThat(s).doesNotContain("SECOND') OR 30").doesNotContain("<= 30");
    }

    /** SERIALCODE 가 NULL 인 행끼리 한 묶음이 되어 서로 무관한 요청이 반복으로 잡히는 것을 막는다. */
    @Test void sqlExcludesNullSerialcode() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("SERIALCODE IS NOT NULL");
    }

    @Test void blankServicenameSkipsFilter() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), "   ", 30, PageRequest.of(0, 20));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("servicename")).isNull();
    }

    @Test void servicenameBecomesLikePattern() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), "kb", 30, PageRequest.of(0, 20));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("servicename")).isEqualTo("%kb%");
    }

    @Test void zeroCountSkipsRowQuery() {
        table(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        var page = service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /** 정렬은 고정이다 — Pageable 의 Sort 를 무시하므로 사용자 입력이 ORDER BY 로 갈 길이 없다. */
    @Test void orderingIsFixed() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20, Sort.by("servicename")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
            .contains("ORDER BY CREATEDTIME DESC, IDX DESC")
            .doesNotContain("ORDER BY servicename")
            .doesNotContain("servicename DESC")
            .doesNotContain("servicename ASC");
    }

    @Test void nonPositiveTermIsRejected() {
        assertThatThrownBy(() -> service.search(LocalDate.of(2026, 9, 27), null, 0, PageRequest.of(0, 20)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
