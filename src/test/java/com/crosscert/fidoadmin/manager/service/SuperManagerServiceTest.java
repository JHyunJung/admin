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
import com.crosscert.fidoadmin.common.TenantMismatchException;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SuperManagerServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    Query lockQuery = mock(Query.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    ManagerUserIdGuard userIdGuard = new ManagerUserIdGuard(managers, em);
    SuperManagerService service = new SuperManagerService(managers, audit, policies, loginAttempts, em, tenant, userIdGuard);

    private void loginSuperSelecting(long companyIdx) {
        var u = new ManagerUserDetails(9L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery);
        selected.select(companyIdx);
    }

    private void loginSuperAs(long idx) {
        var u = new ManagerUserDetails(idx, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery);
    }

    private void loginCompany(long companyIdx) {
        var u = new ManagerUserDetails(3L, "bizuser", null, "고객사담당", companyIdx, "고객사", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    /** companyIdx 는 이 화면이 소유한 행의 COMPANY_IDX 다(테넌트 운영자면 5L, 슈퍼관리자면 0L). */
    private CcfaManager manager(Long idx, Long companyIdx) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId("u" + idx); m.setUserPw("hash"); m.setCompanyIdx(companyIdx);
        return m;
    }

    private CcfaManager manager(Long idx, Long companyIdx, String userId) {
        CcfaManager m = manager(idx, companyIdx);
        m.setUserId(userId);
        return m;
    }

    /** 이 화면은 유효 테넌트가 아니라 상수 0 으로 건다. */
    @Test void 슈퍼관리자_계정만_조회된다() {
        loginSuperSelecting(9L);
        ArgumentCaptor<Specification<CcfaManager>> captor = ArgumentCaptor.forClass(Specification.class);
        service.search(new ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx")));
        verify(managers).findAll(captor.capture(), any(PageRequest.class));
        assertThat(companyIdxEqualsIn(captor.getValue())).isEqualTo(0L);
    }

    @Test void 등록하면_COMPANY_IDX_가_0_이_된다() {
        loginSuperSelecting(9L);
        when(managers.findByUserId(any())).thenReturn(Optional.empty());
        when(managers.save(any(CcfaManager.class))).thenAnswer(i -> i.getArgument(0));
        CcfaManager created = service.create(manager(null, 5L));
        assertThat(created.getCompanyIdx()).isEqualTo(0L);
    }

    /** 테넌트 운영자 행은 이 화면에서 열 수 없다. */
    @Test void 일반_운영자_행은_열_수_없다() {
        loginSuperSelecting(9L);
        when(managers.findById(1L)).thenReturn(Optional.of(manager(1L, 5L)));
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(TenantMismatchException.class);
    }

    @Test void 자기_자신은_삭제할_수_없다() {
        loginSuperAs(1L);
        when(managers.findById(1L)).thenReturn(Optional.of(manager(1L, 0L)));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("자기 자신");
        verify(managers, never()).delete(any(CcfaManager.class));
    }

    /** COMPANY 역할이 서비스로 직접 부르면(URL 설정이 없다고 가정해도) 거부되어야 한다. */
    @Test void COMPANY_역할은_서비스_계층에서_거부된다() {
        loginCompany(5L);
        assertThatThrownBy(() -> service.search(new ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
    }

    /** 미선택 SUPER 도 이 화면을 열 수 있다 — companyIdxAttribute() 가 null 이라 유효 테넌트를 요구하지 않는다. */
    @Test void 미선택_SUPER_도_조회할_수_있다() {
        loginSuperAs(1L); // selected 테넌트 없음
        when(managers.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        service.search(new ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx")));
        // 예외 없이 통과하면 유효 테넌트를 요구하지 않았다는 뜻이다.
    }

    /**
     * USER_ID 에 유니크 제약이 없어(ERD) 코드에서 중복을 막는다. 이 화면으로 이미 존재하는
     * USER_ID(슈퍼관리자 계정의 것)를 다시 등록하려 하면 거부된다.
     */
    @Test void duplicateUserIdIsRejectedBeforeSave() {
        loginSuperSelecting(9L);
        when(managers.findByUserId("superadmin")).thenReturn(Optional.of(manager(2L, 0L, "superadmin")));
        assertThatThrownBy(() -> service.create(manager(null, 0L, "superadmin")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("superadmin");
        verify(managers, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    /**
     * 교차 경로: SuperManagerService 로 이미 존재하는 USER_ID(테넌트 운영자의 것)를 만들려 하면
     * 거부된다. USER_ID 중복 검사는 findByUserId() 로 테넌트와 무관하게 전역이기 때문이다.
     */
    @Test void 테넌트_운영자의_USER_ID_로는_슈퍼관리자_계정을_만들_수_없다() {
        loginSuperSelecting(9L);
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(3L, 5L, "kbadmin")));
        assertThatThrownBy(() -> service.create(manager(null, 0L, "kbadmin")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("kbadmin");
        verify(managers, never()).save(any());
    }

    /**
     * 잠금이 존재 검사보다 먼저 일어나는지를 InOrder 로 본다.
     * ManagerServiceTest.createLocksTableBeforeDuplicateCheck 와 같은 성질을 이 화면 쪽에도 고정한다.
     */
    @Test void createLocksTableBeforeDuplicateCheck() {
        loginSuperSelecting(9L);
        when(managers.findByUserId("newsuper")).thenReturn(Optional.empty());
        when(managers.save(any())).thenAnswer(inv -> { CcfaManager m = inv.getArgument(0); m.setIdx(11L); return m; });

        service.create(manager(null, 0L, "newsuper"));

        InOrder order = Mockito.inOrder(em, lockQuery, managers);
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE");
        order.verify(lockQuery).executeUpdate();
        order.verify(managers).findByUserId("newsuper");
    }

    @Test void defaultsFilledOnCreate() {
        loginSuperSelecting(9L);
        when(managers.findByUserId("newsuper")).thenReturn(Optional.empty());
        when(managers.save(any())).thenAnswer(inv -> { CcfaManager m = inv.getArgument(0); m.setIdx(10L); return m; });

        CcfaManager saved = service.create(manager(null, 0L, "newsuper"));

        assertThat(saved.getStatus()).isEqualTo("활성");
        assertThat(saved.getLogin()).isEqualTo("OFF-LINE");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_MANAGER CREATE 10");
    }

    @Test void unlockDelegatesAndAuditsStatus() {
        loginSuperSelecting(9L);
        CcfaManager m = manager(2L, 0L, "superadmin");
        when(managers.findById(2L)).thenReturn(Optional.of(m));

        service.unlock(2L);

        verify(loginAttempts).unlock("superadmin");
        verify(audit).log(AuditType.STATUS, "CCFA_MANAGER UNLOCK superadmin");
    }

    /** 승인대기 행의 상태는 이 화면에서도 바꿀 수 없다. ManagerService.update() 와 같은 규칙이다. */
    @Test void updateCannotActivatePendingRow() {
        loginSuperSelecting(9L);
        CcfaManager m = manager(2L, 0L, "applicant");
        m.setStatus(SignupPolicy.STATUS_PENDING);
        when(managers.findById(2L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(2L, e -> { e.setUserNm("이름만 바꿈"); e.setStatus("활성"); }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("가입 승인");

        assertThat(m.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
        verify(managers, never()).save(any());
    }

    /**
     * toSpecification() 이 실제로 companyIdx = 0 조건을 거는지, JPA Criteria API 를 최소한만
     * 흉내 낸 스텁으로 확인한다(cb.equal() 호출에 쓰인 값을 그대로 기록한다).
     */
    @SuppressWarnings("unchecked")
    private Long companyIdxEqualsIn(Specification<CcfaManager> spec) {
        jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.Root<CcfaManager> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.Path<Object> path = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("companyIdx")).thenReturn(path);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        when(cb.equal(any(), valueCaptor.capture())).thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        when(cb.and(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate[]>any()))
            .thenReturn(mock(jakarta.persistence.criteria.Predicate.class));
        spec.toPredicate(root, query, cb);
        return (Long) valueCaptor.getAllValues().stream()
            .filter(v -> v instanceof Long).findFirst().orElseThrow();
    }
}
