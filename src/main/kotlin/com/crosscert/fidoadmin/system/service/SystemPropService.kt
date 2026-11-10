package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.AssignedIdCrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import com.crosscert.fidoadmin.system.web.SystemPropSearchForm
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/**
 * CCFA_SYSTEM_PROP. 복합키(PROP_KEY, COMPANY_IDX) 가 폼에서 채워지므로 삽입 전용 경로를 쓴다.
 * SUPER 전용 URL(/system/\*\*) 이지만 COMPANY_IDX 가 키 안에 있어 테넌트 속성은 "id.companyIdx" 로 둔다
 * (SUPER 의 고객사 필터에만 쓰인다).
 */
@Service
class SystemPropService(
    repository: CcfaSystemPropRepository,
    audit: AuditLogger,
    em: EntityManager,
    tenant: TenantContext,
) : AssignedIdCrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm>(repository, audit, em, tenant) {

    override fun toSpecification(f: SystemPropSearchForm): Specification<CcfaSystemProp> =
        Specs.all(Specs.like("id.propKey", f.propKey))
    override fun companyIdxAttribute(): String? = "id.companyIdx"
    override fun companyIdxOf(e: CcfaSystemProp): Long? = e.id?.companyIdx
    override fun setCompanyIdx(e: CcfaSystemProp, c: Long?) {
        if (e.id == null) e.id = CcfaSystemPropId()
        e.id!!.companyIdx = c
    }
    override fun idOf(e: CcfaSystemProp): String = e.id!!.toPathValue()
    override fun assignedId(e: CcfaSystemProp): CcfaSystemPropId? {
        val id = e.id
        val key = id?.propKey
        if (id == null || key == null || key.javaIsBlank() || id.companyIdx == null) return null
        return id
    }
    override fun tableName(): String = "CCFA_SYSTEM_PROP"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx")
    override fun sortableProperties(): Set<String> = setOf("id.propKey", "id.companyIdx", "updatedtime")

    override fun applyDefaults(e: CcfaSystemProp) {
        val shareType = e.shareType
        if (shareType == null || shareType.javaIsBlank()) e.shareType = SHARE_TYPE_DEFAULT
    }
    override fun touchCreated(e: CcfaSystemProp, now: LocalDateTime) { e.updatedtime = now }
    override fun touchUpdated(e: CcfaSystemProp, now: LocalDateTime) { e.updatedtime = now }

    companion object {
        /** SHARE_TYPE 이 비어 있을 때 넣는 기본값. */
        private const val SHARE_TYPE_DEFAULT = "NO"
    }
}
