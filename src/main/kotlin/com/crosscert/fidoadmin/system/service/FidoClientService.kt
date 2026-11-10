package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.AssignedIdCrudService
import com.crosscert.fidoadmin.common.Specs
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.common.Utf8
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository
import com.crosscert.fidoadmin.system.web.FidoClientSearchForm
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** CCFA_FIDOCLIENT — 문자열 PK(SERVERCODE). STATUS 기본 'ON'. SUPER 전용. */
@Service
class FidoClientService(
    repository: CcfaFidoclientRepository,
    audit: AuditLogger,
    em: EntityManager,
    tenant: TenantContext,
) : AssignedIdCrudService<CcfaFidoclient, String, FidoClientSearchForm>(repository, audit, em, tenant) {

    private val fidoClients: CcfaFidoclientRepository = repository

    override fun toSpecification(f: FidoClientSearchForm): Specification<CcfaFidoclient> =
        Specs.all(
            Specs.like("servercode", f.servercode),
            Specs.eq("status", f.status),
        )
    override fun companyIdxAttribute(): String? = null
    override fun companyIdxOf(e: CcfaFidoclient): Long? = null
    override fun setCompanyIdx(e: CcfaFidoclient, c: Long?) {}
    override fun idOf(e: CcfaFidoclient): String? = e.servercode
    override fun assignedId(e: CcfaFidoclient): String? = e.servercode
    override fun tableName(): String = "CCFA_FIDOCLIENT"
    override fun defaultSort(): Sort = Sort.by(Sort.Direction.ASC, "servercode")
    override fun sortableProperties(): Set<String> = setOf("servercode", "servername", "status", "updatedtime")

    override fun applyDefaults(e: CcfaFidoclient) {
        val status = e.status
        if (status == null || status.javaIsBlank()) e.status = STATUS_ON
    }
    override fun touchCreated(e: CcfaFidoclient, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: CcfaFidoclient, now: LocalDateTime) { e.updatedtime = now }

    /**
     * FIDO 서버 자가등록. 기동한 FIDO 서버가 스스로 호출한다(로그인 없는 경로).
     *
     * 같은 SERVERNAME 이 이미 있으면 그 행을 갱신한다. 재기동할 때마다 행이 새로 쌓이지 않는다.
     *
     * 테넌트 경계를 타지 않는다 — CCFA_FIDOCLIENT 는 COMPANY_IDX 가 없는 전역 테이블이고,
     * 세션이 없는 이 경로에서 `TenantContext.companyIdx()` 를 부르면 예외가 난다.
     * 같은 이유로 감사 로그(행위자 필요)도 남기지 않는다. 호출 기록은 컨트롤러가 애플리케이션 로그에 남긴다.
     */
    @Transactional
    fun register(serverName: String?, serverUrl: String?): CcfaFidoclient {
        // 존재 검사와 INSERT 사이의 경쟁을 막는다. 이 테이블은 PK 제약이 없어 DB 가 잡아주지 못한다
        // (AssignedIdCrudService.insert 와 같은 방식이다). 잠금은 이 트랜잭션 커밋 때 풀린다.
        lockTable()

        val now = LocalDateTime.now()
        val client = fidoClients.findFirstByServernameOrderByCreatedtimeAsc(serverName)
            .orElseGet {
                CcfaFidoclient().apply {
                    // 보내오는 식별자는 하나뿐이라 SERVERCODE 에도 같은 값을 넣는다.
                    // SERVERCODE 는 PK 이자 /system/fido-clients 화면의 URL 경로라 길이를 맞춰 자른다.
                    servercode = cut(serverName, SERVERCODE_MAX_BYTES)
                    servername = cut(serverName, SERVERNAME_MAX_BYTES)
                    createdtime = now
                }
            }
        client.serverurl = cut(serverUrl, SERVERURL_MAX_BYTES)
        client.status = STATUS_ON
        client.updatedtime = now
        return fidoClients.save(client)
    }

    /**
     * FIDO 서버 등록 해제. 행을 지우지 않고 STATUS 만 'OFF' 로 내린다.
     * 등록된 적 없는 서버면 아무것도 하지 않는다.
     */
    @Transactional
    fun deregister(serverName: String?): Optional<CcfaFidoclient> =
        fidoClients.findFirstByServernameOrderByCreatedtimeAsc(serverName)
            .map { client ->
                client.status = STATUS_OFF
                client.updatedtime = LocalDateTime.now()
                fidoClients.save(client)
            }

    companion object {
        private const val STATUS_ON = "ON"
        private const val STATUS_OFF = "OFF"

        /** CCFA_FIDOCLIENT 컬럼 길이(BYTE). `CcfaFidoclient` 의 `@Column(length)` 와 같은 값이다. */
        private const val SERVERCODE_MAX_BYTES = 64
        private const val SERVERNAME_MAX_BYTES = 1024
        private const val SERVERURL_MAX_BYTES = 2048

        /**
         * 컬럼 길이를 넘는 값이 와도 등록 자체가 실패하지는 않게 한다.
         * 컬럼은 BYTE 의미라 글자 수가 아니라 UTF-8 바이트로 자른다(한글 서버명이 ORA-12899 로 실패하지 않는다).
         */
        private fun cut(value: String?, maxBytes: Int): String? = Utf8.truncateToBytes(value, maxBytes)
    }
}
