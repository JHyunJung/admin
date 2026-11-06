package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm
import java.time.LocalDateTime
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_CRITERIA — 어드민 인증기기 정책. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
class AdminCriteriaService(
    repository: CcfaCriteriaRepository,
    audit: AuditLogger,
    tenant: TenantContext,
) : CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: AdminCriteriaSearchForm): Specification<CcfaCriteria> =
        Specs.all(Specs.like("aaid", f.aaid))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaCriteria): Long? = null
    override fun setCompanyIdx(e: CcfaCriteria, c: Long?) {}
    override fun idOf(e: CcfaCriteria): String = e.idx.toString()
    override fun tableName(): String = "CCFA_CRITERIA"
    override fun sortableProperties(): Set<String> = setOf("idx", "aaid", "updatedtime")
    /** 목록 DTO 가 싣지 않는 CLOB. 목록 조회의 SELECT 절에서 빼 행마다 전송·메모리 적재를 막는다. */
    override fun listExcludedProperties(): Set<String> = setOf("jsondata")

    override fun touchCreated(e: CcfaCriteria, now: LocalDateTime) { e.createtime = now; e.updatedtime = now }
    override fun touchUpdated(e: CcfaCriteria, now: LocalDateTime) { e.updatedtime = now }
}
