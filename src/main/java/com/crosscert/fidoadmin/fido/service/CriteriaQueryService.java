package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRITERIA(AAID 정책) 조회와 고객사별 활성/비활성 토글.
 *
 * <p>CRITERIA 자체는 COMPANY_IDX 가 없는 전역 기준 데이터라 companyIdxAttribute() 는
 * null 이다. 그래도 SUPER 전용이 아니다 — 이 화면의 실제 용도가 고객사별 토글이고,
 * 그 상태는 CCFA_COMPANY_AAID 에 고객사별로 들어간다. 기반의
 * requireSuperForGlobalTable() 을 재정의해 로그인 확인까지만 하고,
 * 고객사 경계는 {@link #disabledAaids()} 와 {@link #changeStatus(Long, boolean)} 이
 * 유효 테넌트로 직접 건다.
 */
@Service
public class CriteriaQueryService extends CrudService<Criteria, Long, CriteriaSearchForm> {

    private final NamedParameterJdbcTemplate jdbc;

    public CriteriaQueryService(CriteriaRepository repository, AuditLogger audit,
                                TenantContext tenant, NamedParameterJdbcTemplate jdbc) {
        super(repository, audit, tenant);
        this.jdbc = jdbc;
    }

    /**
     * CRITERIA 자체는 전역 기준 데이터지만, 이 화면의 쓸모는 고객사별 활성/비활성
     * 토글에 있다(CCFA_COMPANY_AAID). 그래서 기반의 SUPER 전용 차단을 풀고
     * 로그인 여부만 확인한다 — 고객사 경계는 아래 두 메서드가 유효 테넌트로 건다.
     */
    @Override
    protected void requireSuperForGlobalTable() {
        tenant.require();
    }

    /**
     * 현재 고객사에서 <b>꺼 둔</b> AAID 집합.
     *
     * <p>CCFA_COMPANY_AAID 는 차단 목록이다 — 행이 있으면 비활성이다.
     * 레거시 매퍼의 disableCompanyAAID 가 insert, enableCompanyAAID 가 delete 인
     * 것과 같은 규약이다(docs/legacy/mybatis-mappers.md 참고).
     */
    @Transactional(readOnly = true)
    public Set<String> disabledAaids() {
        Long companyIdx = tenant.companyIdx();
        String sql = """
            SELECT DISTINCT AAID
              FROM CCFA_COMPANY_AAID
             WHERE COMPANY_IDX = :companyIdx
               AND AAID IS NOT NULL
            """;
        var params = new MapSqlParameterSource("companyIdx", companyIdx);
        return new HashSet<>(jdbc.queryForList(sql, params, String.class));
    }

    /**
     * 현재 고객사에서 AAID 를 켜거나 끈다. 이미 그 상태면 {@code false}.
     *
     * <p>JPA 가 아니라 JDBC 인 이유: CCFA_COMPANY_AAID 는 (COMPANY_IDX, AAID)
     * 복합 키에 다른 컬럼이 없는 순수 연결 테이블이라 엔티티로 둘 이득이 없다.
     */
    @Transactional
    public boolean changeStatus(Long id, boolean enabled) {
        Long companyIdx = tenant.companyIdx();
        Criteria criteria = get(id);
        String aaid = criteria.getAaid();
        if (aaid == null || aaid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AAID가 없는 정책입니다");
        }
        var params = new MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("aaid", aaid);

        // 같은 고객사의 상태 변경을 직렬화한다. 이 테이블에는 유니크 제약이 없어
        // 동시 요청이 들어오면 같은 (COMPANY_IDX, AAID) 행이 두 번 들어갈 수 있다.
        jdbc.queryForObject("""
            SELECT IDX FROM CCFA_COMPANY
             WHERE IDX = :companyIdx
               FOR UPDATE
            """, params, Long.class);

        int changed;
        if (enabled) {
            changed = jdbc.update("""
                DELETE FROM CCFA_COMPANY_AAID
                 WHERE COMPANY_IDX = :companyIdx
                   AND AAID = :aaid
                """, params);
        } else {
            changed = jdbc.update("""
                INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
                SELECT :companyIdx, :aaid FROM DUAL
                 WHERE NOT EXISTS (
                     SELECT 1 FROM CCFA_COMPANY_AAID
                      WHERE COMPANY_IDX = :companyIdx AND AAID = :aaid
                 )
                """, params);
        }
        if (changed == 0) return false;
        audit.log(AuditType.STATUS,
            "AAID(정책) 상태 변경 | 고객사: " + companyIdx
                + " | AAID: " + aaid
                + " | " + (enabled ? "비활성 → 활성" : "활성 → 비활성"));
        return true;
    }

    /**
     * 현재 고객사의 모든 AAID 를 한 번에 켜거나 끈다. 실제로 바뀐 건수를 돌려준다.
     *
     * <p>이전 어드민의 {@code disableCompanyAllAAID} 를 잇는다. 그쪽은 CRITERIA 전체를
     * 무조건 insert 해서 이미 꺼 둔 AAID 가 두 번 들어갔다 — 여기서는 NOT EXISTS 로 거른다.
     * "전체 활성"은 이전 어드민에 없었지만 짝이 없으면 되돌릴 방법이 화면에 없어 함께 둔다.
     */
    @Transactional
    public int changeStatusAll(boolean enabled) {
        Long companyIdx = tenant.companyIdx();
        var params = new MapSqlParameterSource("companyIdx", companyIdx);

        // 단건 토글과 같은 잠금. 전체 비활성과 단건 활성이 겹치면 한쪽이 다른 쪽을 되돌린다.
        jdbc.queryForObject("""
            SELECT IDX FROM CCFA_COMPANY
             WHERE IDX = :companyIdx
               FOR UPDATE
            """, params, Long.class);

        int changed;
        if (enabled) {
            changed = jdbc.update("""
                DELETE FROM CCFA_COMPANY_AAID
                 WHERE COMPANY_IDX = :companyIdx
                """, params);
        } else {
            changed = jdbc.update("""
                INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
                SELECT :companyIdx, c.AAID
                  FROM CRITERIA c
                 WHERE c.AAID IS NOT NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM CCFA_COMPANY_AAID b
                        WHERE b.COMPANY_IDX = :companyIdx AND b.AAID = c.AAID
                   )
                """, params);
        }
        if (changed > 0) {
            audit.log(AuditType.STATUS,
                "AAID(정책) 전체 " + (enabled ? "활성" : "비활성")
                    + " | 고객사: " + companyIdx + " | " + changed + "건");
        }
        return changed;
    }

    @Override protected Specification<Criteria> toSpecification(CriteriaSearchForm f) {
        return Specs.all(Specs.like("aaid", f.getAaid()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Criteria e) { return null; }
    @Override protected void setCompanyIdx(Criteria e, Long c) {}
    @Override public String idOf(Criteria e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CRITERIA"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "aaid", "updatedtime"); }
}
