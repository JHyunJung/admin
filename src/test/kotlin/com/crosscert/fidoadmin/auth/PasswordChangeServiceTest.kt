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
    val service = PasswordChangeService(managers, Sha256PasswordEncoder(), audit, expiry)

    private fun manager(): CcfaManager {
        val m = CcfaManager()
        m.userId = "kbadmin"
        m.userPw = "c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461" // Company1234!
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m))
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
}
