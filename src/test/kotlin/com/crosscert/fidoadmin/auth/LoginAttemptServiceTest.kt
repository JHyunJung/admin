package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class LoginAttemptServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val policies: CcfaManagerPwPolicyRepository = mock(CcfaManagerPwPolicyRepository::class.java)
    val props: CcfaSystemPropRepository = mock(CcfaSystemPropRepository::class.java)
    val service = LoginAttemptService(managers, policies, props, 5, 30)
    val manager = CcfaManager()

    @BeforeEach fun setUp() {
        manager.userId = "kbadmin"
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager))
        whenever(managers.findByUserIdForUpdate("kbadmin")).thenReturn(Optional.of(manager))
        whenever(props.findById(CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.empty())
    }

    /**
     * Spring Security 의 UsernamePasswordAuthenticationFilter 는 username 을 trim 한다.
     * 실패 집계가 원문을 그대로 쓰면 " kbadmin" 으로 kbadmin 의 비밀번호를 무제한 시도하면서도
     * 잠금 카운터는 올라가지 않는다. 같은 정규화를 적용해야 한다.
     */
    @Test fun failureNormalizesUsernameSoLockoutCannotBeBypassed() {
        val policy = CcfaManagerPwPolicy()
        policy.userId = "kbadmin"; policy.pwFailCnt = 0L; policy.accountLock = "N"
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy))

        service.onFailure("  kbadmin  ")

        assertThat(policy.pwFailCnt).isEqualTo(1L)
    }

    @Test fun successResetsCounterAndMarksOnline() {
        val policy = CcfaManagerPwPolicy()
        policy.userId = "kbadmin"; policy.pwFailCnt = 3L; policy.accountLock = "N"
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy))

        service.onSuccess("kbadmin")

        assertThat(policy.pwFailCnt).isZero()
        assertThat(manager.login).isEqualTo("ON-LINE")
        assertThat(manager.lastAccess).isNotNull()
        verify(policies).save(policy)
        verify(managers).save(manager)
    }

    @Test fun failureIncrementsAndLocksAtLimit() {
        val policy = CcfaManagerPwPolicy()
        policy.userId = "kbadmin"; policy.pwFailCnt = 4L; policy.accountLock = "N"
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy))

        service.onFailure("kbadmin")

        assertThat(policy.pwFailCnt).isEqualTo(5L)
        assertThat(policy.accountLock).isEqualTo("Y")
        assertThat(manager.blockTime).isNotNull()
    }

    @Test fun failureCreatesPolicyRowWhenMissing() {
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())

        service.onFailure("kbadmin")

        val captor = ArgumentCaptor.forClass(CcfaManagerPwPolicy::class.java)
        verify(policies).save(captor.capture())
        assertThat(captor.value.userId).isEqualTo("kbadmin")
        assertThat(captor.value.pwFailCnt).isEqualTo(1L)
        assertThat(captor.value.accountLock).isEqualTo("N")
        assertThat(captor.value.createdtime).isNotNull()
    }

    @Test fun failureForUnknownUserDoesNothing() {
        whenever(managers.findByUserIdForUpdate("nobody")).thenReturn(Optional.empty())
        service.onFailure("nobody")
        verify(policies, never()).save(anyOrNull<CcfaManagerPwPolicy>())
    }

    @Test fun failLimitPrefersSystemProp() {
        val prop = CcfaSystemProp()
        prop.id = CcfaSystemPropId("PW_FAIL_LIMIT", 0L); prop.propValue = "3"
        whenever(props.findById(CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop))
        assertThat(service.failLimit()).isEqualTo(3)
    }

    @Test fun failLimitFallsBackOnGarbage() {
        val prop = CcfaSystemProp()
        prop.id = CcfaSystemPropId("PW_FAIL_LIMIT", 0L); prop.propValue = "abc"
        whenever(props.findById(CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop))
        assertThat(service.failLimit()).isEqualTo(5)
    }

    @Test fun unlockClearsLockAndCounter() {
        val policy = CcfaManagerPwPolicy()
        policy.userId = "kbadmin"; policy.pwFailCnt = 5L; policy.accountLock = "Y"
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy))
        manager.blockTime = LocalDateTime.now()

        service.unlock("kbadmin")

        assertThat(policy.accountLock).isEqualTo("N")
        assertThat(policy.pwFailCnt).isZero()
        assertThat(manager.blockTime).isNull()
    }

    private fun lockedPolicy(lockedAt: LocalDateTime?): CcfaManagerPwPolicy {
        val p = CcfaManagerPwPolicy()
        p.userId = "kbadmin"; p.accountLock = "Y"; p.pwFailCnt = 5L; p.updatedtime = lockedAt
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p))
        return p
    }

    /** 30분 자동 해제를 로그인 시도 시점에 판정한다. */
    @Test fun lockExpiresAfterLockMinutes() {
        val now = LocalDateTime.of(2026, 9, 27, 12, 0)
        assertThat(service.isLocked(lockedPolicy(now.minusMinutes(31)), now)).isFalse()
        assertThat(service.isLocked(lockedPolicy(now.minusMinutes(10)), now)).isTrue()
    }

    /** 언제 잠겼는지 모르면 잠긴 것으로 둔다 — 풀어 주는 쪽이 위험하다. */
    @Test fun lockWithoutTimestampStaysLocked() {
        assertThat(service.isLocked(lockedPolicy(null), LocalDateTime.now())).isTrue()
    }

    /** 만료된 잠금 뒤의 첫 실패는 처음부터 다시 센다. 이어 세면 한 번만 틀려도 즉시 다시 잠긴다. */
    @Test fun failureAfterExpiredLockRestartsTheCount() {
        val p = lockedPolicy(LocalDateTime.now().minusMinutes(31))

        service.onFailure("kbadmin")

        assertThat(p.accountLock).isEqualTo("N")
        assertThat(p.pwFailCnt).isEqualTo(1L)
    }

    /** 잠긴 동안의 실패는 잠금 시각을 밀지 않는다. 밀면 계속 두드리는 한 영원히 풀리지 않는다. */
    @Test fun failureWhileLockedDoesNotExtendTheLock() {
        val lockedAt = LocalDateTime.now().minusMinutes(5)
        val p = lockedPolicy(lockedAt)

        service.onFailure("kbadmin")

        assertThat(p.updatedtime).isEqualTo(lockedAt)
        assertThat(p.pwFailCnt).isEqualTo(5L)
        assertThat(p.accountLock).isEqualTo("Y")
    }

    /** 만료된 잠금으로 들어온 로그인이 성공하면 그때 실제로 푼다. */
    @Test fun successClearsLockFlag() {
        val p = lockedPolicy(LocalDateTime.now().minusMinutes(31))

        service.onSuccess("kbadmin")

        assertThat(p.accountLock).isEqualTo("N")
        assertThat(p.pwFailCnt).isZero()
        assertThat(manager.blockTime).isNull()
    }
}
