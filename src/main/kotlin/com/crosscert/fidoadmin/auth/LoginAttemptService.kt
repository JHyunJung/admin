package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 로그인 성공/실패에 따른 CCFA_MANAGER, CCFA_MANAGER_PW_POLICY 갱신. */
@Service
class LoginAttemptService(
    private val managers: CcfaManagerRepository,
    private val policies: CcfaManagerPwPolicyRepository,
    private val props: CcfaSystemPropRepository,
    @Value("\${fido-admin.password-fail-limit:5}") private val defaultFailLimit: Int,
    @Value("\${fido-admin.password-lock-minutes:30}") private val defaultLockMinutes: Int,
) {

    fun failLimit(): Int = intProp(PROP_FAIL_LIMIT, defaultFailLimit)

    /**
     * 잠금이 저절로 풀리기까지의 시간(분). 0 이하면 자동 해제하지 않는다.
     *
     * 잠금은 30분이 지나면 풀린다.
     * 여기에는 스케줄러가 없으므로 로그인 시도 시점에 "잠근 지 N분이 지났는가"로 판정한다.
     * 결과는 같고, 아무도 시도하지 않는 계정을 위해 배치를 돌릴 필요가 없다.
     */
    fun lockMinutes(): Int = intProp(PROP_LOCK_MINUTES, defaultLockMinutes)

    private fun intProp(key: String, fallback: Int): Int =
        props.findById(CcfaSystemPropId(key, 0L))
            .map { p ->
                val raw = p.propValue ?: return@map fallback
                try { Integer.parseInt(raw.javaTrim()) }
                catch (e: NumberFormatException) { fallback }
            }
            .orElse(fallback)

    /**
     * 지금 잠겨 있는가. ACCOUNT_LOCK='Y' 여도 잠근 지 [lockMinutes] 가 지났으면 풀린 것으로 본다.
     *
     * 잠근 시각은 UPDATEDTIME 으로 본다 — 잠글 때 그 값을 함께 갱신하고, 잠긴 동안에는
     * 실패해도 갱신하지 않으므로([onFailure]) 잠금 시각이 뒤로 밀리지 않는다.
     * 시각이 없으면 언제 잠겼는지 모르므로 잠긴 것으로 둔다(풀어 주는 쪽이 위험하다).
     */
    fun isLocked(policy: CcfaManagerPwPolicy?, now: LocalDateTime): Boolean {
        if (policy == null || !LOCK_YES.equals(policy.accountLock, ignoreCase = true)) return false
        val minutes = lockMinutes()
        if (minutes <= 0) return true
        val lockedAt = policy.updatedtime ?: return true
        return lockedAt.plusMinutes(minutes.toLong()).isAfter(now)
    }

    /**
     * 로그인 시도 동안 계정 행을 잠근다. 호출자 트랜잭션이 끝날 때까지 같은 계정의 다른 시도·집계는 기다린다
     * ([SerializedLoginAuthenticationProvider]). 계정이 없으면 잠글 행이 없다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockForAttempt(rawUserId: String?) {
        managers.findByUserIdForUpdate(normalize(rawUserId))
    }

    /** 이 계정이 지금 잠겨 있는가. 최신 정책 행으로 [isLocked] 를 본다. */
    fun isLockedNow(rawUserId: String?, now: LocalDateTime): Boolean =
        policies.findFirstByUserIdOrderByIdxDesc(normalize(rawUserId))
            .map { p -> isLocked(p, now) }.orElse(false)

    @Transactional
    fun onSuccess(rawUserId: String?) {
        val userId = normalize(rawUserId)
        managers.findByUserIdForUpdate(userId).ifPresent { m ->
            val now = LocalDateTime.now()
            m.login = ManagerStatus.LOGIN_ONLINE
            m.lastAccess = now
            // 만료된 잠금으로 들어온 로그인이면 여기서 실제로 푼다.
            m.blockTime = null
            m.updatedtime = now
            managers.save(m)
            val p = policyOrNew(userId, now)
            p.pwFailCnt = 0L
            p.accountLock = LOCK_NO
            p.updatedtime = now
            policies.save(p)
        }
    }

    @Transactional
    fun onFailure(rawUserId: String?) {
        val userId = normalize(rawUserId)
        managers.findByUserIdForUpdate(userId).ifPresent { m ->
            val now = LocalDateTime.now()
            val p = policyOrNew(userId, now)

            if (LOCK_YES.equals(p.accountLock, ignoreCase = true)) {
                if (isLocked(p, now)) {
                    // 아직 잠겨 있다. 카운터도 시각도 건드리지 않는다 —
                    // 실패할 때마다 시각을 밀면 계속 두드리는 한 영원히 풀리지 않는다.
                    return@ifPresent
                }
                // 잠금이 만료된 뒤의 첫 실패. 처음부터 다시 센다.
                // 이전 카운터를 이어 가면 만료 직후 한 번만 틀려도 즉시 다시 잠긴다.
                log.info("계정 잠금 만료로 해제: userId={}", userId)
                p.accountLock = LOCK_NO
                p.pwFailCnt = 0L
                m.blockTime = null
            }

            val cnt = (p.pwFailCnt ?: 0L) + 1
            p.pwFailCnt = cnt
            p.updatedtime = now
            if (cnt >= failLimit()) {
                log.warn("계정 잠금: userId={} failCount={} lockMinutes={}", userId, cnt, lockMinutes())
                p.accountLock = LOCK_YES
                m.blockTime = now
                m.updatedtime = now
                managers.save(m)
            }
            policies.save(p)
        }
    }

    @Transactional
    fun onLogout(rawUserId: String?) {
        val userId = normalize(rawUserId)
        managers.findByUserIdForUpdate(userId).ifPresent { m ->
            m.login = ManagerStatus.LOGIN_OFFLINE
            m.updatedtime = LocalDateTime.now()
            managers.save(m)
        }
    }

    @Transactional
    fun unlock(rawUserId: String?) {
        val userId = normalize(rawUserId)
        // 존재하지 않는 계정에 대해 고아 정책 행을 만들지 않도록 계정 확인 후 진행한다.
        managers.findByUserIdForUpdate(userId).ifPresent { m ->
            val now = LocalDateTime.now()
            val p = policyOrNew(userId, now)
            log.info("계정 잠금 수동 해제: userId={}", userId)
            p.accountLock = LOCK_NO
            p.pwFailCnt = 0L
            p.updatedtime = now
            policies.save(p)
            m.blockTime = null
            m.updatedtime = now
            managers.save(m)
        }
    }

    private fun policyOrNew(userId: String?, now: LocalDateTime): CcfaManagerPwPolicy =
        policies.findFirstByUserIdOrderByIdxDesc(userId).orElseGet {
            val p = CcfaManagerPwPolicy()
            p.userId = userId
            p.accountLock = LOCK_NO
            p.pwFailCnt = 0L
            p.createdtime = now
            p.updatedtime = now
            p
        }

    companion object {
        private val log = LoggerFactory.getLogger(LoginAttemptService::class.java)

        internal const val PROP_FAIL_LIMIT = "PW_FAIL_LIMIT"
        internal const val PROP_LOCK_MINUTES = "PW_LOCK_MINUTES"

        /** CCFA_MANAGER_PW_POLICY.ACCOUNT_LOCK 값. */
        private const val LOCK_YES = "Y"
        private const val LOCK_NO = "N"

        /**
         * Spring Security 의 UsernamePasswordAuthenticationFilter 가 username 을 trim 하므로
         * 집계 쪽도 같은 정규화를 해야 한다. 그러지 않으면 " kbadmin" 처럼 공백을 붙여
         * 실제 계정의 비밀번호를 시도하면서 잠금 카운터만 피해 갈 수 있다.
         * (Java `String.trim()` 의미를 유지한다 — 필터의 trim 과 같아야 하므로 Kotlin trim() 을 쓰지 않는다.)
         */
        @JvmStatic
        internal fun normalize(userId: String?): String? = userId?.javaTrim()
    }
}
