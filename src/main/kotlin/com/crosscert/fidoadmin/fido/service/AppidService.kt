package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Appid
import com.crosscert.fidoadmin.fido.repository.AppidRepository
import com.crosscert.fidoadmin.fido.web.AppidSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

/** APPID. COMPANY_IDX 로 테넌트 격리. DB 기본값 STATUS='use', DEVICE_DEFAULT='F'. */
@Service
class AppidService(
    repository: AppidRepository,
    audit: AuditLogger,
    tenant: TenantContext,
    private val events: ApplicationEventPublisher,
) : CrudService<Appid, Long, AppidSearchForm>(repository, audit, tenant) {

    /** FIDO 서버가 AppID 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다. */
    override fun afterChange(action: String, e: Appid) {
        events.publishEvent(FidoConfigChanged(tableName() + " " + action + " " + idOf(e)))
    }

    override fun toSpecification(f: AppidSearchForm): Specification<Appid> =
        Specs.all(
            Specs.like("appid", f.appid),
            Specs.like("servicename", f.servicename),
            Specs.eq("status", f.status),
        )
    override fun companyIdxAttribute(): String? = "companyIdx"
    override fun companyIdxOf(e: Appid): Long? = e.companyIdx
    override fun setCompanyIdx(e: Appid, c: Long?) { e.companyIdx = c }
    override fun idOf(e: Appid): String = e.idx.toString()
    override fun tableName(): String = "APPID"
    override fun sortableProperties(): Set<String> = setOf("idx", "appid", "servicename", "createdtime")

    override fun applyDefaults(e: Appid) {
        if (e.status.isNullOrBlank()) e.status = "use"
        if (e.deviceDefault.isNullOrBlank()) e.deviceDefault = "F"
    }
    override fun touchCreated(e: Appid, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: Appid, now: LocalDateTime) { e.updatedtime = now }
}
