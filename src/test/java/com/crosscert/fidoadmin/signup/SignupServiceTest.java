package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
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
import org.springframework.security.core.context.SecurityContextHolder;

class SignupServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    EntityManager em = mock(EntityManager.class);
    Query lockQuery = mock(Query.class);
    AuditLogger audit = mock(AuditLogger.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    SignupService service = new SignupService(managers, companies, em, audit);

    @BeforeEach void stubLock() {
        // 가입은 인증 없이 일어난다. 테스트도 로그인 사용자가 없는 상태로 돌린다.
        SecurityContextHolder.clearContext();
        when(em.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.executeUpdate()).thenReturn(0);
        when(managers.save(any(CcfaManager.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    /** 신청 결과는 항상 승인대기 + 미배정 소속이다. */
    @Test void applyForcesPendingStatusAndUnassignedCompany() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());

        CcfaManager saved = service.apply("newbie", "hash", "홍길동",
            "hong@kb.local", "010-0000-0000", "업무 담당자입니다");

        assertThat(saved.getStatus()).isEqualTo("승인대기");
        assertThat(saved.getCompanyIdx()).isEqualTo(-1L);
        assertThat(saved.getUserId()).isEqualTo("newbie");
        assertThat(saved.getUserPw()).isEqualTo("hash");
        assertThat(saved.getUserNm()).isEqualTo("홍길동");
        assertThat(saved.getUserEmail()).isEqualTo("hong@kb.local");
        assertThat(saved.getUserPhone()).isEqualTo("010-0000-0000");
        assertThat(saved.getEtc()).contains("업무 담당자입니다");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
    }

    /**
     * 상태·소속을 넘길 수 있는 파라미터가 존재하면 안 된다. 서비스가 강제한다는 보장은
     * "덮어쓴다"가 아니라 "받을 통로가 없다"여야 한다. 시그니처를 리플렉션으로 고정한다.
     */
    @Test void applyHasNoParameterForStatusOrCompanyIdx() {
        var methods = java.util.Arrays.stream(SignupService.class.getMethods())
            .filter(m -> m.getName().equals("apply")).toList();
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getParameterTypes())
            .containsOnly(String.class);   // Long(소속)·상태 열거형이 끼어들 여지가 없다
        assertThat(methods.get(0).getParameterCount()).isEqualTo(6);
    }

    /**
     * SignupService 는 CrudService 를 상속하면 안 된다.
     * CrudService.create() 는 TenantContext.require() 로 이어져 로그인 사용자를 요구하는데,
     * 가입은 인증 없이 호출된다.
     */
    @Test void signupServiceDoesNotExtendCrudService() {
        assertThat(CrudService.class.isAssignableFrom(SignupService.class)).isFalse();
    }

    /** IDX 는 시퀀스가 채번한다. 서비스가 직접 넣으면 안 된다. */
    @Test void applyDoesNotAssignIdx() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());
        CcfaManager saved = service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, null);
        assertThat(saved.getIdx()).isNull();
    }

    /** 신청 사유가 비어 있으면 ETC 에 빈 접두사만 남기지 않는다. */
    @Test void applyLeavesEtcNullWhenReasonIsBlank() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());
        CcfaManager saved = service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, "   ");
        assertThat(saved.getEtc()).isNull();
    }

    @Test void applyRejectsDuplicateUserId() {
        CcfaManager existing = new CcfaManager();
        existing.setUserId("kbadmin");
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.apply("kbadmin", "hash", "홍길동", "hong@kb.local", null, null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("kbadmin");
        verify(managers, never()).save(any(CcfaManager.class));
    }

    /**
     * USER_ID 에 DB 유니크 제약이 없어(ERD, 스키마 변경 불가) 동시 신청이 둘 다
     * findByUserId() 에서 빈 결과를 볼 수 있다. 존재 검사 전에 테이블을 배타 잠금해
     * 직렬화해야 한다(ManagerService.insert() 와 같은 방식).
     */
    @Test void applyLocksTableBeforeDuplicateCheck() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());

        service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, null);

        InOrder order = Mockito.inOrder(em, lockQuery, managers);
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE");
        order.verify(lockQuery).executeUpdate();
        order.verify(managers).findByUserId("newbie");
        order.verify(managers).save(any(CcfaManager.class));
    }

    @Test void existsUserIdReflectsRepository() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(new CcfaManager()));
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());
        assertThat(service.existsUserId("kbadmin")).isTrue();
        assertThat(service.existsUserId("newbie")).isFalse();
    }
}
