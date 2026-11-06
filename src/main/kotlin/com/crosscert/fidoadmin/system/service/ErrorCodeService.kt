package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.AssignedIdCrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository
import com.crosscert.fidoadmin.system.web.ErrorCodeSearchForm
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_ERROR_TABLE — 문자열 PK(ERROR_CODE). 타임스탬프 컬럼 없음. SUPER 전용. */
@Service
class ErrorCodeService(
    repository: CcfaErrorTableRepository,
    audit: AuditLogger,
    em: EntityManager,
    tenant: TenantContext,
) : AssignedIdCrudService<CcfaErrorTable, String, ErrorCodeSearchForm>(repository, audit, em, tenant) {

    override fun toSpecification(f: ErrorCodeSearchForm): Specification<CcfaErrorTable> =
        Specs.all(
            Specs.like("errorCode", f.errorCode),
            Specs.like("errorType", f.errorType),
        )
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaErrorTable): Long? = null
    override fun setCompanyIdx(e: CcfaErrorTable, c: Long?) {}
    override fun idOf(e: CcfaErrorTable): String? = e.errorCode
    override fun assignedId(e: CcfaErrorTable): String? = e.errorCode
    override fun tableName(): String = "CCFA_ERROR_TABLE"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.ASC, "errorCode")
    override fun sortableProperties(): Set<String> = setOf("errorCode", "errorType")
}
