package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.AssignedIdCrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import com.crosscert.fidoadmin.system.repository.CcfaSystemInfoRepository
import com.crosscert.fidoadmin.system.web.SystemInfoSearchForm
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_SYSTEM_INFO — 문자열 PK(PROP_KEY). COMPANY_IDX 없음 → SUPER 전용. */
@Service
class SystemInfoService(
    repository: CcfaSystemInfoRepository,
    audit: AuditLogger,
    em: EntityManager,
    tenant: TenantContext,
) : AssignedIdCrudService<CcfaSystemInfo, String, SystemInfoSearchForm>(repository, audit, em, tenant) {

    override fun toSpecification(f: SystemInfoSearchForm): Specification<CcfaSystemInfo> =
        Specs.all(Specs.like("propKey", f.propKey))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaSystemInfo): Long? = null
    override fun setCompanyIdx(e: CcfaSystemInfo, c: Long?) {}
    override fun idOf(e: CcfaSystemInfo): String? = e.propKey
    override fun assignedId(e: CcfaSystemInfo): String? = e.propKey
    override fun tableName(): String = "CCFA_SYSTEM_INFO"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.ASC, "propKey")
    override fun sortableProperties(): Set<String> = setOf("propKey", "updatedtime")
    override fun touchCreated(e: CcfaSystemInfo, now: LocalDateTime) { e.updatedtime = now }
    override fun touchUpdated(e: CcfaSystemInfo, now: LocalDateTime) { e.updatedtime = now }
}
