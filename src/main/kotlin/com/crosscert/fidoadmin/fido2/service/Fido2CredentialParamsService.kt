package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** FIDO2_CREDENTIAL_PARAMS. COMPANY_IDX 가 없어 SUPER 전용. UPDATEDTIME 컬럼 없음. */
@Service
class Fido2CredentialParamsService(
    repository: Fido2CredentialParamsRepository, audit: AuditLogger, tenant: TenantContext,
    private val events: ApplicationEventPublisher,
) : CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm>(repository, audit, tenant) {

    /** FIDO 서버가 크리덴셜 파라미터 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다. */
    override fun afterChange(action: String, e: Fido2CredentialParams) {
        events.publishEvent(FidoConfigChanged(tableName() + " " + action + " " + idOf(e)))
    }

    override fun toSpecification(f: Fido2CredentialParamsSearchForm): Specification<Fido2CredentialParams> =
        Specs.all(
            Specs.like("credType", f.credType),
            Specs.eq("status", f.status))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: Fido2CredentialParams): Long? = null
    override fun setCompanyIdx(e: Fido2CredentialParams, c: Long?) {}
    override fun idOf(e: Fido2CredentialParams): String = e.idx.toString()
    override fun tableName(): String = "FIDO2_CREDENTIAL_PARAMS"
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "credType", "credAlg", "createdtime")

    /** DB 기본값: CRED_TYPE 'public-key', STATUS 'T'. */
    override fun applyDefaults(e: Fido2CredentialParams) {
        if (e.credType.let { it == null || it.all(Character::isWhitespace) }) e.credType = "public-key"
        if (e.status.let { it == null || it.all(Character::isWhitespace) }) e.status = "T"
    }
    override fun touchCreated(e: Fido2CredentialParams, now: LocalDateTime) { e.createdtime = now }
}
