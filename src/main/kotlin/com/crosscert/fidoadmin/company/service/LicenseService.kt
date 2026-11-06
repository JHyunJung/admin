package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository
import com.crosscert.fidoadmin.company.web.LicenseSearchForm
import java.time.LocalDateTime
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_LICENSE. SUPER 전용 URL. COMPANY_NAME 은 비정규화 컬럼이라 CCFA_COMPANY 에서 동기화한다. */
@Service
class LicenseService(
    repository: CcfaLicenseRepository, audit: AuditLogger,
    private val companies: CcfaCompanyRepository, tenant: TenantContext,
) : CrudService<CcfaLicense, Long, LicenseSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: LicenseSearchForm): Specification<CcfaLicense> =
        Specs.all(Specs.like("serviceName", f.serviceName))
    override fun companyIdxAttribute(): String? = "companyIdx"
    override fun companyIdxOf(e: CcfaLicense): Long? = e.companyIdx
    override fun setCompanyIdx(e: CcfaLicense, c: Long?) { e.companyIdx = c }
    override fun idOf(e: CcfaLicense): String = e.idx.toString()
    override fun tableName(): String = "CCFA_LICENSE"
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "serviceName", "createdtime")

    override fun applyDefaults(e: CcfaLicense) { syncCompanyName(e) }
    override fun touchCreated(e: CcfaLicense, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: CcfaLicense, now: LocalDateTime) { e.updatedtime = now; syncCompanyName(e) }

    /** COMPANY_IDX 로 CCFA_COMPANY 이름을 찾아 COMPANY_NAME 에 복사한다. 없으면 null 로 둔다. */
    internal fun syncCompanyName(e: CcfaLicense) {
        val companyIdx = e.companyIdx
        if (companyIdx == null) { e.companyName = null; return }
        e.companyName = companies.findById(companyIdx).map { it.companyName }.orElse(null)
    }
}
