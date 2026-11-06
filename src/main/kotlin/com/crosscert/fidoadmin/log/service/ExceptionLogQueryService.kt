package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.entity.CcfaExceptions
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** CCFA_EXCEPTIONS 조회 전용. CREATEDTIME 은 문자열이므로 like 검색만 지원한다. */
@Service
class ExceptionLogQueryService(repository: CcfaExceptionsRepository, audit: AuditLogger, tenant: TenantContext) :
    CrudService<CcfaExceptions, Long, ExceptionLogSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: ExceptionLogSearchForm): Specification<CcfaExceptions> = Specs.all(
        Specs.like("eType", f.eType),
        Specs.eq("eLevel", f.eLevel),
        Specs.like("exceptionMessage", f.message),
        Specs.like("createdtime", f.createdtime))
    /** Java 에서는 protected 였다. 같은 패키지 테스트가 부르던 것을 Kotlin 테스트도 부를 수 있게 public 으로 넓힌다. */
    public override fun companyIdxAttribute(): String = "companyIdx"
    override fun companyIdxOf(e: CcfaExceptions): Long? = e.companyIdx
    override fun setCompanyIdx(e: CcfaExceptions, c: Long?) { e.companyIdx = c }
    override fun idOf(e: CcfaExceptions): String = e.idx.toString()
    override fun tableName(): String = "CCFA_EXCEPTIONS"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.DESC, "idx")
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "createdtime", "eType", "eLevel")
    /** 목록 DTO 가 싣지 않는 CLOB. 목록 조회의 SELECT 절에서 빼 행마다 전송·메모리 적재를 막는다. */
    override fun listExcludedProperties(): Set<String> = java.util.Set.of("exceptionData")
}
