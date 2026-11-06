package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * CRITERIA(AAID 정책) 조회·등록과 고객사별 활성/비활성 토글.
 *
 * CRITERIA 자체는 COMPANY_IDX 가 없는 전역 기준 데이터라 companyIdxAttribute() 는
 * null 이다. 그래도 SUPER 전용이 아니다 — 이 화면의 실제 용도가 고객사별 토글이고,
 * 그 상태는 CCFA_COMPANY_AAID 에 고객사별로 들어간다. 기반의
 * requireSuperForGlobalTable() 을 재정의해 로그인 확인까지만 하고,
 * 고객사 경계는 [disabledAaids] 와 [changeStatus] 이
 * 유효 테넌트로 직접 건다.
 */
@Service
class CriteriaQueryService(
    repository: CriteriaRepository,
    audit: AuditLogger,
    tenant: TenantContext,
    private val jdbc: NamedParameterJdbcTemplate,
    private val events: ApplicationEventPublisher,
    private val parser: CriteriaMetadataParser,
) : CrudService<Criteria, Long, CriteriaSearchForm>(repository, audit, tenant) {

    /**
     * CRITERIA 자체는 전역 기준 데이터지만, 이 화면의 쓸모는 고객사별 활성/비활성
     * 토글에 있다(CCFA_COMPANY_AAID). 그래서 기반의 SUPER 전용 차단을 풀고
     * 로그인 여부만 확인한다 — 고객사 경계는 아래 두 메서드가 유효 테넌트로 건다.
     */
    override fun requireSuperForGlobalTable() {
        tenant.require()
    }

    /**
     * 현재 고객사에서 **꺼 둔** AAID 집합.
     *
     * CCFA_COMPANY_AAID 는 차단 목록이다 — 행이 있으면 비활성이다.
     * 비활성화가 insert, 활성화가 delete 다.
     */
    @Transactional(readOnly = true)
    fun disabledAaids(): Set<String> {
        val companyIdx = tenant.companyIdx()
        val sql = """
            SELECT DISTINCT AAID
              FROM CCFA_COMPANY_AAID
             WHERE COMPANY_IDX = :companyIdx
               AND AAID IS NOT NULL
            """.trimIndent()
        val params = MapSqlParameterSource("companyIdx", companyIdx)
        return HashSet(jdbc.queryForList(sql, params, String::class.java))
    }

    /**
     * 메타데이터 JSON 으로 새 AAID 정책을 등록한다. SUPER 전용.
     *
     * CRITERIA 는 전역 기준 데이터라 고객사 운영자가 늘릴 수 없다. 이 서비스는
     * [requireSuperForGlobalTable] 을 로그인 확인으로 풀어 두었으므로 여기서 직접 막는다.
     *
     * 새 AAID 는 모든 고객사에서 꺼진 채로 시작한다 — 고객사 생성 때 AAID 를 전부 막는 것과 같은
     * 규약이다. 각 고객사가 목록에서 켜야 FIDO 등록에 쓰인다.
     *
     * CRITERIA 에 유니크 제약이 없어 동시 등록 경쟁은 막지 못한다. SUPER 전용·저빈도 화면이라
     * 애플리케이션 중복 검사로 둔다.
     */
    @Transactional
    fun create(json: String?): Criteria {
        if (!tenant.require().isSuper) {
            throw AccessDeniedException("AAID 정책 등록은 최고 관리자 전용입니다")
        }
        val criteria = parser.parse(json)
        val criteriaRepository = repository as CriteriaRepository
        if (criteriaRepository.existsByAaid(criteria.aaid)) {
            throw CriteriaMetadataException("이미 등록된 AAID 입니다.")
        }
        val now = LocalDateTime.now()
        criteria.createtime = now
        criteria.updatedtime = now
        val saved = repository.save(criteria)

        val blocked = jdbc.update(
            """
            INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
            SELECT c.IDX, :aaid FROM CCFA_COMPANY c
             WHERE NOT EXISTS (
                 SELECT 1 FROM CCFA_COMPANY_AAID b
                  WHERE b.COMPANY_IDX = c.IDX AND b.AAID = :aaid
             )
            """.trimIndent(),
            MapSqlParameterSource("aaid", saved.aaid),
        )

        audit.log(
            AuditType.CREATE,
            "AAID(정책) 등록 | AAID: " + saved.aaid + " | 차단 고객사 " + blocked + "곳",
        )
        events.publishEvent(FidoConfigChanged("AAID 등록 " + saved.aaid))
        return saved
    }

    /**
     * 현재 고객사에서 AAID 를 켜거나 끈다. 이미 그 상태면 `false`.
     *
     * JPA 가 아니라 JDBC 인 이유: CCFA_COMPANY_AAID 는 (COMPANY_IDX, AAID)
     * 복합 키에 다른 컬럼이 없는 순수 연결 테이블이라 엔티티로 둘 이득이 없다.
     */
    @Transactional
    fun changeStatus(id: Long?, enabled: Boolean): Boolean {
        val companyIdx = tenant.companyIdx()
        val criteria = get(id)
        val aaid = criteria.aaid
        if (aaid.isNullOrJavaBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "AAID가 없는 정책입니다")
        }
        val params = MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("aaid", aaid)

        // 같은 고객사의 상태 변경을 직렬화한다. 이 테이블에는 유니크 제약이 없어
        // 동시 요청이 들어오면 같은 (COMPANY_IDX, AAID) 행이 두 번 들어갈 수 있다.
        jdbc.queryForObject(
            """
            SELECT IDX FROM CCFA_COMPANY
             WHERE IDX = :companyIdx
               FOR UPDATE
            """.trimIndent(),
            params,
            Long::class.javaObjectType,
        )

        val changed = if (enabled) {
            jdbc.update(
                """
                DELETE FROM CCFA_COMPANY_AAID
                 WHERE COMPANY_IDX = :companyIdx
                   AND AAID = :aaid
                """.trimIndent(),
                params,
            )
        } else {
            jdbc.update(
                """
                INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
                SELECT :companyIdx, :aaid FROM DUAL
                 WHERE NOT EXISTS (
                     SELECT 1 FROM CCFA_COMPANY_AAID
                      WHERE COMPANY_IDX = :companyIdx AND AAID = :aaid
                 )
                """.trimIndent(),
                params,
            )
        }
        if (changed == 0) return false
        audit.log(
            AuditType.STATUS,
            "AAID(정책) 상태 변경 | 고객사: " + companyIdx +
                " | AAID: " + aaid +
                " | " + (if (enabled) "비활성 → 활성" else "활성 → 비활성"),
        )
        events.publishEvent(FidoConfigChanged("AAID 상태 변경 $aaid"))
        return true
    }

    /**
     * 현재 고객사의 모든 AAID 를 한 번에 켜거나 끈다. 실제로 바뀐 건수를 돌려준다.
     *
     * 끌 때는 이미 꺼 둔 AAID 가 두 번 들어가지 않도록 NOT EXISTS 로 거른다.
     * "전체 활성"은 짝이 없으면 되돌릴 방법이 화면에 없어 함께 둔다.
     */
    @Transactional
    fun changeStatusAll(enabled: Boolean): Int {
        val companyIdx = tenant.companyIdx()
        val params = MapSqlParameterSource("companyIdx", companyIdx)

        // 단건 토글과 같은 잠금. 전체 비활성과 단건 활성이 겹치면 한쪽이 다른 쪽을 되돌린다.
        jdbc.queryForObject(
            """
            SELECT IDX FROM CCFA_COMPANY
             WHERE IDX = :companyIdx
               FOR UPDATE
            """.trimIndent(),
            params,
            Long::class.javaObjectType,
        )

        val changed = if (enabled) {
            jdbc.update(
                """
                DELETE FROM CCFA_COMPANY_AAID
                 WHERE COMPANY_IDX = :companyIdx
                """.trimIndent(),
                params,
            )
        } else {
            disableAllFor(companyIdx)
        }
        if (changed > 0) {
            audit.log(
                AuditType.STATUS,
                "AAID(정책) 전체 " + (if (enabled) "활성" else "비활성") +
                    " | 고객사: " + companyIdx + " | " + changed + "건",
            )
            events.publishEvent(FidoConfigChanged("AAID 전체 " + (if (enabled) "활성" else "비활성") + " " + changed + "건"))
        }
        return changed
    }

    /**
     * 주어진 고객사에서 모든 AAID 를 차단한다. 잠금·감사 로그·이벤트는 호출자 몫이다.
     * 고객사 생성(`CompanyService.create`)과 전체 비활성([changeStatusAll])이 같이 쓴다.
     */
    @Transactional
    fun disableAllFor(companyIdx: Long?): Int =
        jdbc.update(
            """
            INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
            SELECT DISTINCT :companyIdx, c.AAID
              FROM CRITERIA c
             WHERE c.AAID IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM CCFA_COMPANY_AAID b
                    WHERE b.COMPANY_IDX = :companyIdx AND b.AAID = c.AAID
               )
            """.trimIndent(),
            MapSqlParameterSource("companyIdx", companyIdx),
        )

    /** 주어진 고객사의 차단 행을 모두 지운다. 고객사 삭제 때 쓴다. */
    @Transactional
    fun deleteAllFor(companyIdx: Long?): Int =
        jdbc.update(
            """
            DELETE FROM CCFA_COMPANY_AAID
             WHERE COMPANY_IDX = :companyIdx
            """.trimIndent(),
            MapSqlParameterSource("companyIdx", companyIdx),
        )

    override fun toSpecification(f: CriteriaSearchForm): Specification<Criteria> =
        Specs.all(Specs.like("aaid", f.aaid))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: Criteria): Long? = null
    override fun setCompanyIdx(e: Criteria, c: Long?) {}
    override fun idOf(e: Criteria): String = e.idx.toString()
    override fun tableName(): String = "CRITERIA"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.DESC, "idx")
    override fun sortableProperties(): Set<String> = setOf("idx", "aaid", "updatedtime")
    /** 목록 DTO 가 싣지 않는 CLOB. 목록 조회의 SELECT 절에서 빼 행마다 전송·메모리 적재를 막는다. */
    override fun listExcludedProperties(): Set<String> = setOf("jsondata")
}
