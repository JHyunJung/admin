package com.crosscert.fidoadmin.log.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 날짜별 분할 테이블 {@code FIDO_LOGS_yyyyMMdd} 의 이름과 존재 여부.
 *
 * <p>테이블 이름은 SQL 에 문자열로 이어 붙는 유일한 값이다. 그래서 여기가 신뢰 경계다 —
 * {@link LocalDate} 를 포맷한 결과만 내보내고 사용자 문자열은 절대 싣지 않는다.
 * 포맷 결과는 항상 숫자 8자리라 다른 것이 섞일 수 없지만, 연도가 범위를 벗어나면
 * 자릿수가 달라지므로 그것만 막는다.
 *
 * <p>일자별 테이블 이름 규칙을 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class FidoLogTable {

    private final NamedParameterJdbcTemplate jdbc;

    public String nameFor(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 조회 날짜입니다");
        }
        return "FIDO_LOGS_" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * 그 테이블이 있는가. 로그가 없는 날은 테이블 자체가 없어 그냥 조회하면 ORA-00942 가
     * 500 으로 올라온다. 빈 목록으로 보여 주려고 먼저 묻는다.
     *
     * <p>현재 스키마로 한정한다. 다른 계정의 동명 테이블이 보이면 남의 로그를 읽게 된다.
     */
    public boolean exists(String table) {
        String sql = """
            SELECT COUNT(*)
              FROM ALL_TABLES
             WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')
               AND TABLE_NAME = :tableName
            """;
        Integer found = jdbc.queryForObject(sql, new MapSqlParameterSource("tableName", table), Integer.class);
        return found != null && found > 0;
    }
}
