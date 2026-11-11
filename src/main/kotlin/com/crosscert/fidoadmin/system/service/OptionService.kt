package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.entity.CcfaOptions
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository
import com.crosscert.fidoadmin.system.web.OptionSearchForm
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CCFA_OPTION(코드 그룹) CRUD + CCFA_OPTIONS(코드) 인라인 관리. COMPANY_IDX 가 없으므로 SUPER 전용.
 * 코드는 그룹 상세 안에서만 추가·삭제하며 별도 화면이 없다.
 */
@Service
class OptionService(
    repository: CcfaOptionRepository,
    audit: AuditLogger,
    private val items: CcfaOptionsRepository,
    tenant: TenantContext,
) : CrudService<CcfaOption, Long, OptionSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: OptionSearchForm): Specification<CcfaOption> =
        Specs.all(Specs.like("optionName", f.optionName))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaOption): Long? = null
    override fun setCompanyIdx(e: CcfaOption, c: Long?) {}
    override fun idOf(e: CcfaOption): String = e.idx.toString()
    override fun tableName(): String = "CCFA_OPTION"
    override fun sortableProperties(): Set<String> = setOf("idx", "optionName")

    override fun beforeDelete(e: CcfaOption) {
        val count = items.countByOptionIdx(e.idx)
        if (count > 0) throw IllegalStateException("하위 코드 " + count + "건이 있어 삭제할 수 없습니다. 코드를 먼저 삭제하세요.")
    }

    @Transactional(readOnly = true)
    fun items(optionIdx: Long?): List<CcfaOptions> {
        requireSuperForGlobalTable()
        return items.findByOptionIdxOrderByIdxAsc(optionIdx)
    }

    /** 그룹 존재 확인 후 코드를 추가한다. OPTION_IDX 는 폼이 아니라 경로의 그룹으로 강제한다. */
    @Transactional
    fun addItem(optionIdx: Long, item: CcfaOptions): CcfaOptions {
        val group = get(optionIdx)
        item.idx = null
        item.optionIdx = group.idx
        val saved = items.save(item)
        auditLog(AuditType.CREATE, "CCFA_OPTIONS CREATE " + saved.idx)
        return saved
    }

    /** 경로의 그룹에 속한 코드만 지운다. 다른 그룹의 코드 IDX 를 넣으면 존재하지 않는 것으로 본다. */
    @Transactional
    fun removeItem(optionIdx: Long, itemIdx: Long) {
        val group = get(optionIdx)
        val item = items.findById(itemIdx)
            .filter { i -> group.idx!! == i.optionIdx }
            .orElseThrow { EntityNotFoundException("CCFA_OPTIONS $itemIdx") }
        items.delete(item)
        auditLog(AuditType.DELETE, "CCFA_OPTIONS DELETE $itemIdx")
    }
}
