package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginAttemptServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaSystemPropRepository props = mock(CcfaSystemPropRepository.class);
    LoginAttemptService service = new LoginAttemptService(managers, policies, props, 5, 30);
    CcfaManager manager = new CcfaManager();

    @BeforeEach void setUp() {
        manager.setUserId("kbadmin");
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager));
        when(managers.findByUserIdForUpdate("kbadmin")).thenReturn(Optional.of(manager));
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.empty());
    }

    /**
     * Spring Security 의 UsernamePasswordAuthenticationFilter 는 username 을 trim 한다.
     * 실패 집계가 원문을 그대로 쓰면 " kbadmin" 으로 kbadmin 의 비밀번호를 무제한 시도하면서도
     * 잠금 카운터는 올라가지 않는다. 같은 정규화를 적용해야 한다.
     */
    @Test void failureNormalizesUsernameSoLockoutCannotBeBypassed() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(0L); policy.setAccountLock("N");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));

        service.onFailure("  kbadmin  ");

        assertThat(policy.getPwFailCnt()).isEqualTo(1L);
    }

    @Test void successResetsCounterAndMarksOnline() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(3L); policy.setAccountLock("N");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));

        service.onSuccess("kbadmin");

        assertThat(policy.getPwFailCnt()).isZero();
        assertThat(manager.getLogin()).isEqualTo("ON-LINE");
        assertThat(manager.getLastAccess()).isNotNull();
        verify(policies).save(policy);
        verify(managers).save(manager);
    }

    @Test void failureIncrementsAndLocksAtLimit() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(4L); policy.setAccountLock("N");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));

        service.onFailure("kbadmin");

        assertThat(policy.getPwFailCnt()).isEqualTo(5L);
        assertThat(policy.getAccountLock()).isEqualTo("Y");
        assertThat(manager.getBlockTime()).isNotNull();
    }

    @Test void failureCreatesPolicyRowWhenMissing() {
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());

        service.onFailure("kbadmin");

        ArgumentCaptor<CcfaManagerPwPolicy> captor = ArgumentCaptor.forClass(CcfaManagerPwPolicy.class);
        verify(policies).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("kbadmin");
        assertThat(captor.getValue().getPwFailCnt()).isEqualTo(1L);
        assertThat(captor.getValue().getAccountLock()).isEqualTo("N");
        assertThat(captor.getValue().getCreatedtime()).isNotNull();
    }

    @Test void failureForUnknownUserDoesNothing() {
        when(managers.findByUserIdForUpdate("nobody")).thenReturn(Optional.empty());
        service.onFailure("nobody");
        verify(policies, org.mockito.Mockito.never()).save(any());
    }

    @Test void failLimitPrefersSystemProp() {
        CcfaSystemProp prop = new CcfaSystemProp();
        prop.setId(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)); prop.setPropValue("3");
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop));
        assertThat(service.failLimit()).isEqualTo(3);
    }

    @Test void failLimitFallsBackOnGarbage() {
        CcfaSystemProp prop = new CcfaSystemProp();
        prop.setId(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)); prop.setPropValue("abc");
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop));
        assertThat(service.failLimit()).isEqualTo(5);
    }

    @Test void unlockClearsLockAndCounter() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(5L); policy.setAccountLock("Y");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));
        manager.setBlockTime(java.time.LocalDateTime.now());

        service.unlock("kbadmin");

        assertThat(policy.getAccountLock()).isEqualTo("N");
        assertThat(policy.getPwFailCnt()).isZero();
        assertThat(manager.getBlockTime()).isNull();
    }

    private CcfaManagerPwPolicy lockedPolicy(LocalDateTime lockedAt) {
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy();
        p.setUserId("kbadmin"); p.setAccountLock("Y"); p.setPwFailCnt(5L); p.setUpdatedtime(lockedAt);
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        return p;
    }

    /** 30분 자동 해제를 로그인 시도 시점에 판정한다. */
    @Test void lockExpiresAfterLockMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        assertThat(service.isLocked(lockedPolicy(now.minusMinutes(31)), now)).isFalse();
        assertThat(service.isLocked(lockedPolicy(now.minusMinutes(10)), now)).isTrue();
    }

    /** 언제 잠겼는지 모르면 잠긴 것으로 둔다 — 풀어 주는 쪽이 위험하다. */
    @Test void lockWithoutTimestampStaysLocked() {
        assertThat(service.isLocked(lockedPolicy(null), LocalDateTime.now())).isTrue();
    }

    /** 만료된 잠금 뒤의 첫 실패는 처음부터 다시 센다. 이어 세면 한 번만 틀려도 즉시 다시 잠긴다. */
    @Test void failureAfterExpiredLockRestartsTheCount() {
        CcfaManagerPwPolicy p = lockedPolicy(LocalDateTime.now().minusMinutes(31));

        service.onFailure("kbadmin");

        assertThat(p.getAccountLock()).isEqualTo("N");
        assertThat(p.getPwFailCnt()).isEqualTo(1L);
    }

    /** 잠긴 동안의 실패는 잠금 시각을 밀지 않는다. 밀면 계속 두드리는 한 영원히 풀리지 않는다. */
    @Test void failureWhileLockedDoesNotExtendTheLock() {
        LocalDateTime lockedAt = LocalDateTime.now().minusMinutes(5);
        CcfaManagerPwPolicy p = lockedPolicy(lockedAt);

        service.onFailure("kbadmin");

        assertThat(p.getUpdatedtime()).isEqualTo(lockedAt);
        assertThat(p.getPwFailCnt()).isEqualTo(5L);
        assertThat(p.getAccountLock()).isEqualTo("Y");
    }

    /** 만료된 잠금으로 들어온 로그인이 성공하면 그때 실제로 푼다. */
    @Test void successClearsLockFlag() {
        CcfaManagerPwPolicy p = lockedPolicy(LocalDateTime.now().minusMinutes(31));

        service.onSuccess("kbadmin");

        assertThat(p.getAccountLock()).isEqualTo("N");
        assertThat(p.getPwFailCnt()).isZero();
        assertThat(manager.getBlockTime()).isNull();
    }
}
