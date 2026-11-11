package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasswordChangeService(
    private val managers: CcfaManagerRepository,
    private val encoder: PasswordEncoder,
    private val audit: AuditLogger,
    private val expiry: PasswordExpiryPolicy,
    private val attempts: LoginAttemptService,
) {

    /**
     * 현재 비밀번호를 확인하고 바꾼다.
     *
     * 행을 FOR UPDATE 로 읽는다 — 동시에 도는 운영자 수정·로그인 집계가 이 변경을 옛 값으로 덮지 않게 한다.
     * 현재 비밀번호 오입력은 로그인 실패와 같은 카운터로 세고([LoginAttemptService.onFailure]),
     * 잠긴 계정은 대조하지 않는다. 그러지 않으면 탈취한 세션으로 현재 비밀번호를 무제한 대입할 수 있다.
     */
    @Transactional(noRollbackFor = [WrongCurrentPasswordException::class])
    fun change(userId: String?, current: String?, next: String?) {
        val m = managers.findByUserIdForUpdate(userId)
            .orElseThrow { IllegalArgumentException("운영자를 찾을 수 없습니다.") }
        if (attempts.isLockedNow(userId, LocalDateTime.now())) {
            throw IllegalArgumentException("계정이 잠겨 있어 비밀번호를 변경할 수 없습니다.")
        }
        if (!encoder.matches(current, m.userPw)) {
            attempts.onFailure(userId)
            log.warn("비밀번호 변경 실패(현재 비밀번호 불일치): userId={}", userId)
            throw WrongCurrentPasswordException("현재 비밀번호가 올바르지 않습니다.")
        }
        if (encoder.matches(next, m.userPw)) {
            throw SamePasswordException("현재 비밀번호와 다른 비밀번호를 입력하세요.")
        }
        m.userPw = encoder.encode(next)
        m.updatedtime = LocalDateTime.now()
        managers.save(m)
        expiry.touch(userId)
        // 자기 계정의 일이라 선택 고객사가 아니라 행위자 소속으로 남긴다(COMPANY 는 둘이 같다).
        audit.logGlobal(AuditType.UPDATE, "CCFA_MANAGER PASSWORD $userId")
    }

    private companion object {
        private val log = LoggerFactory.getLogger(PasswordChangeService::class.java)
    }
}
