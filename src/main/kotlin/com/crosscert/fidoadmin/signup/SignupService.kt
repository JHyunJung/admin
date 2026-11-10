package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.common.isNullOrJavaBlank
import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.common.Utf8
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.manager.service.ManagerUserIdGuard
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 가입 신청 저장과 승인·거절. 신청은 인증 없이 호출되므로 `common.CrudService` 를
 * 상속하지 않는다. `CrudService.create()` 는 테넌트가 있는 테이블이면
 * `tenant.companyIdx()` 를 부르고, 이는 `require()` 로 이어져
 * 로그인 사용자가 없으면 예외를 던진다.
 *
 * 감사 로그는 `apply()` 에만 남지 않는다. AuditLogger 는 로그인 사용자가 없으면
 * 조용히 기록을 건너뛰는데, 신청은 익명 호출이기 때문이다(의도된 동작).
 * 승인·거절은 로그인한 슈퍼 관리자가 수행하므로 정상적으로 기록된다.
 */
@Service
class SignupService(
    private val managers: CcfaManagerRepository,
    private val companies: CcfaCompanyRepository,
    private val userIdGuard: ManagerUserIdGuard,
    private val audit: AuditLogger,
    private val expiry: PasswordExpiryPolicy,
) {

    @Transactional(readOnly = true)
    fun existsUserId(userId: String?): Boolean = managers.findByUserId(userId).isPresent

    /**
     * 신청을 저장한다. 상태와 소속은 입력과 무관하게 강제된다(권한 상승 차단 1단계).
     * 상태·소속을 받을 파라미터 자체가 없으므로 호출자가 값을 끼워 넣을 통로가 없다.
     *
     * USER_ID 에 DB 유니크 제약이 없어 동시 신청 시 중복이 생길 수 있다. 잠금 → 존재 검사 → INSERT 는
     * 운영자 등록 화면과 같은 [ManagerUserIdGuard] 가 맡는다 — "USER_ID 는 전역 유일" 을 지키는
     * 코드가 한 곳이어야 한다. 가입은 드물게 일어나므로 테이블 잠금 비용은 수용 가능하다.
     */
    @Transactional
    fun apply(userId: String?, encodedPw: String?, userNm: String?,
              userEmail: String?, userPhone: String?, reason: String?): CcfaManager {
        val now = LocalDateTime.now()
        val m = CcfaManager()
        // IDX 는 CCFA_MANAGER_SEQ 가 채번한다. 직접 넣지 않는다.
        m.userId = userId
        m.userPw = encodedPw
        m.userNm = userNm
        m.userEmail = userEmail
        m.userPhone = userPhone
        // 승인 전까지 로그인 불가. 소속은 미배정(-1)이며 SUPER 인 0 이 될 수 없다.
        m.status = SignupPolicy.STATUS_PENDING
        m.companyIdx = SignupPolicy.UNASSIGNED_COMPANY_IDX
        m.login = ManagerStatus.LOGIN_OFFLINE
        m.alramType = ManagerStatus.ALARM_TYPE_NONE
        m.alramLevel = ManagerStatus.ALARM_LEVEL_DEFAULT
        m.etc = if (reason.isNullOrJavaBlank()) null else "신청 사유: $reason"
        m.createdtime = now
        m.updatedtime = now
        return userIdGuard.insert(m)
    }

    /** 승인 대기 목록. 오래된 신청이 위로 온다. */
    @Transactional(readOnly = true)
    fun pending(): List<CcfaManager> = managers.findByStatusOrderByCreatedtimeAsc(SignupPolicy.STATUS_PENDING)

    /**
     * 승인: 소속을 배정하고 활성화한다.
     *
     * 권한 상승 차단 2단계 — 전역(IDX 0) 배정을 거부한다. 승인으로 슈퍼 관리자를
     * 만들 수 없다. 미배정 표식(-1)이나 null 로도 승인할 수 없다.
     * 권한 상승 차단 3단계 — 대상이 승인대기일 때만 동작한다([lockPending]).
     *
     * 실재하지 않는 고객사 IDX 도 거부한다. CCFA_MANAGER 에는 CCFA_COMPANY 로 가는
     * 외래키가 없어 DB 가 걸러 주지 않는다. 그대로 두면 활성 계정이 존재하지 않는 테넌트에
     * 묶여 로그인은 되지만 테넌트로 걸러지는 모든 조회가 빈 결과를 돌려주고,
     * 신청은 대기 목록에서 사라져 운영자가 다시 처리할 수도 없다.
     *
     * 고객사 존재 확인에는 `CompanyLookup` 이 아니라 리포지터리를 직접 쓴다.
     * CompanyLookup 은 로그인 사용자 기준(`TenantContext`)으로 결과를 가리는
     * 화면용 조회라, 없는 고객사와 볼 권한이 없는 고객사를 똑같이 "#IDX" 로 돌려준다.
     * 존재 여부를 판정할 수 없을뿐더러 서비스 계층에 웹 요청 컨텍스트 의존이 끼어든다.
     *
     * 모든 검증은 [lockPending] 이 행을 건드리기 전에 끝난다. 거부된 승인은
     * 행을 승인대기 그대로 남긴다.
     */
    @Transactional
    fun approve(idx: Long?, companyIdx: Long?): CcfaManager {
        if (companyIdx != null && companyIdx == SignupPolicy.SUPER_COMPANY_IDX) {
            throw IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다.")
        }
        if (companyIdx == null || companyIdx < 0) {
            throw IllegalArgumentException("고객사를 선택해야 합니다.")
        }
        if (!companies.existsById(companyIdx)) {
            throw IllegalArgumentException("존재하지 않는 고객사입니다: $companyIdx")
        }
        val m = lockPending(idx)
        m.companyIdx = companyIdx
        m.status = ManagerStatus.ACTIVE
        m.updatedtime = LocalDateTime.now()
        val saved = managers.save(m)
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP APPROVE " + saved.userId +
            " -> COMPANY_IDX " + companyIdx)
        expiry.touch(saved.userId)
        return saved
    }

    /** 거절: 상태를 바꾸고 사유를 ETC 에 덧붙인다. 소속은 미배정 그대로 둔다. */
    @Transactional
    fun reject(idx: Long?, reason: String?): CcfaManager {
        val m = lockPending(idx)
        m.status = SignupPolicy.STATUS_REJECTED
        m.etc = appendReason(m.etc, reason)
        m.updatedtime = LocalDateTime.now()
        val saved = managers.save(m)
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP REJECT " + saved.userId)
        return saved
    }

    /**
     * 대상 행을 FOR UPDATE 로 잠그고 **잠근 뒤에** 승인대기인지 확인한다.
     * 잠그기 전에 읽으면 동시에 들어온 두 처리가 둘 다 검사를 통과한다.
     * 운영자 잠금 해제(`ManagerService.unlock`)와 같은 방식이다.
     */
    private fun lockPending(idx: Long?): CcfaManager {
        val m = managers.findByIdxForUpdate(idx)
            .orElseThrow { IllegalArgumentException("가입 신청을 찾을 수 없습니다: $idx") }
        if (SignupPolicy.STATUS_PENDING != m.status) {
            throw IllegalStateException(
                "이미 처리된 신청입니다(승인대기 상태가 아닙니다): " + m.userId)
        }
        return m
    }

    private companion object {
        /** CCFA_MANAGER.ETC 의 크기. 문자 수가 아니라 **바이트** 다(한글 1자 = 3바이트). */
        private const val ETC_MAX_BYTES = 2048

        /**
         * 거절 사유를 ETC 뒤에 덧붙인다. ETC 는 문자 수가 아니라 바이트로 제한되므로
         * 문자 수로 자르면 한글 입력에서 ORA-12899 로 저장이 실패한다.
         */
        fun appendReason(etc: String?, reason: String?): String? {
            val added = "거절 사유: " + (if (reason.isNullOrJavaBlank()) "(사유 없음)" else reason)
            val merged = if (etc.isNullOrJavaBlank()) added else etc + "\n" + added
            return Utf8.truncateToBytes(merged, ETC_MAX_BYTES)
        }
    }
}
