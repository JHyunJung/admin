package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.common.isNullOrJavaBlank
import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Appserver
import com.crosscert.fidoadmin.fido.repository.AppserverRepository
import com.crosscert.fidoadmin.fido.web.AppserverSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** APPSERVER. COMPANY_IDX 로 테넌트 격리. DB 기본값 TYPE='use'. */
@Service
class AppserverService(
    repository: AppserverRepository,
    audit: AuditLogger,
    tenant: TenantContext,
    private val events: ApplicationEventPublisher,
    private val memberCodes: MemberCodeGenerator,
) : CrudService<Appserver, Long, AppserverSearchForm>(repository, audit, tenant) {

    /** FIDO 서버가 멤버코드 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다. */
    override fun afterChange(action: String, e: Appserver) {
        events.publishEvent(FidoConfigChanged(tableName() + " " + action + " " + idOf(e)))
    }

    /**
     * 현재 고객사에 같은 MEMBER_CODE + MEMBER_ID 행이 있는가. 수정이면 자기 행(`excludeIdx`)은 뺀다.
     * membercode 는 중복될 수 없다. DB 유니크 제약은 없다.
     */
    @Transactional(readOnly = true)
    fun existsDuplicate(memberCode: String?, memberId: String?, excludeIdx: Long?): Boolean {
        if (memberCode.isNullOrJavaBlank() || memberId.isNullOrJavaBlank()) return false
        val r = repository as AppserverRepository
        val company = tenant.companyIdx()
        return if (excludeIdx == null) {
            r.existsByCompanyIdxAndMemberCodeAndMemberId(company, memberCode, memberId)
        } else {
            r.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(company, memberCode, memberId, excludeIdx)
        }
    }

    override fun toSpecification(f: AppserverSearchForm): Specification<Appserver> =
        Specs.all(
            Specs.like("memberCode", f.memberCode),
            Specs.like("memberId", f.memberId),
            Specs.eq("type", f.type),
        )
    override fun companyIdxAttribute(): String? = "companyIdx"
    override fun companyIdxOf(e: Appserver): Long? = e.companyIdx
    override fun setCompanyIdx(e: Appserver, c: Long?) { e.companyIdx = c }
    override fun idOf(e: Appserver): String = e.idx.toString()
    override fun tableName(): String = "APPSERVER"
    override fun sortableProperties(): Set<String> = setOf("idx", "memberCode", "memberId", "createdtime")

    /**
     * 등록 때만 불린다(CrudService.create). 멤버코드는 사람이 정하지 않는다 — 호출자가 무엇을
     * 넘겼든 새로 만든다. 수정 경로는 이 훅을 거치지 않으므로 한 번 정한 코드는 바뀌지 않는다.
     */
    override fun applyDefaults(e: Appserver) {
        if (e.type.isNullOrJavaBlank()) e.type = "use"
        e.memberCode = memberCodes.generate()
    }
    override fun touchCreated(e: Appserver, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: Appserver, now: LocalDateTime) { e.updatedtime = now }
}
