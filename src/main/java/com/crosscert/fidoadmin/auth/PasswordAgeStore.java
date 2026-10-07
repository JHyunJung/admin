package com.crosscert.fidoadmin.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * CCFA_MANAGER.LAST_PW_CHANGE_DATE 를 다루는 유일한 곳.
 *
 * <p>이 열은 운영 DB 에만 있고 스키마 정의에는 없다. JPA 엔티티에 매핑하면 열이 없는 DB 에서
 * CCFA_MANAGER 조회가 모두 ORA-00904 로 실패해 로그인부터 막히므로, JDBC 로만 읽고 쓴다.
 */
@Component
@RequiredArgsConstructor
public class PasswordAgeStore {

    private final JdbcTemplate jdbc;

    /**
     * 열이 있는가. 시노님으로 다른 스키마의 테이블을 보는 경우에도 맞도록 딕셔너리 대신 직접 조회해 본다.
     * 열이 없을 때({@link BadSqlGrammarException})만 false 이고, 연결 실패 같은 다른 DataAccessException 은
     * 열이 없다는 뜻이 아니므로 그대로 던진다.
     */
    public boolean columnExists() {
        try {
            jdbc.queryForList("SELECT LAST_PW_CHANGE_DATE FROM CCFA_MANAGER WHERE 1=0");
            return true;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }

    public Optional<LocalDateTime> lastChanged(String userId) {
        List<Timestamp> rows = jdbc.queryForList(
            "SELECT LAST_PW_CHANGE_DATE FROM CCFA_MANAGER WHERE USER_ID = ?", Timestamp.class, userId);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0).toLocalDateTime());
    }

    public void touch(String userId) {
        jdbc.update("UPDATE CCFA_MANAGER SET LAST_PW_CHANGE_DATE = SYSTIMESTAMP WHERE USER_ID = ?", userId);
    }
}
