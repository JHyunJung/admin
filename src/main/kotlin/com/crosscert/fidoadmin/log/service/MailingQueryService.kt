package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository
import com.crosscert.fidoadmin.log.web.MailingSearchForm
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_MAILING(메일/SMS 발송 큐) 조회 전용. SENDTIME 은 문자열이라 정렬만 지원한다. */
@Service
class MailingQueryService(repository: CcfaMailingRepository, audit: AuditLogger, tenant: TenantContext) :
    CrudService<CcfaMailing, Long, MailingSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: MailingSearchForm): Specification<CcfaMailing> = Specs.all(
        Specs.eq("status", f.status),
        Specs.like("to", f.to),
        Specs.eq("smsStatus", f.smsStatus))
    /** Java 에서는 protected 였다. 같은 패키지 테스트가 부르던 것을 Kotlin 테스트도 부를 수 있게 public 으로 넓힌다. */
    public override fun companyIdxAttribute(): String = "companyIdx"
    override fun companyIdxOf(e: CcfaMailing): Long? = e.companyIdx
    override fun setCompanyIdx(e: CcfaMailing, c: Long?) { e.companyIdx = c }
    override fun idOf(e: CcfaMailing): String = e.idx.toString()
    override fun tableName(): String = "CCFA_MAILING"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.DESC, "idx")
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "status", "sendtime")
}
