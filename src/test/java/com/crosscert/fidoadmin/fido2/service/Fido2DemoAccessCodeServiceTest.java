package com.crosscert.fidoadmin.fido2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2DemoAccessCodeServiceTest {

    Fido2DemoAccessCodeRepository repo = mock(Fido2DemoAccessCodeRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    Fido2DemoAccessCodeService service = new Fido2DemoAccessCodeService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private Fido2DemoAccessCode code(String accesscode) {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode(); c.setAccesscode(accesscode); c.setVendorname("벤더"); return c;
    }

    /** 할당형 PK: 기존 코드로 등록하면 덮어쓰지 않고 거부한다. */
    @Test void duplicateAccessCodeIsRejectedWithoutPersist() {
        when(repo.existsById("DEMO-0001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(code("DEMO-0001")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("DEMO-0001");
        verify(em, never()).persist(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void newAccessCodePersistsWithDefaultStatusAndAudits() {
        when(repo.existsById("DEMO-0003")).thenReturn(false);
        Fido2DemoAccessCode saved = service.create(code("DEMO-0003"));
        assertThat(saved.getStatus()).isEqualTo("E");
        verify(em).persist(saved);
        verify(em).flush();
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "FIDO2_DEMO_ACCESS_CODE CREATE DEMO-0003");
    }

    @Test void explicitStatusIsKept() {
        when(repo.existsById("DEMO-0004")).thenReturn(false);
        Fido2DemoAccessCode c = code("DEMO-0004"); c.setStatus("D");
        assertThat(service.create(c).getStatus()).isEqualTo("D");
    }

    /** idx 가 없는 엔티티라 기본 정렬을 반드시 재정의해야 한다(기본 "idx" 면 조회 시 500). */
    @Test void sortsByAccessCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "accesscode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("accesscode", "vendorname", "status");
    }
}
