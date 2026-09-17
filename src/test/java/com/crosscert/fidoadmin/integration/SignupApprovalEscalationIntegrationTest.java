package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService;
import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import com.crosscert.fidoadmin.signup.SignupService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 승인·거절 경로를 실제 Oracle 에 대고 공격해 본다.
 *
 * <p>단위 테스트는 모의 객체 위에서 돈다. 여기서는 실제로 행을 저장하고, 승인을 시도하고,
 * 그 결과로 만들어진 계정이 실제 인증 코드에서 어떤 권한을 갖는지까지 확인한다.
 * 특히 승인 경로로 SUPER(COMPANY_IDX=0)가 만들어질 수 있는지가 핵심이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SignupService.class, ManagerUserDetailsService.class})
class SignupApprovalEscalationIntegrationTest extends OracleContainerSupport {

    @Autowired SignupService signups;
    @Autowired ManagerUserDetailsService uds;
    @Autowired CcfaManagerRepository managers;
    @MockitoBean AuditLogger audit;

    private CcfaManager applied(String prefix) {
        String userId = prefix + System.nanoTime();
        return signups.apply(userId, "hash", "신청자", userId + "@kb.local", null, "사유");
    }

    /** 승인 경로로 SUPER 를 만들 수 없다. 거부될 뿐 아니라 행이 그대로 남아야 한다. */
    @Test void approveCannotCreateSuperAccount() {
        CcfaManager m = applied("sup_");

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 0L))
            .isInstanceOf(IllegalArgumentException.class);

        CcfaManager after = managers.findById(m.getIdx()).orElseThrow();
        assertThat(after.getStatus()).as("거부됐으니 승인대기 그대로").isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(after.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
    }

    /** 정상 승인 후에는 로그인 가능한 일반 고객사 계정이 된다(SUPER 아님). */
    @Test void approvedAccountBecomesCompanyUserNotSuper() {
        CcfaManager m = applied("ok_");

        signups.approve(m.getIdx(), 1L);

        ManagerUserDetails u = (ManagerUserDetails) uds.loadUserByUsername(m.getUserId());
        assertThat(u.isEnabled()).as("승인 후에는 로그인 가능").isTrue();
        assertThat(u.isSuper()).as("절대 SUPER 가 아니다").isFalse();
        assertThat(u.getCompanyIdx()).isEqualTo(1L);
        assertThat(u.getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    /** 이미 승인된 계정을 다시 승인해 다른 고객사로 옮길 수 없다. */
    @Test void approvedAccountCannotBeReassigned() {
        CcfaManager m = applied("re_");
        signups.approve(m.getIdx(), 1L);

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 2L))
            .isInstanceOf(IllegalStateException.class);

        assertThat(managers.findById(m.getIdx()).orElseThrow().getCompanyIdx())
            .as("소속이 바뀌지 않았다").isEqualTo(1L);
    }

    /** 거절된 계정을 승인으로 되살릴 수 없다. */
    @Test void rejectedAccountCannotBeApproved() {
        CcfaManager m = applied("rej_");
        signups.reject(m.getIdx(), "소속 확인 불가");

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 1L))
            .isInstanceOf(IllegalStateException.class);

        CcfaManager after = managers.findById(m.getIdx()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SignupPolicy.STATUS_REJECTED);
        // 거절된 계정은 로그인도 안 된다
        assertThat(((ManagerUserDetails) uds.loadUserByUsername(m.getUserId())).isEnabled()).isFalse();
    }

    /** 기존 운영자(superuser, SUPER)를 이 경로로 건드릴 수 없다. */
    @Test void existingSuperAccountCannotBeTouched() {
        CcfaManager superuser = managers.findByUserId("superuser").orElseThrow();
        assertThat(superuser.getStatus()).isEqualTo(ManagerStatus.ACTIVE);

        assertThatThrownBy(() -> signups.approve(superuser.getIdx(), 2L))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signups.reject(superuser.getIdx(), "공격"))
            .isInstanceOf(IllegalStateException.class);

        CcfaManager after = managers.findByUserId("superuser").orElseThrow();
        assertThat(after.getCompanyIdx()).as("여전히 전역 SUPER").isEqualTo(0L);
        assertThat(after.getStatus()).isEqualTo(ManagerStatus.ACTIVE);
    }

    /** 거절 사유가 길어도 실제 Oracle 이 받아들인다(ETC 2048 바이트, 한글 3바이트). */
    @Test void longKoreanRejectReasonFitsInColumn() {
        String userId = "big_" + System.nanoTime();
        // 신청 사유를 한도 가까이 채운 뒤, 거절 사유까지 길게 붙인다
        signups.apply(userId, "hash", "신청자", userId + "@kb.local", null, "가".repeat(560));
        CcfaManager m = managers.findByUserId(userId).orElseThrow();

        signups.reject(m.getIdx(), "나".repeat(200));

        String etc = managers.findById(m.getIdx()).orElseThrow().getEtc();
        assertThat(etc.getBytes(StandardCharsets.UTF_8).length)
            .as("컬럼 한도 안에 들어간다").isLessThanOrEqualTo(2048);
        assertThat(etc).as("글자가 중간에 잘려 깨지지 않았다").doesNotContain("�");
    }
}
