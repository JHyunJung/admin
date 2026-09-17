package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService;
import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import com.crosscert.fidoadmin.signup.SignupService;
import jakarta.persistence.EntityManager;
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
 *
 * <p><b>다시 읽을 때는 반드시 {@link #reloaded}/{@link #reloadedById} 를 쓴다.</b>
 * 서비스가 바꾼 것은 영속성 컨텍스트에 붙어 있는 엔티티이고, 곧바로 부르는 조회는
 * 같은 1차 캐시 인스턴스를 그대로 돌려준다. flush 하지 않으면 UPDATE 가 Oracle 까지
 * 가지도 않으므로, DB 가 값을 거부해도 테스트는 통과해 버린다. flush + clear 로
 * 실제 SQL 을 내보내고 캐시를 비운 뒤 읽어야 DB 를 검증하는 테스트가 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SignupService.class, ManagerUserDetailsService.class})
class SignupApprovalEscalationIntegrationTest extends OracleContainerSupport {

    @Autowired SignupService signups;
    @Autowired ManagerUserDetailsService uds;
    @Autowired CcfaManagerRepository managers;
    @Autowired EntityManager em;
    @MockitoBean AuditLogger audit;

    /**
     * 운영자 수정 화면이 쓰는 서비스. 컨테이너에서 꺼내지 않고 직접 만든다 —
     * {@code LoginAttemptService} 는 여기서 검증하는 {@code update()} 경로가 쓰지 않는데,
     * 빈으로 올리려면 관계없는 의존성을 줄줄이 끌어와야 한다.
     */
    private ManagerService managerService;

    @org.junit.jupiter.api.BeforeEach void prepareManagerService() {
        managerService = new ManagerService(managers, audit,
            org.mockito.Mockito.mock(com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository.class),
            org.mockito.Mockito.mock(com.crosscert.fidoadmin.auth.LoginAttemptService.class), em);
        // update() 는 테넌트 검사를 거친다. 이 화면은 SUPER 전용이므로 SUPER 로 로그인한 상태를 만든다.
        var su = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(su, null, su.getAuthorities()));
    }

    @org.junit.jupiter.api.AfterEach void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private CcfaManager applied(String prefix) {
        String userId = prefix + System.nanoTime();
        return signups.apply(userId, "hash", "신청자", userId + "@kb.local", null, "사유");
    }

    /** 보류 중인 변경을 Oracle 로 밀어내고 1차 캐시를 비운다. 이 뒤의 조회는 DB 를 읽는다. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** IDX 로 다시 읽는다. 캐시가 아니라 DB 에 저장된 값이다. */
    private CcfaManager reloadedById(Long idx) {
        flushAndClear();
        return managers.findById(idx).orElseThrow();
    }

    /** USER_ID 로 다시 읽는다. 캐시가 아니라 DB 에 저장된 값이다. */
    private CcfaManager reloaded(String userId) {
        flushAndClear();
        return managers.findByUserId(userId).orElseThrow();
    }

    /** 승인 경로로 SUPER 를 만들 수 없다. 거부될 뿐 아니라 행이 그대로 남아야 한다. */
    @Test void approveCannotCreateSuperAccount() {
        CcfaManager m = applied("sup_");

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 0L))
            .isInstanceOf(IllegalArgumentException.class);

        CcfaManager after = reloadedById(m.getIdx());
        assertThat(after.getStatus()).as("거부됐으니 승인대기 그대로").isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(after.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
    }

    /**
     * 실재하지 않는 고객사로는 승인할 수 없다. CCFA_MANAGER 에 CCFA_COMPANY 로 가는
     * 외래키가 없으니 DB 가 막아 주지 않는다. 막지 않으면 활성 계정이 없는 테넌트에 묶여
     * 로그인은 되면서 테넌트로 걸러지는 조회는 모두 비고, 신청은 대기 목록에서 사라져
     * 운영자가 다시 처리할 수도 없다.
     */
    @Test void approveRejectsCompanyThatDoesNotExist() {
        CcfaManager m = applied("nc_");

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 99999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 고객사");

        CcfaManager after = reloadedById(m.getIdx());
        assertThat(after.getStatus()).as("거부됐으니 승인대기 그대로").isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(after.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        // 대기 목록에 남아 있어야 운영자가 올바른 고객사로 다시 승인할 수 있다
        assertThat(signups.pending()).extracting(CcfaManager::getUserId).contains(m.getUserId());
        // 로그인도 여전히 막혀 있다
        assertThat(((ManagerUserDetails) uds.loadUserByUsername(m.getUserId())).isEnabled()).isFalse();
    }

    /** 정상 승인 후에는 로그인 가능한 일반 고객사 계정이 된다(SUPER 아님). */
    @Test void approvedAccountBecomesCompanyUserNotSuper() {
        CcfaManager m = applied("ok_");

        signups.approve(m.getIdx(), 1L);
        flushAndClear();

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
        // 두 번째 승인이 캐시가 아니라 DB 에 저장된 상태를 보고 거부하는지 확인한다
        flushAndClear();

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 2L))
            .isInstanceOf(IllegalStateException.class);

        assertThat(reloadedById(m.getIdx()).getCompanyIdx())
            .as("소속이 바뀌지 않았다").isEqualTo(1L);
    }

    /** 거절된 계정을 승인으로 되살릴 수 없다. */
    @Test void rejectedAccountCannotBeApproved() {
        CcfaManager m = applied("rej_");
        signups.reject(m.getIdx(), "소속 확인 불가");
        flushAndClear();

        assertThatThrownBy(() -> signups.approve(m.getIdx(), 1L))
            .isInstanceOf(IllegalStateException.class);

        CcfaManager after = reloadedById(m.getIdx());
        assertThat(after.getStatus()).isEqualTo(SignupPolicy.STATUS_REJECTED);
        // 거절된 계정은 로그인도 안 된다
        assertThat(((ManagerUserDetails) uds.loadUserByUsername(m.getUserId())).isEnabled()).isFalse();
    }

    /**
     * 운영자 수정 화면(`/managers`)으로 승인 절차를 우회할 수 없다.
     *
     * <p>그 화면의 상태 선택지는 활성·비활성 뿐이라 승인대기를 표현하지 못한다. 그래서 승인대기 행을 열면
     * 브라우저가 활성을 고르고, 이름만 고쳐 저장해도 상태가 활성이 되어 {@code approve()} 의
     * 감사 로그·상태 재확인·행 잠금을 모두 건너뛰었다(설계서 4장). 실제 Oracle 에 대고 그 저장을
     * 재현해, 이제 거부되고 행이 승인대기 그대로인지 확인한다.
     */
    @Test void managerEditCannotActivatePendingRow() {
        CcfaManager m = applied("byp_");
        flushAndClear();

        assertThatThrownBy(() -> managerService.update(m.getIdx(), e -> {
            e.setUserNm("이름만 바꿈");          // 관리자가 실제로 의도한 수정
            e.setStatus(ManagerStatus.ACTIVE);   // 화면이 몰래 함께 실어 보내던 값
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("가입 승인");

        CcfaManager after = reloadedById(m.getIdx());
        assertThat(after.getStatus()).as("승인대기 그대로").isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(after.getCompanyIdx()).as("소속도 배정되지 않았다").isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        assertThat(((ManagerUserDetails) uds.loadUserByUsername(m.getUserId())).isEnabled())
            .as("로그인도 여전히 막혀 있다").isFalse();
    }

    /** 같은 행의 상태 아닌 필드 수정은 그대로 통과한다. 막는 것은 상태 변경뿐이다. */
    @Test void managerEditStillUpdatesOtherFieldsOfPendingRow() {
        CcfaManager m = applied("keep_");
        flushAndClear();

        managerService.update(m.getIdx(), e -> e.setUserNm("바뀐 이름"));

        CcfaManager after = reloadedById(m.getIdx());
        assertThat(after.getUserNm()).isEqualTo("바뀐 이름");
        assertThat(after.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
    }

    /** 정상 상태(활성) 행의 상태 변경은 기존 화면 그대로 동작해야 한다. */
    @Test void managerEditStillTogglesStatusOfNormalRow() {
        CcfaManager m = applied("norm_");
        signups.approve(m.getIdx(), 1L);
        flushAndClear();

        managerService.update(m.getIdx(), e -> e.setStatus("비활성"));

        assertThat(reloadedById(m.getIdx()).getStatus()).isEqualTo("비활성");
    }

    /** 기존 운영자(superuser, SUPER)를 이 경로로 건드릴 수 없다. */
    @Test void existingSuperAccountCannotBeTouched() {
        CcfaManager superuser = managers.findByUserId("superuser").orElseThrow();
        assertThat(superuser.getStatus()).isEqualTo(ManagerStatus.ACTIVE);

        assertThatThrownBy(() -> signups.approve(superuser.getIdx(), 2L))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signups.reject(superuser.getIdx(), "공격"))
            .isInstanceOf(IllegalStateException.class);

        CcfaManager after = reloaded("superuser");
        assertThat(after.getCompanyIdx()).as("여전히 전역 SUPER").isEqualTo(0L);
        assertThat(after.getStatus()).isEqualTo(ManagerStatus.ACTIVE);
    }

    /**
     * 거절 사유가 길어도 실제 Oracle 이 받아들인다(ETC 2048 바이트, 한글 3바이트).
     *
     * <p>flush 없이 다시 읽으면 서비스가 방금 고친 그 인스턴스가 캐시에서 돌아온다.
     * UPDATE 가 Oracle 로 나가지 않았으니 컬럼이 값을 거부해도 테스트는 통과해 버린다.
     * 그러면 메모리 위의 바이트 길이를 두 번 재는 셈이라 DB 를 검증하지 못한다.
     * 여기서는 flush + clear 로 UPDATE 를 실제로 내보낸 뒤 DB 에서 다시 읽는다.
     */
    @Test void longKoreanRejectReasonFitsInColumn() {
        String userId = "big_" + System.nanoTime();
        // 신청 사유를 한도 가까이 채운 뒤, 거절 사유까지 길게 붙인다
        signups.apply(userId, "hash", "신청자", userId + "@kb.local", null, "가".repeat(560));
        CcfaManager m = reloaded(userId);

        signups.reject(m.getIdx(), "나".repeat(200));

        // ORA-12899 가 나면 이 시점의 flush 에서 터진다 — 그래야 컬럼을 검증한 것이다
        String etc = reloadedById(m.getIdx()).getEtc();
        assertThat(etc.getBytes(StandardCharsets.UTF_8).length)
            .as("컬럼 한도 안에 들어간다").isLessThanOrEqualTo(2048);
        assertThat(etc).as("글자가 중간에 잘려 깨지지 않았다").doesNotContain("�");
        assertThat(etc).as("DB 에 저장된 것은 거절 사유가 붙은 값이다").contains("거절 사유: 나");
    }
}
