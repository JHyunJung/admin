package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 성공/실패에 따른 CCFA_MANAGER, CCFA_MANAGER_PW_POLICY 갱신. */
@Service
public class LoginAttemptService {

    static final String PROP_FAIL_LIMIT = "PW_FAIL_LIMIT";

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final CcfaSystemPropRepository props;
    private final int defaultFailLimit;

    public LoginAttemptService(CcfaManagerRepository managers, CcfaManagerPwPolicyRepository policies,
                               CcfaSystemPropRepository props,
                               @Value("${fido-admin.password-fail-limit:5}") int defaultFailLimit) {
        this.managers = managers;
        this.policies = policies;
        this.props = props;
        this.defaultFailLimit = defaultFailLimit;
    }

    public int failLimit() {
        return props.findById(new CcfaSystemPropId(PROP_FAIL_LIMIT, 0L))
            .map(p -> {
                try { return Integer.parseInt(p.getPropValue().trim()); }
                catch (RuntimeException e) { return defaultFailLimit; }
            })
            .orElse(defaultFailLimit);
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
            m.setUpdatedtime(now);
            managers.save(m);
            CcfaManagerPwPolicy p = policyOrNew(userId, now);
            p.setPwFailCnt(0L);
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
            long cnt = (p.getPwFailCnt() == null ? 0L : p.getPwFailCnt()) + 1;
            p.setPwFailCnt(cnt);
            p.setUpdatedtime(now);
            if (cnt >= failLimit()) {
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
        managers.findByUserId(userId).ifPresent(m -> {
            m.setLogin("OFF-LINE");
            m.setUpdatedtime(LocalDateTime.now());
            managers.save(m);
        });
    }

    @Transactional
    public void unlock(String rawUserId) {
        String userId = normalize(rawUserId);
        managers.findByUserIdForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        CcfaManagerPwPolicy p = policyOrNew(userId, now);
        p.setAccountLock("N");
        p.setPwFailCnt(0L);
        p.setUpdatedtime(now);
        policies.save(p);
        managers.findByUserId(userId).ifPresent(m -> {
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
