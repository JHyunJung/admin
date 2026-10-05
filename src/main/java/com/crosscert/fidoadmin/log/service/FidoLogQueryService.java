package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.log.web.FidoLogRow;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import com.crosscert.fidoadmin.log.web.FidoLogSearchResult;
import com.crosscert.fidoadmin.log.web.FidoLogView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FIDO 로그 조회. 읽기 전용이다(append-only 로그라 화면에서 고치거나 지우지 않는다).
 *
 * <p>FIDO_LOGS 는 날짜별로 테이블이 갈린다 — {@code FIDO_LOGS_20260926} 식이다.
 * 테이블 이름이 조회할 때 정해지므로 JPA 엔티티로는 다룰 수 없고, 여기서 JDBC 로 읽는다.
 * 이름을 SQL 에 이어 붙이는 유일한 자리라, {@link FidoLogTable} 이 날짜를 포맷해 만든
 * 이름만 쓰고 사용자 문자열은 절대 싣지 않는다.
 *
 * <p>{@code CrudService} 를 상속하지 않는다. 그쪽은 고정된 한 테이블을 JPA 로 다루는
 * 기반이다. 대신 테넌트 경계는 같은 규칙으로 지킨다 — 모든 조회가
 * {@code COMPANY_IDX = 유효 테넌트} 를 건다.
 */
@Service
@RequiredArgsConstructor
public class FidoLogQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final FidoLogTable tables;

    /**
     * 조건 검색이 한 번에 풀어 보는 최대 행 수. 하루 로그가 이보다 많으면 최근 것부터 이만큼만 보고
     * 화면에 알린다. 행마다 JSONDATA 를 풀지만 목록 행(작은 값 몇 개)만 남기므로 메모리는 행 수에 비례한다.
     */
    public static final int SCAN_LIMIT = 50_000;

    private static final Set<String> OPS = Set.of("reg", "auth", "dereg", "tc");

    /**
     * 목록. 없는 날짜면 빈 페이지다(오류가 아니다 — 그날 로그가 없다는 뜻이다).
     *
     * <p>운영 JSONDATA 는 base64url 이라 DB 가 안을 볼 수 없다. 조건이 없으면 DB 가 한 페이지만 읽고
     * 그 행만 푼다. 구분·서비스명·사용자·상태 조건이 하나라도 있으면 그날 그 고객사 로그를 최근 것부터
     * {@link #SCAN_LIMIT} 건까지 풀어 Java 에서 거른 뒤 페이지를 나눈다.
     */
    @Transactional(readOnly = true)
    public FidoLogSearchResult search(FidoLogSearchForm form, Pageable pageable) {
        Long companyIdx = tenant.companyIdx();
        String table = tables.nameFor(form.getLogDate());
        if (!tables.exists(table)) {
            return new FidoLogSearchResult(new PageImpl<>(List.of(), pageable, 0), false);
        }
        var params = new MapSqlParameterSource("companyIdx", companyIdx);
        // 텍스트 블록을 쓰지 않는다 — 조각을 이어 붙이면 블록마다 들여쓰기가 따로 벗겨져
        // 앞뒤 공백이 사라진다(FROM 테이블명WHERE 처럼 붙어 ORA-03048 이 났다).
        String select = "SELECT IDX, COMPANY_IDX, SERVICENAME, CREATEDTIME, JSONDATA FROM " + table
            + " WHERE COMPANY_IDX = :companyIdx ORDER BY CREATEDTIME DESC, IDX DESC";

        String op = opOrNull(form.getOp());
        String servicename = termOrNull(form.getServicename());
        String userid = termOrNull(form.getUserid());
        String status = termOrNull(form.getStatus());
        if (op == null && servicename == null && userid == null && status == null) {
            Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE COMPANY_IDX = :companyIdx", params, Long.class);
            if (total == null || total == 0) {
                return new FidoLogSearchResult(new PageImpl<>(List.of(), pageable, 0), false);
            }
            // Oracle 페이징. rownum 은 정렬 전에 매겨지므로 정렬을 끝낸 결과를 한 번 감싼다.
            params.addValue("offset", pageable.getOffset()).addValue("limit", pageable.getPageSize());
            String sql = "SELECT * FROM (SELECT b.*, rownum AS RN FROM (" + select + ") b"
                + " WHERE rownum <= (:offset + :limit)) WHERE RN > :offset";
            List<FidoLogRow> rows = jdbc.query(sql, params, (rs, i) -> toRow(rs));
            return new FidoLogSearchResult(new PageImpl<>(rows, pageable, total), false);
        }

        params.addValue("cap", SCAN_LIMIT + 1);
        List<FidoLogRow> scanned = jdbc.query(
            "SELECT * FROM (" + select + ") WHERE rownum <= :cap", params, (rs, i) -> toRow(rs));
        boolean truncated = scanned.size() > SCAN_LIMIT;
        List<FidoLogRow> matched = (truncated ? scanned.subList(0, SCAN_LIMIT) : scanned).stream()
            .filter(r -> op == null || op.equalsIgnoreCase(r.op()))
            .filter(r -> servicename == null || containsIgnoreCase(r.servicename(), servicename))
            .filter(r -> userid == null || containsIgnoreCase(r.userid(), userid))
            .filter(r -> status == null || status.equalsIgnoreCase(r.status()))
            .toList();
        int from = (int) Math.min(pageable.getOffset(), matched.size());
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        return new FidoLogSearchResult(new PageImpl<>(matched.subList(from, to), pageable, matched.size()), truncated);
    }

    /**
     * 상세 한 건. 다른 고객사의 행은 {@code COMPANY_IDX} 조건에서 걸러져 404 가 된다
     * (있는데 못 본다는 사실조차 알리지 않는다).
     */
    @Transactional(readOnly = true)
    public FidoLogView get(LocalDate date, Long id) {
        Long companyIdx = tenant.companyIdx();
        String table = tables.nameFor(date);
        if (!tables.exists(table)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FIDO 로그 " + id);
        }

        String sql = "SELECT IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, CREATEDTIME, JSONDATA "
            + "FROM " + table + " "
            + "WHERE COMPANY_IDX = :companyIdx "
            + "AND IDX = :id";

        var params = new MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("id", id);

        List<FidoLogView> found = jdbc.query(sql, params, (rs, i) -> FidoLogView.of(
            rs.getLong("IDX"),
            rs.getLong("COMPANY_IDX"),
            rs.getString("SERIALCODE"),
            rs.getString("SERVICENAME"),
            createdtime(rs),
            rs.getString("JSONDATA")));

        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FIDO 로그 " + id);
        }
        return found.get(0);
    }

    private static LocalDateTime createdtime(ResultSet rs) throws SQLException {
        return rs.getTimestamp("CREATEDTIME") == null ? null : rs.getTimestamp("CREATEDTIME").toLocalDateTime();
    }

    private static FidoLogRow toRow(ResultSet rs) throws SQLException {
        return FidoLogRow.of(rs.getLong("IDX"), rs.getLong("COMPANY_IDX"), rs.getString("SERVICENAME"),
            createdtime(rs), FidoLogPayload.parse(rs.getString("JSONDATA")));
    }

    /** 아는 구분만 조건이 된다. 그 밖(빈 값 포함)은 조건 없음. */
    private static String opOrNull(String op) {
        return op != null && OPS.contains(op.trim().toLowerCase(Locale.ROOT)) ? op.trim() : null;
    }

    private static String termOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean containsIgnoreCase(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }
}
