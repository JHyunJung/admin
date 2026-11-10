package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.company.web.CompanySearchForm
import com.crosscert.fidoadmin.fido.repository.AppidRepository
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** CCFA_COMPANY. SUPER 전용이므로 테넌트 필터 없음. IDX 0 은 전역 레코드라 삭제 불가. */
@Service
class CompanyService(
    repository: CcfaCompanyRepository, audit: AuditLogger,
    private val appids: AppidRepository,
    private val users: UserinfoRepository,
    private val managers: CcfaManagerRepository,
    private val props: CcfaSystemPropRepository,
    tenant: TenantContext,
    private val criteria: CriteriaQueryService,
    private val events: ApplicationEventPublisher,
    private val lookup: CompanyLookup,
) : CrudService<CcfaCompany, Long, CompanySearchForm>(repository, audit, tenant) {

    /**
     * 고객사 이름은 [CompanyLookup] 이 캐시한다. 행이 바뀌면 지워서 바뀐 이름이 바로 보이게 한다.
     *
     * Java 에서는 protected 였고 같은 패키지의 테스트가 직접 불렀다. Kotlin 의 protected 는 같은 패키지에
     * 열리지 않으므로 public 으로 넓힌다(호출 경로는 그대로).
     */
    public override fun afterChange(action: String, e: CcfaCompany) {
        lookup.evict(e.idx)
    }

    override fun toSpecification(f: CompanySearchForm): Specification<CcfaCompany> =
        Specs.all(
            Specs.like("companyName", f.companyName),
            Specs.eq("companyType", f.companyType),
            Specs.eq("enableType", f.enableType))
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaCompany): Long? = null
    override fun setCompanyIdx(e: CcfaCompany, c: Long?) {}
    override fun idOf(e: CcfaCompany): String = e.idx.toString()
    override fun tableName(): String = "CCFA_COMPANY"
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "companyName")

    override fun applyDefaults(e: CcfaCompany) {
        if (e.enableType == null) e.enableType = "Y"
        if (e.maxAppid == null) e.maxAppid = 0L
        if (e.maxAppserver == null) e.maxAppserver = 0L
        if (e.maxUser == null) e.maxUser = 0L
        if (e.starttime == null) e.starttime = LocalDateTime.now()
        if (e.endtime == null) e.endtime = DEFAULT_ENDTIME
        if (e.creator == null) e.creator = tenant.require().idx
    }
    override fun touchCreated(e: CcfaCompany, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: CcfaCompany, now: LocalDateTime) {
        e.updatedtime = now
        e.updator = tenant.require().idx
    }

    /**
     * 고객사를 만들고, 회사 0 의 고객사별 설정(SHARE_TYPE='NO')을 새 고객사로 복사한다.
     *
     * 복사하지 않아도 화면은
     * `FidoSettingKey` 기본값으로 동작하지만, 설정 행이 없는 고객사와 있는 고객사가
     * 섞이면 "왜 이 고객사만 값이 없나"를 매번 설명해야 한다. 생성 시점에 맞춰 둔다.
     */
    @Transactional
    override fun create(entity: CcfaCompany): CcfaCompany {
        val saved = super.create(entity)
        val copied = copyPerCompanyProps(saved.idx)
        if (copied > 0) {
            audit.log(AuditType.CREATE,
                "CCFA_SYSTEM_PROP COPY 고객사 " + saved.idx + " ← 회사 0 (" + copied + "건)")
        }
        // 새 고객사는 인증기를 하나씩 허용하기 전까지 FIDO 등록이 되지 않는다.
        val disabled = criteria.disableAllFor(saved.idx)
        if (disabled > 0) {
            audit.log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 " + saved.idx + " (" + disabled + "건)")
        }
        events.publishEvent(FidoConfigChanged("고객사 생성 " + saved.idx))
        return saved
    }

    /**
     * 고객사를 지우고, 그 고객사만 쓰던 설정 행도 함께 지운다.
     *
     * 하위 데이터 검사([beforeDelete])는 `super.delete` 안에서 먼저 돈다.
     * 거기서 막히면 예외로 빠져나오므로 설정 행은 건드리지 않는다.
     */
    @Transactional
    override fun delete(id: Long) {
        super.delete(id)
        val rows = props.findAll(perCompanyProps(id))
        if (rows.isNotEmpty()) {
            props.deleteAll(rows)
            audit.log(AuditType.DELETE, "CCFA_SYSTEM_PROP DELETE 고객사 " + id + " (" + rows.size + "건)")
        }
        val aaidRows = criteria.deleteAllFor(id)
        if (aaidRows > 0) {
            audit.log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 " + id + " (" + aaidRows + "건)")
        }
    }

    /** VENDOR_CODE 로 고객사를 찾는다. 중복 검사용 — DB 에 유니크 제약이 없어 화면에서 막는다. */
    @Transactional(readOnly = true)
    fun findByVendorCode(vendorCode: String?): Optional<CcfaCompany> {
        if (vendorCode == null || vendorCode.all(Character::isWhitespace)) return Optional.empty()
        return (repository as CcfaCompanyRepository).findFirstByVendorCode(vendorCode.trim { it <= ' ' })
    }

    private fun copyPerCompanyProps(companyIdx: Long?): Int {
        // 회사 0 자체를 만드는 경우는 없지만, 있더라도 자기 자신에게 복사하지 않는다.
        if (companyIdx == null || companyIdx == 0L) return 0
        val now = LocalDateTime.now()
        var copied = 0
        for (template in props.findAll(perCompanyProps(0L))) {
            val id = CcfaSystemPropId(template.id!!.propKey, companyIdx)
            // 같은 키가 이미 있으면 그대로 둔다 — 복사는 기본값 제공이지 덮어쓰기가 아니다.
            if (props.existsById(id)) continue
            val row = CcfaSystemProp()
            row.id = id
            row.propValue = template.propValue
            row.shareType = SHARE_TYPE_PER_COMPANY
            row.updatedtime = now
            props.save(row)
            copied++
        }
        return copied
    }

    override fun beforeDelete(e: CcfaCompany) {
        if (e.idx != null && e.idx == 0L) throw IllegalStateException("전역(IDX 0) 고객사는 삭제할 수 없습니다.")
        val deps = ArrayList<String>()
        val a = appids.countByCompanyIdx(e.idx); if (a > 0) deps.add("앱 ID " + a + "건")
        val u = users.countByCompanyIdx(e.idx); if (u > 0) deps.add("사용자 " + u + "건")
        val m = managers.countByCompanyIdx(e.idx); if (m > 0) deps.add("운영자 " + m + "건")
        if (deps.isNotEmpty()) throw IllegalStateException("하위 데이터가 있어 삭제할 수 없습니다: " + deps.joinToString(", "))
    }

    companion object {
        /** 고객사별로 따로 갖는 설정. 회사 0 의 이 값들이 새 고객사의 기본값이 된다. */
        internal const val SHARE_TYPE_PER_COMPANY = "NO"

        /** 종료일을 비워 두면 "기한 없음"으로 본다. 스키마 ENDTIME 기본값과 같은 값이다. */
        private val DEFAULT_ENDTIME: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)

        private fun perCompanyProps(companyIdx: Long?): Specification<CcfaSystemProp> =
            Specs.all(Specs.eq("id.companyIdx", companyIdx), Specs.eq("shareType", SHARE_TYPE_PER_COMPANY))
    }
}
