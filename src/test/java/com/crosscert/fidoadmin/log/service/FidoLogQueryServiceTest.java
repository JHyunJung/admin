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
import com.crosscert.fidoadmin.log.web.FidoLogRow;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

        var page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20)).page();

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

    /** 총 건수가 0이면 목록 조회로 나가지 않는다. */
    @Test void zeroCountSkipsRowQuery() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        var page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20)).page();

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

    private static FidoLogRow row(long idx, String op, String servicename, String userid) {
        return new FidoLogRow(idx, 1L, op, servicename, userid, 2L, LocalDateTime.of(2026, 9, 26, 10, 0));
    }

    private static String base64url(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 조건이 없으면 DB 가 한 페이지만 읽는다. 행마다 JSONDATA 를 풀어 구분·서비스명·사용자·인증장치를 채운다. */
    @SuppressWarnings("unchecked")
    @Test void withoutFiltersPagesInDbAndDecodesEachRow() throws Exception {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), mapper.capture());
        assertThat(sql.getValue()).contains("JSONDATA").contains("RN > :offset").doesNotContain("JSON_VALUE");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("IDX")).thenReturn(7L);
        when(rs.getLong("COMPANY_IDX")).thenReturn(1L);
        when(rs.getString("SERVICENAME")).thenReturn("SERVICE");
        when(rs.getTimestamp("CREATEDTIME")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 2, 14, 42, 20)));
        when(rs.getString("JSONDATA")).thenReturn(base64url(
            "{\"transaction\":{\"serviceName\":\"com.kbstar.kbbank\",\"userName\":\"u1\",\"op\":\"TC\",\"bioType\":2}}"));
        FidoLogRow r = (FidoLogRow) mapper.getValue().mapRow(rs, 0);

        assertThat(r.op()).isEqualTo("TC");
        assertThat(r.servicename()).isEqualTo("com.kbstar.kbbank");
        assertThat(r.userid()).isEqualTo("u1");
        assertThat(r.bioTypeLabel()).isEqualTo("지문");
    }

    /** JSON 에 서비스명이 없으면 SERVICENAME 컬럼 값을 쓴다. */
    @SuppressWarnings("unchecked")
    @Test void servicenameFallsBackToColumn() throws Exception {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20));
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(anyString(), any(MapSqlParameterSource.class), mapper.capture());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("SERVICENAME")).thenReturn("kbstar");
        when(rs.getString("JSONDATA")).thenReturn("garbage!!");
        FidoLogRow r = (FidoLogRow) mapper.getValue().mapRow(rs, 0);

        assertThat(r.servicename()).isEqualTo("kbstar");
        assertThat(r.op()).isNull();
        assertThat(r.bioTypeLabel()).isEqualTo("알수없음");
    }

    /** 공백뿐인 검색어는 조건이 아니다 — 하루치를 훑지 않고 DB 페이징으로 간다. */
    @SuppressWarnings("unchecked")
    @Test void blankTermsDoNotTriggerScan() {
        tableExists(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        FidoLogSearchForm f = form(LocalDate.of(2026, 9, 26));
        f.setServicename("  "); f.setUserid(" "); f.setOp("bogus");

        service.search(f, PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("RN > :offset").doesNotContain(":cap");
    }

    /** 조건이 있으면 그날 그 고객사 로그를 풀어 Java 에서 거르고 페이지를 나눈다. */
    @SuppressWarnings("unchecked")
    @Test void filtersScanTheDayAndMatchInJava() {
        tableExists(true);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(
            row(5, "TC", "com.kbstar.kbbank", "f5JyUa2Q"),
            row(4, "Auth", "com.kbstar.kbbank", "f5JyUa2Q"),
            row(3, "TC", "com.kbstar.kbbiz", "113057331000001_0_*com.kbstar.kbbiz_*KF"),
            row(2, "tc", "com.kbstar.kbbank", "F5JYUA2Q-other"),
            row(1, null, null, null)));
        FidoLogSearchForm f = form(LocalDate.of(2026, 9, 26));
        f.setOp("TC"); f.setServicename("KBBANK"); f.setUserid("f5jyua2q");

        var result = service.search(f, PageRequest.of(0, 1));

        assertThat(result.truncated()).isFalse();
        assertThat(result.page().getTotalElements()).isEqualTo(2);
        assertThat(result.page().getContent()).extracting(FidoLogRow::idx).containsExactly(5L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("FIDO_LOGS_20260926").contains("COMPANY_IDX = :companyIdx")
            .contains("rownum <= :cap").contains("ORDER BY CREATEDTIME DESC, IDX DESC");
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(1L);
        assertThat(params.getValue().getValue("cap")).isEqualTo(FidoLogQueryService.SCAN_LIMIT + 1);
        verify(jdbc, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /** 하루 로그가 상한을 넘으면 앞부분만 보고, 그 사실을 화면에 알린다. */
    @SuppressWarnings("unchecked")
    @Test void scanStopsAtLimitAndReportsTruncation() {
        tableExists(true);
        List<FidoLogRow> many = new ArrayList<>();
        for (int i = 0; i < FidoLogQueryService.SCAN_LIMIT + 1; i++) many.add(row(i, "Auth", "s", "u"));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(many);
        FidoLogSearchForm f = form(LocalDate.of(2026, 9, 26));
        f.setOp("Auth");

        var result = service.search(f, PageRequest.of(0, 20));

        assertThat(result.truncated()).isTrue();
        assertThat(result.page().getTotalElements()).isEqualTo(FidoLogQueryService.SCAN_LIMIT);
    }
}
