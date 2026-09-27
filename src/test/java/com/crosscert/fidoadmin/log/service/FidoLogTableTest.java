package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 테이블 이름은 SQL 에 문자열로 들어가는 유일한 값이다. 날짜 포맷 결과만 나가는지 확인한다. */
class FidoLogTableTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    FidoLogTable tables = new FidoLogTable(jdbc);

    @Test void nameIsDatePartitioned() {
        assertThat(tables.nameFor(LocalDate.of(2026, 9, 27))).isEqualTo("FIDO_LOGS_20260927");
        assertThat(tables.nameFor(LocalDate.of(2026, 1, 2))).isEqualTo("FIDO_LOGS_20260102");
    }

    @Test void missingDateIsRejected() {
        assertThatThrownBy(() -> tables.nameFor(null)).isInstanceOf(ResponseStatusException.class);
    }

    /** 연도가 네 자리를 벗어나면 이름 자릿수가 달라진다. 형태를 벗어나는 입력을 여기서 끊는다. */
    @Test void outOfRangeYearIsRejected() {
        assertThatThrownBy(() -> tables.nameFor(LocalDate.of(0, 1, 1))).isInstanceOf(ResponseStatusException.class);
    }

    /** 현재 스키마로 한정한다. 다른 계정의 동명 테이블이 보이면 남의 로그를 읽는다. */
    @Test void existenceCheckIsScopedToCurrentSchema() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(1);

        assertThat(tables.exists("FIDO_LOGS_20260927")).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Integer.class));
        assertThat(sql.getValue()).contains("ALL_TABLES").contains("SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')");
        assertThat(params.getValue().getValue("tableName")).isEqualTo("FIDO_LOGS_20260927");
    }

    @Test void zeroCountMeansAbsent() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(0);
        assertThat(tables.exists("FIDO_LOGS_19990101")).isFalse();
    }
}
