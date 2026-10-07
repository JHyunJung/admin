package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 성공/실패에 따른 CCFA_MANAGER, CCFA_MANAGER_PW_POLICY 갱신. */
@Slf4j
@Service
public class LoginAttemptService {

    static final String PROP_FAIL_LIMIT = "PW_FAIL_LIMIT";
    static final String PROP_LOCK_MINUTES = "PW_LOCK_MINUTES";

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final CcfaSystemPropRepository props;
    private final int defaultFailLimit;
    private final int defaultLockMinutes;

    public LoginAttemptService(CcfaManagerRepository managers, CcfaManagerPwPolicyRepository policies,
                               CcfaSystemPropRepository props,
                               @Value("${fido-admin.password-fail-limit:5}") int defaultFailLimit,
                               @Value("${fido-admin.password-lock-minutes:30}") int defaultLockMinutes) {
        this.managers = managers;
        this.policies = policies;
        this.props = props;
        this.defaultFailLimit = defaultFailLimit;
        this.defaultLockMinutes = defaultLockMinutes;
    }

    public int failLimit() {
        return intProp(PROP_FAIL_LIMIT, defaultFailLimit);
    }

    /**
     * 잠금이 저절로 풀리기까지의 시간(분). 0 이하면 자동 해제하지 않는다.
     *
     * <p>잠금은 30분이 지나면 풀린다.
     * 여기에는 스케줄러가 없으므로 로그인 시도 시점에 "잠근 지 N분이 지났는가"로 판정한다.
     * 결과는 같고, 아무도 시도하지 않는 계정을 위해 배치를 돌릴 필요가 없다.
     */
    public int lockMinutes() {
        return intProp(PROP_LOCK_MINUTES, defaultLockMinutes);
    }

    private int intProp(String key, int fallback) {
        return props.findById(new CcfaSystemPropId(key, 0L))
            .map(p -> {
                try { return Integer.parseInt(p.getPropValue().trim()); }
                catch (RuntimeException e) { return fallback; }
            })
            .orElse(fallback);
    }

    /**
     * 지금 잠겨 있는가. ACCOUNT_LOCK='Y' 여도 잠근 지 {@link #lockMinutes()} 가 지났으면 풀린 것으로 본다.
     *
     * <p>잠근 시각은 UPDATEDTIME 으로 본다 — 잠글 때 그 값을 함께 갱신하고, 잠긴 동안에는
     * 실패해도 갱신하지 않으므로({@link #onFailure}) 잠금 시각이 뒤로 밀리지 않는다.
     * 시각이 없으면 언제 잠겼는지 모르므로 잠긴 것으로 둔다(풀어 주는 쪽이 위험하다).
     */
    public boolean isLocked(CcfaManagerPwPolicy policy, LocalDateTime now) {
        if (policy == null || !"Y".equalsIgnoreCase(policy.getAccountLock())) return false;
        int minutes = lockMinutes();
        if (minutes <= 0) return true;
        LocalDateTime lockedAt = policy.getUpdatedtime();
        if (lockedAt == null) return true;
        return lockedAt.plusMinutes(minutes).isAfter(now);
    }

    /**
     * Spring Security 의 UsernamePasswordAuthenticationFilter 가 username 을 trim 하므로
     * 집계 쪽도 같은 정규화를 해야 한다. 그러지 않으면 " kbadmin" 처럼 공백을 붙여
     * 실제 계정의 비밀번호를 시도하면서 잠금 카운터만 피해 갈 수 있다.
     */
    static String normalize(String userId) {
        return userId == null ? null : userId.trim();
    }

    @Transactional
    public void onSuccess(String rawUserId) {
        String userId = normalize(rawUserId);
        managers.findByUserIdForUpdate(userId).ifPresent(m -> {
            LocalDateTime now = LocalDateTime.now();
            m.setLogin("ON-LINE");
            m.setLastAccess(now);
            // 만료된 잠금으로 들어온 로그인이면 여기서 실제로 푼다.
            m.setBlockTime(null);
            m.setUpdatedtime(now);
            managers.save(m);
            CcfaManagerPwPolicy p = policyOrNew(userId, now);
            p.setPwFailCnt(0L);
            p.setAccountLock("N");
            p.setUpdatedtime(now);
            policies.save(p);
        });
    }

    @Transactional
    public void onFailure(String rawUserId) {
        String userId = normalize(rawUserId);
        managers.findByUserIdForUpdate(userId).ifPresent(m -> {
            LocalDateTime now = LocalDateTime.now();
            CcfaManagerPwPolicy p = policyOrNew(userId, now);

            if ("Y".equalsIgnoreCase(p.getAccountLock())) {
                if (isLocked(p, now)) {
                    // 아직 잠겨 있다. 카운터도 시각도 건드리지 않는다 —
                    // 실패할 때마다 시각을 밀면 계속 두드리는 한 영원히 풀리지 않는다.
                    return;
                }
                // 잠금이 만료된 뒤의 첫 실패. 처음부터 다시 센다.
                // 이전 카운터를 이어 가면 만료 직후 한 번만 틀려도 즉시 다시 잠긴다.
                log.info("계정 잠금 만료로 해제: userId={}", userId);
                p.setAccountLock("N");
                p.setPwFailCnt(0L);
                m.setBlockTime(null);
            }

            long cnt = (p.getPwFailCnt() == null ? 0L : p.getPwFailCnt()) + 1;
            p.setPwFailCnt(cnt);
            p.setUpdatedtime(now);
            if (cnt >= failLimit()) {
                log.warn("계정 잠금: userId={} failCount={} lockMinutes={}", userId, cnt, lockMinutes());
                p.setAccountLock("Y");
                m.setBlockTime(now);
                m.setUpdatedtime(now);
                managers.save(m);
            }
            policies.save(p);
        });
    }

    @Transactional
    public void onLogout(String rawUserId) {
        String userId = normalize(rawUserId);
        managers.findByUserIdForUpdate(userId).ifPresent(m -> {
            m.setLogin("OFF-LINE");
            m.setUpdatedtime(LocalDateTime.now());
            managers.save(m);
        });
    }

    @Transactional
    public void unlock(String rawUserId) {
        String userId = normalize(rawUserId);
        // 존재하지 않는 계정에 대해 고아 정책 행을 만들지 않도록 계정 확인 후 진행한다.
        managers.findByUserIdForUpdate(userId).ifPresent(m -> {
            LocalDateTime now = LocalDateTime.now();
            CcfaManagerPwPolicy p = policyOrNew(userId, now);
            log.info("계정 잠금 수동 해제: userId={}", userId);
            p.setAccountLock("N");
            p.setPwFailCnt(0L);
            p.setUpdatedtime(now);
            policies.save(p);
            m.setBlockTime(null);
            m.setUpdatedtime(now);
            managers.save(m);
        });
    }

    private CcfaManagerPwPolicy policyOrNew(String userId, LocalDateTime now) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId).orElseGet(() -> {
            CcfaManagerPwPolicy p = new CcfaManagerPwPolicy();
            p.setUserId(userId);
            p.setAccountLock("N");
            p.setPwFailCnt(0L);
            p.setCreatedtime(now);
            p.setUpdatedtime(now);
            return p;
        });
    }
}
