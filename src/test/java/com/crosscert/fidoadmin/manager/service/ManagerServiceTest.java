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
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
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
    ManagerService service = new ManagerService(managers, audit, policies, loginAttempts, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery);
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
        when(managers.findById(2L)).thenReturn(Optional.of(manager(2L, "kbadmin")));

        service.unlock(2L);

        verify(loginAttempts).unlock("kbadmin");
        verify(audit).log(AuditType.STATUS, "CCFA_MANAGER UNLOCK kbadmin");
    }

    @Test void selfDeleteIsBlocked() {
        when(managers.findById(1L)).thenReturn(Optional.of(manager(1L, "superuser")));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("자기 자신");
        verify(managers, never()).delete(any(CcfaManager.class));
    }

    @Test void lockStateReadsLatestPolicyRow() {
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setUserId("kbadmin"); p.setAccountLock("Y"); p.setPwFailCnt(5L);
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        assertThat(service.lockState("kbadmin")).get().extracting(CcfaManagerPwPolicy::getAccountLock).isEqualTo("Y");
        assertThat(service.lockState("nobody")).isEmpty();
    }
}
