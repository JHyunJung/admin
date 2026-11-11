package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import java.time.LocalDateTime
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasswordChangeService(
    private val managers: CcfaManagerRepository,
    private val encoder: PasswordEncoder,
    private val audit: AuditLogger,
    private val expiry: PasswordExpiryPolicy,
) {

    @Transactional
    fun change(userId: String?, current: String?, next: String?) {
        val m = managers.findByUserId(userId)
            .orElseThrow { IllegalArgumentException("운영자를 찾을 수 없습니다.") }
        if (!encoder.matches(current, m.userPw)) {
            throw IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.")
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
}
