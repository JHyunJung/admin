package com.crosscert.fidoadmin.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ManagerServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    Query lockQuery = mock(Query.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    ManagerUserIdGuard userIdGuard = new ManagerUserIdGuard(managers, em);
    ManagerService service = new ManagerService(managers, audit, policies, loginAttempts, em, tenant, userIdGuard);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery);
        selected.select(1L); // manager() 가 만드는 행의 소유 COMPANY_IDX 와 맞춘다
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaManager manager(Long idx, String userId) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId(userId); m.setUserPw("hash"); m.setCompanyIdx(1L);
        return m;
    }

    /** USER_ID 에 유니크 제약이 없어(ERD) 코드에서 중복을 막는다. 로그인이 USER_ID 로 조회하기 때문. */
    @Test void duplicateUserIdIsRejectedBeforeSave() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(2L, "kbadmin")));
        assertThatThrownBy(() -> service.create(manager(null, "kbadmin")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("kbadmin");
        verify(managers, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    /**
     * USER_ID 에 DB 유니크 제약이 없어(ERD, 스키마 변경 불가) 동시 등록 요청이 둘 다
     * findByUserId() 에서 빈 결과를 볼 수 있다. 존재 검사 전에 테이블을 배타 잠금해
     * 이후 생성 요청을 직렬화해야 한다.
     */
    @Test void createLocksTableBeforeDuplicateCheck() {
        when(managers.findByUserId("newop")).thenReturn(Optional.empty());
        when(managers.save(any())).thenAnswer(inv -> { CcfaManager m = inv.getArgument(0); m.setIdx(11L); return m; });

        service.create(manager(null, "newop"));

        InOrder order = Mockito.inOrder(em, lockQuery, managers);
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE");
        order.verify(lockQuery).executeUpdate();
        order.verify(managers).findByUserId("newop");
    }

    @Test void defaultsFilledOnCreate() {
        when(managers.findByUserId("newop")).thenReturn(Optional.empty());
        when(managers.save(any())).thenAnswer(inv -> { CcfaManager m = inv.getArgument(0); m.setIdx(10L); return m; });

        CcfaManager saved = service.create(manager(null, "newop"));

        assertThat(saved.getStatus()).isEqualTo("활성");
        assertThat(saved.getLogin()).isEqualTo("OFF-LINE");
        assertThat(saved.getAlramType()).isEqualTo("none");
        assertThat(saved.getAlramLevel()).isEqualTo("0");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_MANAGER CREATE 10");
    }

    @Test void unlockDelegatesAndAuditsStatus() {
        CcfaManager m = manager(2L, "kbadmin");
        when(managers.findById(2L)).thenReturn(Optional.of(m));

        service.unlock(2L);

        verify(loginAttempts).unlock("kbadmin");
        verify(audit).log(AuditType.STATUS, "CCFA_MANAGER UNLOCK kbadmin");
    }

    /**
     * get() 은 잠금 없이 읽는다. 동시 로그인이 LOGIN/LAST_ACCESS/BLOCK_TIME/UPDATEDTIME 을 바꾼 뒤
     * save() 가 그 값을 덮어쓰지 않도록, findById() 이후·loginAttempts.unlock() 이전에
     * PESSIMISTIC_WRITE 로 행을 재조회(refresh)해 잠가야 한다.
     */
    @Test void unlockRefreshesUnderRowLockBeforeDelegating() {
        CcfaManager m = manager(2L, "kbadmin");
        when(managers.findById(2L)).thenReturn(Optional.of(m));

        service.unlock(2L);

        InOrder order = Mockito.inOrder(managers, em, loginAttempts);
        order.verify(managers).findById(2L);
        order.verify(em).refresh(m, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        order.verify(loginAttempts).unlock("kbadmin");
    }

    @Test void selfDeleteIsBlocked() {
        when(managers.findById(1L)).thenReturn(Optional.of(manager(1L, "superuser")));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("자기 자신");
        verify(managers, never()).delete(any(CcfaManager.class));
    }

    /**
     * 승인대기 행을 운영자 수정 화면에서 열면 상태 선택지에 그 값이 없어 브라우저가 `활성` 을 고른다.
     * 이름만 고쳐 저장해도 상태가 `활성` 으로 바뀌어 승인 절차(감사 로그·상태 재확인·행 잠금)를
     * 통째로 건너뛰게 된다. 템플릿을 고치는 것만으로는 조작된 POST 를 막지 못하므로
     * 서비스가 거부해야 한다(설계서 4장).
     */
    @Test void updateCannotActivatePendingRow() {
        CcfaManager m = manager(2L, "applicant");
        m.setStatus(SignupPolicy.STATUS_PENDING);
        when(managers.findById(2L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(2L, e -> { e.setUserNm("이름만 바꿈"); e.setStatus("활성"); }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("가입 승인");

        assertThat(m.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
        verify(managers, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    /** 거절 행도 같다. 되살리려면 가입 승인 화면을 거쳐야 한다. */
    @Test void updateCannotChangeStatusOfRejectedRow() {
        CcfaManager m = manager(3L, "rejected");
        m.setStatus(SignupPolicy.STATUS_REJECTED);
        when(managers.findById(3L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(3L, e -> e.setStatus("비활성")))
            .isInstanceOf(IllegalStateException.class);

        assertThat(m.getStatus()).isEqualTo(SignupPolicy.STATUS_REJECTED);
        verify(managers, never()).save(any());
    }

    /**
     * 막는 것은 상태 변경뿐이다. 승인대기 행의 다른 필드(이름·연락처 등) 수정은 그대로 통과하고,
     * 상태는 원래 값으로 저장된다. 화면이 상태를 읽기 전용으로 되돌려 보내므로 정상 경로다.
     */
    @Test void updateKeepsOtherFieldsEditableOnPendingRow() {
        CcfaManager m = manager(2L, "applicant");
        m.setStatus(SignupPolicy.STATUS_PENDING);
        when(managers.findById(2L)).thenReturn(Optional.of(m));
        when(managers.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(2L, e -> { e.setUserNm("바뀐 이름"); e.setStatus(SignupPolicy.STATUS_PENDING); });

        assertThat(m.getUserNm()).isEqualTo("바뀐 이름");
        assertThat(m.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
        verify(audit).log(AuditType.UPDATE, "CCFA_MANAGER UPDATE 2");
    }

    /** 정상 상태(활성/비활성) 행의 상태 변경은 기존 화면 그대로 동작해야 한다. */
    @Test void updateStillTogglesStatusOnNormalRow() {
        CcfaManager m = manager(2L, "kbadmin");
        m.setStatus("활성");
        when(managers.findById(2L)).thenReturn(Optional.of(m));
        when(managers.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(2L, e -> e.setStatus("비활성"));

        assertThat(m.getStatus()).isEqualTo("비활성");
        verify(audit).log(AuditType.UPDATE, "CCFA_MANAGER UPDATE 2");
    }

    @Test void lockStateReadsLatestPolicyRow() {
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setUserId("kbadmin"); p.setAccountLock("Y"); p.setPwFailCnt(5L);
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        assertThat(service.lockState("kbadmin")).get().extracting(CcfaManagerPwPolicy::getAccountLock).isEqualTo("Y");
        assertThat(service.lockState("nobody")).isEmpty();
    }
}
