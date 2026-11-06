package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaFields
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository
import com.crosscert.fidoadmin.system.web.FieldSearchForm
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** CCFA_FIELDS — 화면 필드 메타 정의. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
class FieldService(
    repository: CcfaFieldsRepository,
    audit: AuditLogger,
    private val options: CcfaOptionRepository,
    tenant: TenantContext,
) : CrudService<CcfaFields, Long, FieldSearchForm>(repository, audit, tenant) {

    override fun toSpecification(f: FieldSearchForm): Specification<CcfaFields> =
        Specs.all(Specs.like("fieldTable", f.fieldTable), Specs.like("fieldName", f.fieldName))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaFields): Long? = null
    override fun setCompanyIdx(e: CcfaFields, c: Long?) {}
    override fun idOf(e: CcfaFields): String = e.idx.toString()
    override fun tableName(): String = "CCFA_FIELDS"
    override fun defaultSort(): Sort = Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx"))
    override fun sortableProperties(): Set<String> = setOf("idx", "fieldTable", "fieldName")

    override fun applyDefaults(e: CcfaFields) {
        if (e.pk == null) e.pk = 0L
        if (e.fk == null) e.fk = 0L
        if (e.editable == null) e.editable = 0L
    }

    /** 폼의 코드 그룹 select 와 목록의 그룹 이름 표시용. */
    @Transactional(readOnly = true)
    fun options(): List<CcfaOption> {
        requireSuperForGlobalTable()
        return options.findAll(Sort.by("idx"))
    }

    /** optionIdx 가 유효한 코드 그룹 참조인지: 비어 있거나(선택 항목) 실제 존재하는 코드 그룹이어야 한다. */
    @Transactional(readOnly = true)
    fun optionExists(optionIdx: Long?): Boolean = optionIdx == null || options.existsById(optionIdx)
}
