package com.crosscert.fidoadmin.log.service;

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
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import java.time.LocalDate;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * FIDO 로그는 날짜별로 테이블이 갈린다. 테이블 이름이 SQL 에 문자열로 들어가는 유일한
 * 자리이므로, 그 이름이 어떻게 만들어지는지와 테넌트 조건이 빠지지 않는지를 확인한다.
 */
class FidoLogQueryServiceTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    FidoLogQueryService service = new FidoLogQueryService(jdbc, new TenantContext(new SelectedTenant()), new FidoLogTable(jdbc));

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    /** 테이블이 있다고 답하게 한다. */
    private void tableExists(boolean exists) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
            .thenReturn(exists ? 1 : 0);
    }

    private FidoLogSearchForm form(LocalDate date) {
        FidoLogSearchForm f = new FidoLogSearchForm();
        f.setLogDate(date);
        return f;
    }

    /** 그날 로그가 없으면 테이블 자체가 없다. 오류가 아니라 빈 목록이다. */
    @Test void missingTableYieldsEmptyPageInsteadOfError() {
        tableExists(false);

        var page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
        // 존재 확인 외에 목록 조회로 나가지 않는다.
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /** 목록은 그 날짜의 테이블을 읽고, 유효 테넌트 조건을 반드시 건다. */
    @Test void searchTargetsDateTableAndFiltersByTenant() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(3L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(java.util.List.of());

        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertThat(sql.getValue()).contains("FIDO_LOGS_20260926");
        assertThat(sql.getValue()).contains("COMPANY_IDX = :companyIdx");
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(1L);
    }

    /** 검색어가 비어 있으면 조건을 건너뛴다(빈 문자열 LIKE '%%' 로 전부 훑지 않는다). */
    @Test void blankSearchTermsBecomeNull() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(java.util.List.of());

        FidoLogSearchForm f = form(LocalDate.of(2026, 9, 26));
        f.setServicename("  ");
        f.setSerialcode("SN-1");
        service.search(f, PageRequest.of(0, 20));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("servicename")).isNull();
        assertThat(params.getValue().getValue("serialcode")).isEqualTo("%SN-1%");
    }

    /** 총 건수가 0이면 목록 조회로 나가지 않는다. */
    @Test void zeroCountSkipsRowQuery() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        var page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /** 상세도 유효 테넌트를 건다. 다른 고객사의 행은 조회되지 않아 404 가 된다. */
    @Test void detailFiltersByTenantAndIsNotFoundWhenAbsent() {
        tableExists(true);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.get(LocalDate.of(2026, 9, 26), 5L))
            .isInstanceOf(ResponseStatusException.class);

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(1L);
    }

    /** 없는 날짜의 상세는 404 다(500 이 아니다). */
    @Test void detailOnMissingTableIsNotFound() {
        tableExists(false);

        assertThatThrownBy(() -> service.get(LocalDate.of(2026, 9, 26), 5L))
            .isInstanceOf(ResponseStatusException.class);

        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /** 정렬은 고정이다 — 사용자 입력이 ORDER BY 로 들어갈 경로가 없다. */
    @Test void orderingIsFixedToCreatedtimeDesc() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(java.util.List.of());

        service.search(form(LocalDate.of(2026, 9, 26)),
            PageRequest.of(0, 20, Sort.by("servicename")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("ORDER BY CREATEDTIME DESC, IDX DESC");
        assertThat(sql.getValue()).doesNotContain("servicename DESC");
    }
}
