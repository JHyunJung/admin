package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.AssignedIdCrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository
import com.crosscert.fidoadmin.fido2.web.Fido2DemoAccessCodeSearchForm
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/**
 * FIDO2_DEMO_ACCESS_CODE. 할당형 문자열 PK(ACCESSCODE) 라 존재 검사 + persist 로 등록한다.
 * COMPANY_IDX 가 없어 SUPER 전용. 타임스탬프 컬럼이 없다.
 */
@Service
class Fido2DemoAccessCodeService(
    repository: Fido2DemoAccessCodeRepository, audit: AuditLogger, em: EntityManager, tenant: TenantContext,
) : AssignedIdCrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm>(repository, audit, em, tenant) {

    override fun toSpecification(f: Fido2DemoAccessCodeSearchForm): Specification<Fido2DemoAccessCode> =
        Specs.all(
            Specs.like("accesscode", f.accesscode),
            Specs.like("vendorname", f.vendorname),
            Specs.eq("status", f.status))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: Fido2DemoAccessCode): Long? = null
    override fun setCompanyIdx(e: Fido2DemoAccessCode, c: Long?) {}
    override fun assignedId(e: Fido2DemoAccessCode): String? = e.accesscode
    override fun idOf(e: Fido2DemoAccessCode): String? = e.accesscode
    override fun tableName(): String = "FIDO2_DEMO_ACCESS_CODE"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.ASC, "accesscode")
    override fun sortableProperties(): Set<String> = java.util.Set.of("accesscode", "vendorname", "status")

    /** DB 기본값: STATUS 'E'(활성). */
    override fun applyDefaults(e: Fido2DemoAccessCode) {
        if (e.status.let { it == null || it.all(Character::isWhitespace) }) e.status = "E"
    }
}
