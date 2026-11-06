package com.crosscert.fidoadmin.manager.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.LoginAttemptService
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.auth.isJavaBlank
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm
import com.crosscert.fidoadmin.signup.SignupPolicy
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.Objects
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import org.springframework.transaction.annotation.Transactional

/**
 * CCFA_MANAGER 계정 화면 둘([ManagerService] 테넌트 운영자, [SuperManagerService] 슈퍼관리자)의
 * 공통 규칙. 두 화면이 다른 점은 **어느 COMPANY_IDX 의 행을 다루는가**(유효 테넌트 vs 상수 0)뿐이고,
 * 기본값·USER_ID 전역 유일성·비밀번호 변경일·가입 상태 보호·자기 삭제 금지·잠금 해제는 같다.
 * 같은 규칙이 두 파일에 있으면 한쪽만 고쳐지므로 여기 한 곳에 둔다.
 *
 * 하위 클래스가 정하는 것: `toSpecification`, `companyIdxAttribute`, (필요하면)
 * `checkTenant`, 그리고 저장 직전 소속을 바로잡는 [normalize].
 */
abstract class ManagerAccountService protected constructor(
    managers: CcfaManagerRepository,
    audit: AuditLogger,
    @JvmField protected val policies: CcfaManagerPwPolicyRepository,
    @JvmField protected val loginAttempts: LoginAttemptService,
    @JvmField protected val em: EntityManager,
    tenant: TenantContext,
    @JvmField protected val userIdGuard: ManagerUserIdGuard,
    @JvmField protected val expiry: PasswordExpiryPolicy,
) : CrudService<CcfaManager, Long, ManagerSearchForm>(managers, audit, tenant) {

    override fun companyIdxOf(e: CcfaManager): Long? = e.companyIdx
    override fun setCompanyIdx(e: CcfaManager, c: Long?) { e.companyIdx = c }
    override fun idOf(e: CcfaManager): String = e.idx.toString()
    override fun tableName(): String = "CCFA_MANAGER"
    override fun sortableProperties(): Set<String> = java.util.Set.of("idx", "userId", "userNm", "lastAccess")

    override fun applyDefaults(e: CcfaManager) {
        if (e.status == null || e.status!!.isJavaBlank()) e.status = ManagerStatus.ACTIVE
        if (e.login == null) e.login = ManagerStatus.LOGIN_OFFLINE
        if (e.alramType == null || e.alramType!!.isJavaBlank()) e.alramType = ManagerStatus.ALARM_TYPE_NONE
        if (e.alramLevel == null || e.alramLevel!!.isJavaBlank()) e.alramLevel = ManagerStatus.ALARM_LEVEL_DEFAULT
    }
    override fun touchCreated(e: CcfaManager, now: LocalDateTime) { e.createdtime = now; e.updatedtime = now }
    override fun touchUpdated(e: CcfaManager, now: LocalDateTime) { e.updatedtime = now }

    /**
     * USER_ID 전역 유일성 검사는 [ManagerUserIdGuard] 하나에 있다. 두 화면과 가입 신청이 같은 테이블에
     * INSERT 하므로, 잠금·검사 로직을 각자 들고 있으면 "USER_ID 는 전역 유일" 보장이 서로 다른 코드로 갈라진다.
     */
    override fun insert(e: CcfaManager): CcfaManager = userIdGuard.insert(e)

    /** 새 운영자의 비밀번호 변경일을 기록한다. JDBC 갱신이 행을 보도록 먼저 flush 한다. */
    @Transactional
    override fun create(entity: CcfaManager): CcfaManager {
        val saved = super.create(entity)
        em.flush()
        expiry.touch(saved.userId)
        return saved
    }

    /**
     * 가입 신청 상태(승인대기·거절)인 행의 STATUS 는 이 화면에서 바꿀 수 없다.
     *
     * 수정 화면의 상태 선택지는 활성·비활성 뿐이라 승인대기를 표현하지 못한다. 그래서 승인대기 행을
     * 열면 브라우저가 활성을 고르고, 이름만 고쳐 저장해도 상태가 활성이 된다. 그러면
     * `SignupService.approve()` 를 거치지 않으므로 승인 감사 로그가 남지 않고
     * 승인대기 여부 재확인과 행 잠금도 건너뛴다.
     *
     * 검사를 **서비스**에 두는 이유: 템플릿은 조작된 POST 를 막지 못하고, 컨트롤러의
     * `validate()` 훅은 폼만 받아 대상 행의 **현재** 상태를 알 수 없다. 현재 상태를 볼 수 있는
     * 지점이자 `update()` 를 부르는 모든 호출자에 한 번에 적용되는 곳이 여기다.
     * 값을 조용히 무시하지 않고 예외를 던지는 것은, 관리자가 바꿨다고 믿은 값이 반영되지 않은 채
     * "수정되었습니다" 만 뜨는 편이 더 나쁘기 때문이다.
     *
     * 막는 것은 상태 변경뿐이다. 같은 행의 이름·연락처 등 다른 필드 수정과,
     * 정상 상태(활성·비활성) 행의 상태 변경은 기존과 똑같이 동작한다.
     * 비밀번호가 바뀌었으면 변경일을 기록한다.
     */
    @Transactional
    override fun update(id: Long, mutator: Consumer<CcfaManager>): CcfaManager {
        val pwBefore = AtomicReference<String?>()
        val saved = super.update(id) { e ->
            pwBefore.set(e.userPw)
            val before = e.status
            mutator.accept(e)
            normalize(e)
            if (SignupPolicy.isSignupStatus(before) && before != e.status) {
                // 던지기 전에 되돌린다. 트랜잭션 롤백에만 기대면, 롤백 경계 밖에서 이 엔티티를
                // 다시 읽는 코드(같은 영속성 컨텍스트, 테스트의 목 객체)가 바뀐 값을 보게 된다.
                e.status = before
                throw IllegalStateException(
                    "가입 신청 상태($before)인 계정의 상태는 가입 승인 화면에서만 변경할 수 있습니다.")
            }
        }
        if (!Objects.equals(pwBefore.get(), saved.userPw)) {
            em.flush()
            expiry.touch(saved.userId)
        }
        return saved
    }

    /**
     * 수정 폼이 실어온 값을 쓴 뒤, 저장 전에 소속을 바로잡는 자리. 기본은 아무것도 하지 않는다
     * (테넌트 운영자 화면은 `CrudService.update()` 가 유효 테넌트로 강제한다).
     */
    protected open fun normalize(e: CcfaManager) {}

    /** 자기 자신은 삭제할 수 없다. 슈퍼관리자가 자기 계정을 지우면 복구 경로가 없다. */
    override fun beforeDelete(e: CcfaManager) {
        if (e.idx != null && e.idx == tenant.require().idx) {
            throw IllegalStateException("자기 자신은 삭제할 수 없습니다.")
        }
    }

    @Transactional(readOnly = true)
    open fun lockState(userId: String?): Optional<CcfaManagerPwPolicy> =
        policies.findFirstByUserIdOrderByIdxDesc(userId)

    /** 잠금 해제: PW_POLICY 초기화 + BLOCK_TIME 제거. 감사 로그 STATUS. */
    @Transactional
    open fun unlock(id: Long) {
        val m = get(id)
        // get() 은 잠금 없이 읽는다. PESSIMISTIC_WRITE 로 재조회(refresh)해 FOR UPDATE 로 행을 다시 읽어야
        // 이후 save() 가 동시 로그인으로 바뀐 값(LOGIN, LAST_ACCESS, BLOCK_TIME, UPDATEDTIME)을 덮어쓰지 않는다.
        em.refresh(m, LockModeType.PESSIMISTIC_WRITE)
        loginAttempts.unlock(m.userId)
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.userId)
    }
}
