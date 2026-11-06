package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Userinfo
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import com.crosscert.fidoadmin.fido.web.UserSearchForm
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * USERINFO 조회 + 상태 변경. 등록·수정·삭제는 컨트롤러에서 노출하지 않는다.
 * 상태는 'O'(정상) / 'X'(해지) 만 허용한다.
 */
@Service
class UserinfoService(
    repository: UserinfoRepository,
    audit: AuditLogger,
    tenant: TenantContext,
) : CrudService<Userinfo, Long, UserSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: UserSearchForm): Specification<Userinfo> =
        Specs.all(
            Specs.like("userid", f.userid),
            Specs.like("servicename", f.servicename),
            Specs.like("aaid", f.aaid),
            Specs.eq("status", f.status),
        )
    override fun companyIdxAttribute(): String? = "companyIdx"
    override fun companyIdxOf(e: Userinfo): Long? = e.companyIdx
    override fun setCompanyIdx(e: Userinfo, c: Long?) { e.companyIdx = c }
    override fun idOf(e: Userinfo): String = e.idx.toString()
    override fun tableName(): String = "USERINFO"
    override fun sortableProperties(): Set<String> = setOf("idx", "userid", "servicename", "regtime")

    /** 상태 변경. get() 이 테넌트 검사를 한다. 감사 로그 TYPE=STATUS. */
    @Transactional
    fun changeStatus(id: Long?, status: String?): Userinfo {
        if (status == null || !STATUSES.contains(status)) {
            throw IllegalArgumentException("허용되지 않는 상태입니다: $status")
        }
        val u = get(id)
        val old = u.status
        u.status = status
        val saved = repository.save(u)
        audit.log(AuditType.STATUS, "USERINFO STATUS " + idOf(saved) + " " + old + "->" + status)
        return saved
    }

    companion object {
        /** Java `Set.of("O", "X")` 와 같은 불변 집합. */
        @JvmField
        val STATUSES: Set<String> = java.util.Set.of("O", "X")
    }
}
