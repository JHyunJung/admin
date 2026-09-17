package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import com.crosscert.fidoadmin.signup.SignupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 가입 신청이 실제 Oracle 에 저장된 뒤, 그 계정으로 인증을 시도하면 무슨 일이 벌어지는지 확인한다.
 *
 * <p>단위 테스트는 저장된 값만 본다. 그러나 실제 위협은 "저장된 행이 로그인 시 어떤 권한을 갖는가"다.
 * 신청 → 저장 → 인증까지 실제 DB 와 실제 인증 코드로 이어 붙여, 미승인 계정이 로그인할 수 없고
 * 특히 SUPER 가 되지 않는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SignupService.class, ManagerUserDetailsService.class})
class SignupEscalationIntegrationTest extends OracleContainerSupport {

    @Autowired SignupService signups;
    @Autowired ManagerUserDetailsService uds;
    @Autowired CcfaManagerRepository managers;
    // JPA 슬라이스에는 감사 로거 빈이 없다. 신청은 익명 호출이라 어차피 기록되지 않는다.
    @MockitoBean AuditLogger audit;

    /** 신청 직후의 계정은 로그인할 수 없고, 무엇보다 SUPER 가 아니다. */
    @Test void appliedAccountCannotLogInAndIsNotSuper() {
        String userId = "esc_" + System.nanoTime();
        signups.apply(userId, "hash", "신청자", userId + "@kb.local", null, "권한 상승 시험");

        ManagerUserDetails u = (ManagerUserDetails) uds.loadUserByUsername(userId);

        assertThat(u.isEnabled()).as("승인 전에는 로그인 불가").isFalse();
        assertThat(u.isSuper()).as("가입 경로로 SUPER 가 되어서는 안 된다").isFalse();
        assertThat(u.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        assertThat(u.getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    /** DB 에 실제로 들어간 값이 승인대기·미배정인지 직접 확인한다. */
    @Test void storedRowHasPendingStatusAndUnassignedCompany() {
        String userId = "row_" + System.nanoTime();
        signups.apply(userId, "hash", "신청자", userId + "@kb.local", "010-0000-0000", "사유");

        CcfaManager saved = managers.findByUserId(userId).orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(saved.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        assertThat(saved.getIdx()).as("시퀀스가 채번한다").isNotNull().isPositive();
        assertThat(saved.getEtc()).contains("사유");
    }

    /**
     * 실제 Oracle 에서 LOCK TABLE 이 문법 오류·타임아웃 없이 수행되고,
     * 중복 아이디가 거부되는지 확인한다(H2 로는 검증되지 않는 부분).
     */
    @Test void duplicateUserIdIsRejectedOnRealDatabase() {
        String userId = "dup_" + System.nanoTime();
        signups.apply(userId, "hash", "먼저", userId + "@kb.local", null, null);

        assertThatThrownBy(() -> signups.apply(userId, "hash", "나중", userId + "2@kb.local", null, null))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(managers.findByUserId(userId)).isPresent();
    }

    /** 기존 운영자 아이디로는 신청할 수 없다(계정 탈취 시도 차단). */
    @Test void cannotApplyWithExistingManagerUserId() {
        assertThat(managers.findByUserId("superuser")).as("시드에 superuser 가 있어야 한다").isPresent();

        assertThatThrownBy(() -> signups.apply("superuser", "hash", "사칭",
            "attacker@evil.local", null, "탈취 시도"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 기존 계정이 손상되지 않았는지 확인한다
        CcfaManager original = managers.findByUserId("superuser").orElseThrow();
        assertThat(original.getCompanyIdx()).isEqualTo(0L);
        assertThat(original.getUserEmail()).isNotEqualTo("attacker@evil.local");
    }
}
