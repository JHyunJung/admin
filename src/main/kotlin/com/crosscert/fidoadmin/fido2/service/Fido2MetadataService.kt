package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** FIDO2_METADATA. COMPANY_IDX 가 없어 SUPER 전용. UPDATEDTIME 컬럼이 없다. */
@Service
class Fido2MetadataService(
    repository: Fido2MetadataRepository, audit: AuditLogger, tenant: TenantContext,
    private val events: ApplicationEventPublisher,
) : CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm>(repository, audit, tenant) {

    /** FIDO 서버가 FIDO2 메타데이터 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다. */
    override fun afterChange(action: String, e: Fido2Metadata) {
        events.publishEvent(FidoConfigChanged(tableName() + " " + action + " " + idOf(e)))
    }

    override fun toSpecification(f: Fido2MetadataSearchForm): Specification<Fido2Metadata> =
        Specs.all(
            Specs.like("aaguid", f.aaguid),
            Specs.like("description", f.description),
            Specs.like("protocolfamily", f.protocolfamily))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: Fido2Metadata): Long? = null
    override fun setCompanyIdx(e: Fido2Metadata, c: Long?) {}
    override fun idOf(e: Fido2Metadata): String = e.idx.toString()
    override fun tableName(): String = "FIDO2_METADATA"
    override fun sortableProperties(): Set<String> =
        java.util.Set.of("idx", "description", "aaguid", "protocolfamily", "createdtime")
    /** 목록 DTO 가 싣지 않는 CLOB. 목록 조회의 SELECT 절에서 빼 행마다 전송·메모리 적재를 막는다. */
    override fun listExcludedProperties(): Set<String> = java.util.Set.of("attestationrootcertificates", "icon")
    override fun touchCreated(e: Fido2Metadata, now: LocalDateTime) { e.createdtime = now }
}
