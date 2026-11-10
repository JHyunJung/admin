package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.common.Utf8
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** CCFA_AUDIT_LOG 기록. 실패해도 호출자 트랜잭션을 깨지 않는다. */
@Component
class AuditLogger(
    private val writer: AuditLogWriter,
    private val tenant: TenantContext,
    private val companies: CompanyLookup,
) {

    /**
     * 현재 로그인 사용자·현재 HTTP 요청 기준으로 기록한다. 로그인 사용자가 없으면 기록하지 않는다.
     *
     * COMPANY_IDX 에는 **대상 테넌트**를 넣는다. 행위자는 USER_ID 로 식별된다.
     * 슈퍼관리자가 어느 고객사 데이터를 만졌는지가 이 컬럼에만 남는다
     * (스키마를 바꿀 수 없어 기존 컬럼을 이렇게 해석한다).
     * 선택이 없는 전역 작업은 행위자 소속(0)을 그대로 쓴다 — 사실에 맞다.
     */
    fun log(type: AuditType, message: String?) {
        tenant.current().ifPresent { actor ->
            val req = currentRequest()
            logResolvingTarget(actor, type, message,
                req?.remoteAddr,
                req?.getHeader("User-Agent"))
        }
    }

    /**
     * 대상 테넌트를 여기서 해석한다(`companies.name(...)` 의 DB 조회 포함).
     * 해석과 기록을 같은 try/catch 보호 구역에 두어, 해석 실패도 기록 실패와 똑같이
     * 삼켜야 "실패해도 호출자 트랜잭션을 깨지 않는다"는 클래스 불변식이 지켜진다.
     */
    private fun logResolvingTarget(actor: ManagerUserDetails, type: AuditType, message: String?, ip: String?, ua: String?) {
        swallowingFailure(type, message) {
            val hasTenant = tenant.hasTenant()
            val targetCompany = if (hasTenant) tenant.companyIdx() else actor.companyIdx
            val targetName = if (hasTenant) companies.name(targetCompany) else actor.companyName
            writeRow(actor, type, message, targetCompany, targetName, ip, ua)
        }
    }

    /** 로그인·로그아웃처럼 테넌트가 없는 사건용. 행위자 소속을 그대로 쓴다. */
    fun log(actor: ManagerUserDetails, type: AuditType, message: String?, ip: String?, ua: String?) {
        swallowingFailure(type, message) {
            writeRow(actor, type, message, actor.companyIdx, actor.companyName, ip, ua)
        }
    }

    /** 기록 실패를 삼키고 남긴다. 호출자 트랜잭션을 깨지 않는다는 클래스 불변식의 한 곳. */
    private inline fun swallowingFailure(type: AuditType, message: String?, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e)
        }
    }

    private fun writeRow(actor: ManagerUserDetails, type: AuditType, message: String?,
                         companyIdx: Long?, companyName: String?, ip: String?, ua: String?) {
        val row = CcfaAuditLog()
        row.companyIdx = companyIdx
        row.companyName = cut(companyName, MAX_COMPANY_NAME_BYTES)
        row.type = type.name
        row.userId = cut(actor.userId, MAX_USER_ID_BYTES)
        row.userName = cut(actor.userNm, MAX_USER_NAME_BYTES)
        row.message = cut(message, MAX_MESSAGE_BYTES)
        row.ip = cut(ip, MAX_IP_BYTES)
        row.ua = cut(ua, MAX_UA_BYTES)
        // Oracle TIMESTAMP 은 소수점 6자리(마이크로초)까지만 보존한다.
        // 나노초를 그대로 두면 저장 후 읽은 값으로 해시를 재계산할 수 없다.
        row.createdtime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        row.intergrityHash = integrityHash(row)
        writer.write(row)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuditLogger::class.java)

        // CCFA_AUDIT_LOG 컬럼의 VARCHAR2 바이트 한도(CcfaAuditLog 의 @Column length 와 같다).
        private const val MAX_COMPANY_NAME_BYTES = 512
        private const val MAX_USER_ID_BYTES = 64
        private const val MAX_USER_NAME_BYTES = 32
        private const val MAX_MESSAGE_BYTES = 4000
        private const val MAX_IP_BYTES = 15
        private const val MAX_UA_BYTES = 2048

        /**
         * 무결성 해시. 저장된 행에서 동일하게 재계산할 수 있어야 하므로
         * 필드 경계를 길이 접두사로 명시한다(값에 "|" 가 들어와도 모호해지지 않는다).
         * null 과 빈 문자열은 Oracle 이 둘 다 NULL 로 저장하므로 같은 표현으로 맞춘다.
         */
        @JvmStatic
        fun integrityHash(row: CcfaAuditLog): String {
            val sb = StringBuilder()
            field(sb, row.companyIdx.toString())
            field(sb, row.companyName)
            field(sb, row.type)
            field(sb, row.userId)
            field(sb, row.userName)
            field(sb, row.message)
            field(sb, row.ip)
            field(sb, row.ua)
            field(sb, row.createdtime?.toString())
            return Sha256PasswordEncoder.sha256Hex(sb.toString())
        }

        /** Oracle 은 빈 문자열을 NULL 로 저장하므로 둘을 같게 본다. */
        private fun field(sb: StringBuilder, value: String?) {
            if (value.isNullOrEmpty()) {
                sb.append("-|")
            } else {
                sb.append(value.length).append(':').append(value).append('|')
            }
        }

        /**
         * UTF-8 바이트 기준으로 자른다. CCFA_AUDIT_LOG 의 VARCHAR2 는 BYTE 의미라
         * 문자 수로 자르면 한글 입력에서 ORA-12899 가 나고 감사 기록이 유실된다.
         */
        private fun cut(s: String?, maxBytes: Int): String? = Utf8.truncateToBytes(s, maxBytes)
    }
}
