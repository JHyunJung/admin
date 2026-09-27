package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.log.web.FidoLogRow;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import com.crosscert.fidoadmin.log.web.FidoLogView;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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


    /** 목록. 없는 날짜면 빈 페이지다(오류가 아니다 — 그날 로그가 없다는 뜻이다). */
    @Transactional(readOnly = true)
    public Page<FidoLogRow> search(FidoLogSearchForm form, Pageable pageable) {
        Long companyIdx = tenant.companyIdx();
        String table = tables.nameFor(form.getLogDate());
        if (!tables.exists(table)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        var params = new MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("servicename", like(form.getServicename()))
            .addValue("serialcode", like(form.getSerialcode()));

        String where = """
             WHERE COMPANY_IDX = :companyIdx
               AND (:servicename IS NULL OR SERVICENAME LIKE :servicename)
               AND (:serialcode IS NULL OR SERIALCODE LIKE :serialcode)
            """;

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Oracle 페이징. rownum 은 정렬 전에 매겨지므로 정렬을 끝낸 결과를 한 번 감싼다.
        params.addValue("offset", pageable.getOffset())
              .addValue("limit", pageable.getPageSize());
        String sql = "SELECT * FROM ("
            + "  SELECT b.*, rownum AS RN FROM ("
            + "    SELECT IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, CREATEDTIME"
            + "      FROM " + table + where
            + "     ORDER BY CREATEDTIME DESC, IDX DESC"
            + "  ) b WHERE rownum <= (:offset + :limit)"
            + ") WHERE RN > :offset";

        List<FidoLogRow> rows = jdbc.query(sql, params, (rs, i) -> new FidoLogRow(
            rs.getLong("IDX"),
            rs.getLong("COMPANY_IDX"),
            rs.getString("SERIALCODE"),
            rs.getString("SERVICENAME"),
            rs.getTimestamp("CREATEDTIME") == null ? null : rs.getTimestamp("CREATEDTIME").toLocalDateTime()));

        return new PageImpl<>(rows, pageable, total);
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

        String sql = "SELECT IDX, COMPANY_IDX, SERIALCODE, "
            + "SERVICENAME, CREATEDTIME, JSONDATA "
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
            rs.getTimestamp("CREATEDTIME") == null ? null : rs.getTimestamp("CREATEDTIME").toLocalDateTime(),
            rs.getString("JSONDATA")));

        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FIDO 로그 " + id);
        }
        return found.get(0);
    }

    /** 부분 일치 검색어. 빈 값은 null 로 바꿔 조건을 건너뛰게 한다. */
    private static String like(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim() + "%";
    }
}
