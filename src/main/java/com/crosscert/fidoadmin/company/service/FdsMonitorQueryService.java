package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.company.web.FdsMonitorRow;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FDS 모니터링 — 같은 기기(SERIALCODE)의 직전·직후 요청이 N초 이내인 로그를 찾는다.
 *
 * <p>이전 어드민의 {@code fds.FDSMonitor} 를 현재 스키마로 다시 정의한 것이다. 그쪽이 보던
 * IP 대역·기기 유형·국가는 현재 FIDO_LOGS 에 컬럼이 없어 뺐고, 반복 주기만 남겼다.
 * 주체도 IP+사용자(CCFA_FIDO_TLOG, 지금은 없음)에서 SERIALCODE 로 바꿨다.
 * 설계: docs/superpowers/specs/2026-09-27-fds-monitor-design.md
 *
 * <p>{@code CrudService} 를 상속하지 않는다. 날짜별 분할 테이블이라 JPA 엔티티가 없다.
 * 테넌트 경계는 같은 규칙으로 지킨다 — 모든 조회가 {@code COMPANY_IDX = 유효 테넌트} 를 건다.
 */
@Service
@RequiredArgsConstructor
public class FdsMonitorQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final CcfaFdsPolicyRepository policies;
    private final FidoLogTable tables;

    /**
     * 현재 고객사 FDS 정책의 반복 주기(초). 없으면 비어 있다.
     *
     * <p>AND_TERM 과 OR_TERM 중 파싱되는 값을 쓰고, 둘 다 있으면 <b>짧은 쪽</b>이다.
     * 이전 어드민이 두 그룹을 나눈 이유는 IP·국가 조건과 조합하기 위해서였는데, 그 조건들이
     * 없는 지금은 두 그룹이 같은 조건 하나로 줄어 구분이 의미를 잃는다. 하나로 합친다.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> policyTerm() {
        Long companyIdx = tenant.companyIdx();
        return policies.findById(companyIdx)
            .flatMap(p -> shorter(parseTerm(p.getAndTerm()), parseTerm(p.getOrTerm())));
    }

    /** 양의 정수(초)만 값으로 본다. Oracle 은 빈 문자열을 NULL 로 저장하고 운영자가 "30초" 를 넣어 둘 수 있다. */
    static Optional<Integer> parseTerm(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<Integer> shorter(Optional<Integer> a, Optional<Integer> b) {
        if (a.isPresent() && b.isPresent()) return Optional.of(Math.min(a.get(), b.get()));
        return a.isPresent() ? a : b;
    }

    /**
     * 그 날짜에서, 같은 SERIALCODE 의 직전 또는 직후 요청이 {@code term} 초 이내인 행.
     *
     * <p>자기 조인이나 상관 서브쿼리를 쓰지 않는다. 이전 어드민은 행마다 ±N초 안의 건수를
     * 세었는데 그것은 행 수 제곱에 비례한다. 창 함수는 한 번의 스캔이다.
     *
     * <p>{@code term} 은 바인드 파라미터다. 테이블 이름 외에는 SQL 에 문자열로 들어가는 값이 없다.
     */
    @Transactional(readOnly = true)
    public Page<FdsMonitorRow> search(LocalDate date, String servicename, int term, Pageable pageable) {
        if (term <= 0) throw new IllegalArgumentException("반복 주기는 1초 이상이어야 합니다");
        Long companyIdx = tenant.companyIdx();
        String table = tables.nameFor(date);
        if (!tables.exists(table)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        var params = new MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("servicename", like(servicename))
            .addValue("term", term);

        // 테넌트·서비스명 조건은 창 함수 안쪽에 둔다. 바깥에 두면 다른 고객사(또는 다른 서비스)의
        // 요청이 PREV_TIME/NEXT_TIME 에 섞여 "같은 기기의 반복"이 아닌 것이 잡힌다.
        // SERIALCODE IS NOT NULL — NULL 끼리 한 묶음이 되어 서로 무관한 요청이 반복으로 잡히는 것을 막는다.
        String flagged = """
            SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME, PREV_TIME, NEXT_TIME, REPEATS
              FROM (
                SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME,
                       LAG(CREATEDTIME)  OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS PREV_TIME,
                       LEAD(CREATEDTIME) OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS NEXT_TIME,
                       COUNT(*)          OVER (PARTITION BY SERIALCODE)                             AS REPEATS
                  FROM %s
                 WHERE COMPANY_IDX = :companyIdx
                   AND SERIALCODE IS NOT NULL
                   AND (:servicename IS NULL OR SERVICENAME LIKE :servicename)
              )
             WHERE (PREV_TIME IS NOT NULL AND CREATEDTIME - PREV_TIME <= NUMTODSINTERVAL(:term, 'SECOND'))
                OR (NEXT_TIME IS NOT NULL AND NEXT_TIME - CREATEDTIME <= NUMTODSINTERVAL(:term, 'SECOND'))
            """.formatted(table);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + flagged + ")", params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Oracle 페이징. rownum 은 정렬 전에 매겨지므로 정렬을 끝낸 결과를 한 번 감싼다.
        params.addValue("offset", pageable.getOffset())
              .addValue("limit", pageable.getPageSize());
        String sql = "SELECT * FROM ("
            + "  SELECT b.*, rownum AS RN FROM ("
            + flagged
            + "     ORDER BY CREATEDTIME DESC, IDX DESC"
            + "  ) b WHERE rownum <= (:offset + :limit)"
            + ") WHERE RN > :offset";

        List<FdsMonitorRow> rows = jdbc.query(sql, params, (rs, i) -> FdsMonitorRow.of(
            rs.getLong("IDX"),
            rs.getString("SERVICENAME"),
            rs.getString("SERIALCODE"),
            toLocal(rs.getTimestamp("CREATEDTIME")),
            toLocal(rs.getTimestamp("PREV_TIME")),
            toLocal(rs.getTimestamp("NEXT_TIME")),
            rs.getLong("REPEATS")));

        return new PageImpl<>(rows, pageable, total);
    }

    private static java.time.LocalDateTime toLocal(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** 부분 일치 검색어. 빈 값은 null 로 바꿔 조건을 건너뛰게 한다. */
    private static String like(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim() + "%";
    }
}
