package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.LoginAttemptService
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.signup.SignupPolicy
import com.crosscert.fidoadmin.signup.SignupService
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 가입 신청이 실제 Oracle 에 저장된 뒤, 그 계정으로 인증을 시도하면 무슨 일이 벌어지는지 확인한다.
 *
 * 단위 테스트는 저장된 값만 본다. 그러나 실제 위협은 "저장된 행이 로그인 시 어떤 권한을 갖는가"다.
 * 신청 → 저장 → 인증까지 실제 DB 와 실제 인증 코드로 이어 붙여, 미승인 계정이 로그인할 수 없고
 * 특히 SUPER 가 되지 않는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SignupService::class, com.crosscert.fidoadmin.manager.service.ManagerUserIdGuard::class, ManagerUserDetailsService::class, LoginAttemptService::class,
    com.crosscert.fidoadmin.auth.PasswordAgeStore::class, com.crosscert.fidoadmin.auth.PasswordExpiryPolicy::class)
class SignupEscalationIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var signups: SignupService
    @Autowired lateinit var uds: ManagerUserDetailsService
    @Autowired lateinit var managers: CcfaManagerRepository
    @Autowired lateinit var em: EntityManager
    // JPA 슬라이스에는 감사 로거 빈이 없다. 신청은 익명 호출이라 어차피 기록되지 않는다.
    @MockitoBean lateinit var audit: AuditLogger

    /**
     * 보류 중인 변경을 Oracle 로 밀어내고 1차 캐시를 비운 뒤 USER_ID 로 다시 읽는다.
     * 이렇게 하지 않으면 서비스가 방금 만든 그 인스턴스가 캐시에서 그대로 돌아와,
     * DB 가 값을 거부해도 테스트가 통과한다(메모리를 검사한 것이지 DB 가 아니다).
     */
    private fun reloaded(userId: String): CcfaManager {
        em.flush()
        em.clear()
        return managers.findByUserId(userId).orElseThrow()
    }

    /** 신청 직후의 계정은 로그인할 수 없고, 무엇보다 SUPER 가 아니다. */
    @Test
    fun appliedAccountCannotLogInAndIsNotSuper() {
        val userId = "esc_" + System.nanoTime()
        signups.apply(userId, "hash", "신청자", "$userId@kb.local", null, "권한 상승 시험")

        val u = uds.loadUserByUsername(userId) as ManagerUserDetails

        assertThat(u.isEnabled()).`as`("승인 전에는 로그인 불가").isFalse()
        assertThat(u.isSuper).`as`("가입 경로로 SUPER 가 되어서는 안 된다").isFalse()
        assertThat(u.companyIdx).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX)
        assertThat(u.authorities).extracting("authority").containsExactly("ROLE_COMPANY")
    }

    /** DB 에 실제로 들어간 값이 승인대기·미배정인지 직접 확인한다. */
    @Test
    fun storedRowHasPendingStatusAndUnassignedCompany() {
        val userId = "row_" + System.nanoTime()
        signups.apply(userId, "hash", "신청자", "$userId@kb.local", "010-0000-0000", "사유")

        val saved = reloaded(userId)

        assertThat(saved.status).isEqualTo(SignupPolicy.STATUS_PENDING)
        assertThat(saved.companyIdx).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX)
        assertThat(saved.idx).`as`("시퀀스가 채번한다").isNotNull().isPositive()
        assertThat(saved.etc).contains("사유")
    }

    /**
     * 실제 Oracle 에서 LOCK TABLE 이 문법 오류·타임아웃 없이 수행되고,
     * 중복 아이디가 거부되는지 확인한다(H2 로는 검증되지 않는 부분).
     */
    @Test
    fun duplicateUserIdIsRejectedOnRealDatabase() {
        val userId = "dup_" + System.nanoTime()
        signups.apply(userId, "hash", "먼저", "$userId@kb.local", null, null)

        assertThatThrownBy { signups.apply(userId, "hash", "나중", userId + "2@kb.local", null, null) }
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)

        assertThat(managers.findByUserId(userId)).isPresent()
    }

    /** 기존 운영자 아이디로는 신청할 수 없다(계정 탈취 시도 차단). */
    @Test
    fun cannotApplyWithExistingManagerUserId() {
        assertThat(managers.findByUserId("superuser")).`as`("시드에 superuser 가 있어야 한다").isPresent()

        assertThatThrownBy { signups.apply("superuser", "hash", "사칭",
            "attacker@evil.local", null, "탈취 시도") }
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)

        // 기존 계정이 손상되지 않았는지 확인한다
        val original = reloaded("superuser")
        assertThat(original.companyIdx).isEqualTo(0L)
        assertThat(original.userEmail).isNotEqualTo("attacker@evil.local")
    }
}
