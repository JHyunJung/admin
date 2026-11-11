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
import com.crosscert.fidoadmin.system.web.isReservedPathId
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
        val client = fidoClients.findFirstByServernameOrderByCreatedtimeAsc(storedName(serverName))
            .orElseGet { newClient(serverName, now) }
        client.serverurl = cut(serverUrl, SERVERURL_MAX_BYTES)
        client.status = STATUS_ON
        client.updatedtime = now
        return fidoClients.save(client)
    }

    /**
     * 처음 등록하는 서버의 행. 보내오는 식별자는 하나뿐이라 SERVERCODE 에도 같은 값을 넣는다.
     * SERVERCODE 는 PK 이자 /system/fido-clients 화면의 URL 경로라 길이를 맞춰 자른다.
     *
     * 잘린 SERVERCODE 가 경로 예약값이거나(상세 화면을 열 수 없다) 다른 SERVERNAME 의 행이 이미 쓰고 있으면
     * (save 가 그 행을 덮어 reload 대상 URL 이 바뀐다) 저장하지 않는다. 이미 등록된 이름의 재등록은 여기를 지나지 않는다.
     */
    private fun newClient(serverName: String?, now: LocalDateTime): CcfaFidoclient {
        val code = cut(serverName, SERVERCODE_MAX_BYTES)
        if (code != null && isReservedPathId(code, RELOAD_PATH)) {
            throw FidoClientRegistrationRejected(FidoClientRegistrationRejected.Reason.RESERVED_CODE, code)
        }
        if (code != null && fidoClients.existsById(code)) {
            throw FidoClientRegistrationRejected(FidoClientRegistrationRejected.Reason.CODE_CONFLICT, code)
        }
        return CcfaFidoclient().apply {
            servercode = code
            servername = storedName(serverName)
            createdtime = now
        }
    }

    /**
     * FIDO 서버 등록 해제. 행을 지우지 않고 STATUS 만 'OFF' 로 내린다.
     * 등록된 적 없는 서버면 아무것도 하지 않는다.
     */
    @Transactional
    fun deregister(serverName: String?): Optional<CcfaFidoclient> =
        fidoClients.findFirstByServernameOrderByCreatedtimeAsc(storedName(serverName))
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

        /** FidoClientController 가 쓰는 리터럴 경로(/system/fido-clients/reload). 폼 등록과 같은 예약값이다. */
        private const val RELOAD_PATH = "reload"

        /**
         * 컬럼 길이를 넘는 값이 와도 등록 자체가 실패하지는 않게 한다.
         * 컬럼은 BYTE 의미라 글자 수가 아니라 UTF-8 바이트로 자른다(한글 서버명이 ORA-12899 로 실패하지 않는다).
         */
        private fun cut(value: String?, maxBytes: Int): String? = Utf8.truncateToBytes(value, maxBytes)

        /**
         * SERVERNAME 은 잘라서 저장하므로 찾을 때도 같은 값으로 찾는다. 1024바이트를 넘는 이름이 재등록·해제 때
         * 자기 행을 못 찾고 새 행(→ SERVERCODE 충돌)이나 404 가 되지 않게 한다. 그 이하 이름은 원래 값 그대로다.
         */
        private fun storedName(serverName: String?): String? = cut(serverName, SERVERNAME_MAX_BYTES)
    }
}

/**
 * 자가등록을 저장하지 않고 거절한다. 컨트롤러가 [reason] 에 따라 400(예약값)·409(SERVERCODE 충돌)로 답한다.
 * 트랜잭션은 롤백되고, 걸어 둔 테이블 잠금도 함께 풀린다.
 */
class FidoClientRegistrationRejected(val reason: Reason, val serverCode: String) :
    RuntimeException("FIDO 서버 자가등록 거절($reason): $serverCode") {
    enum class Reason { RESERVED_CODE, CODE_CONFLICT }
}
