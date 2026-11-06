package com.crosscert.fidoadmin.log.service

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.server.ResponseStatusException

/** 테이블 이름은 SQL 에 문자열로 들어가는 유일한 값이다. 날짜 포맷 결과만 나가는지 확인한다. */
class FidoLogTableTest {

    val jdbc: NamedParameterJdbcTemplate = mock()
    val tables = FidoLogTable(jdbc)

    @Test fun nameIsDatePartitioned() {
        assertThat(tables.nameFor(LocalDate.of(2026, 9, 27))).isEqualTo("FIDO_LOGS_20260927")
        assertThat(tables.nameFor(LocalDate.of(2026, 1, 2))).isEqualTo("FIDO_LOGS_20260102")
    }

    @Test fun missingDateIsRejected() {
        assertThatThrownBy { tables.nameFor(null) }.isInstanceOf(ResponseStatusException::class.java)
    }

    /** 연도가 네 자리를 벗어나면 이름 자릿수가 달라진다. 형태를 벗어나는 입력을 여기서 끊는다. */
    @Test fun outOfRangeYearIsRejected() {
        assertThatThrownBy { tables.nameFor(LocalDate.of(0, 1, 1)) }.isInstanceOf(ResponseStatusException::class.java)
    }

    /** 현재 스키마로 한정한다. 다른 계정의 동명 테이블이 보이면 남의 로그를 읽는다. */
    @Test fun existenceCheckIsScopedToCurrentSchema() {
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Int::class.javaObjectType))).thenReturn(1)

        assertThat(tables.exists("FIDO_LOGS_20260927")).isTrue()

        val sql = argumentCaptor<String>()
        val params = argumentCaptor<MapSqlParameterSource>()
        verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Int::class.javaObjectType))
        assertThat(sql.firstValue).contains("ALL_TABLES").contains("SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')")
        assertThat(params.firstValue.getValue("tableName")).isEqualTo("FIDO_LOGS_20260927")
    }

    @Test fun zeroCountMeansAbsent() {
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Int::class.javaObjectType))).thenReturn(0)
        assertThat(tables.exists("FIDO_LOGS_19990101")).isFalse()
    }
}
