package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Transactionhash
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository
import com.crosscert.fidoadmin.fido.web.TransactionhashSearchForm
import java.time.Duration
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** TRANSACTIONHASH 조회 전용. */
@Service
class TransactionhashQueryService(
    repository: TransactionhashRepository,
    audit: AuditLogger,
    tenant: TenantContext,
) : CrudService<Transactionhash, Long, TransactionhashSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: TransactionhashSearchForm): Specification<Transactionhash> =
        Specs.all(
            Specs.like("userid", f.userid),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()),
        )
    override fun companyIdxAttribute(): String? = "companyIdx"
    override fun companyIdxOf(e: Transactionhash): Long? = e.companyIdx
    override fun setCompanyIdx(e: Transactionhash, c: Long?) { e.companyIdx = c }
    override fun idOf(e: Transactionhash): String = e.idx.toString()
    override fun tableName(): String = "TRANSACTIONHASH"
    /**
     * 시퀀스 PK 역순. 행이 많은 로그 테이블이라 CREATETIME 로 정렬하면 매 조회가 전체 정렬이 되고,
     * IDX 로 정렬하면 PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다. 생성 순서와 거의 같다
     * (같은 시각에 들어온 행끼리는 순서가 다를 수 있다). 시각 정렬은 목록 머리글로 고를 수 있다.
     */
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.DESC, "idx")
    /** 행이 많은 로그라 페이지를 넘길 때마다 COUNT(*) 를 다시 보내지 않는다. "총 N건" 은 최대 30초 늦다. */
    override fun countCacheTtl(): Duration = LOG_COUNT_TTL
    override fun sortableProperties(): Set<String> = setOf("idx", "createtime")
    /** 목록 DTO 가 싣지 않는 CLOB. 목록 조회의 SELECT 절에서 빼 행마다 전송·메모리 적재를 막는다. */
    override fun listExcludedProperties(): Set<String> = setOf("content")
}
