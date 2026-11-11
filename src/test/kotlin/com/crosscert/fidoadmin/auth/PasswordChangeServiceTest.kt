package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class PasswordChangeServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val expiry: PasswordExpiryPolicy = mock(PasswordExpiryPolicy::class.java)
    val audit: AuditLogger = mock(AuditLogger::class.java)
    val attempts: LoginAttemptService = mock(LoginAttemptService::class.java)
    val service = PasswordChangeService(managers, Sha256PasswordEncoder(), audit, expiry, attempts)

    private fun manager(): CcfaManager {
        val m = CcfaManager()
        m.userId = "kbadmin"
        m.userPw = "c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461" // Company1234!
        whenever(managers.findByUserIdForUpdate("kbadmin")).thenReturn(Optional.of(m))
        return m
    }

    @Test fun changesHashWhenCurrentMatches() {
        val m = manager()
        service.change("kbadmin", "Company1234!", "NewPass5678!")
        assertThat(m.userPw).isEqualTo(Sha256PasswordEncoder.sha256Hex("NewPass5678!"))
        assertThat(m.updatedtime).isNotNull()
        verify(expiry).touch("kbadmin")
    }

    /** 자기 계정의 일이라 SUPER 가 고객사를 선택 중이어도 그 고객사 감사 로그에 남기지 않는다(행위자 소속). */
    @Test fun auditsAsActorNotSelectedTenant() {
        manager()
        service.change("kbadmin", "Company1234!", "NewPass5678!")
        org.mockito.Mockito.verify(audit).logGlobal(AuditType.UPDATE, "CCFA_MANAGER PASSWORD kbadmin")
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).log(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull())
    }

    @Test fun rejectsSameAsCurrent() {
        val m = manager()
        assertThatThrownBy { service.change("kbadmin", "Company1234!", "Company1234!") }
            .isInstanceOf(SamePasswordException::class.java).hasMessageContaining("다른 비밀번호")
        verify(managers, never()).save(any<CcfaManager>())
        verify(expiry, never()).touch(anyOrNull())
        assertThat(m.userPw).isEqualTo("c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461")
    }

    @Test fun rejectsWrongCurrent() {
        manager()
        assertThatThrownBy { service.change("kbadmin", "wrong", "NewPass5678!") }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("현재 비밀번호")
        verify(expiry, never()).touch(anyOrNull())
    }

    /** 현재 비밀번호 오입력은 로그인 실패와 같은 카운터로 센다(진단 80). 탈취한 세션으로 무제한 대조하지 못하게 한다. */
    @Test fun wrongCurrentCountsAsLoginFailure() {
        manager()
        assertThatThrownBy { service.change("kbadmin", "wrong", "NewPass5678!") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(attempts).onFailure("kbadmin")
    }

    /** 실패 집계가 롤백되지 않아야 한다 — 오입력 예외는 트랜잭션을 되돌리지 않는다. */
    @Test fun wrongCurrentDoesNotRollBackFailureCount() {
        val tx = PasswordChangeService::class.java.getMethod("change", String::class.java, String::class.java, String::class.java)
            .getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        assertThat(tx.noRollbackFor.toList()).contains(WrongCurrentPasswordException::class)
    }

    /** 잠긴 계정은 대조하지 않는다. 잠긴 뒤에도 대조하면 카운터만 멈추고 대입은 계속된다. */
    @Test fun lockedAccountIsNotCompared() {
        val m = manager()
        whenever(attempts.isLockedNow(org.mockito.kotlin.eq("kbadmin"), any())).thenReturn(true)
        assertThatThrownBy { service.change("kbadmin", "Company1234!", "NewPass5678!") }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("잠겨")
        verify(attempts, never()).onFailure(anyOrNull())
        assertThat(m.userPw).isEqualTo("c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461")
    }

    @Test fun successDoesNotCountFailure() {
        manager()
        service.change("kbadmin", "Company1234!", "NewPass5678!")
        verify(attempts, never()).onFailure(anyOrNull())
    }
}
