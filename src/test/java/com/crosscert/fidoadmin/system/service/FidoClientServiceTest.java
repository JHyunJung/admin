package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FidoClientServiceTest {

    CcfaFidoclientRepository repo = mock(CcfaFidoclientRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    FidoClientService service = new FidoClientService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery(anyString())).thenReturn(mock(Query.class));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaFidoclient client(String code) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServername("n"); c.setServerurl("https://x"); return c;
    }

    @Test void createRejectsDuplicateCode() {
        when(repo.existsById("FIDO01")).thenReturn(true);
        assertThatThrownBy(() -> service.create(client("FIDO01"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
    }

    @Test void createFillsDefaultsAndAudits() {
        when(repo.existsById("FIDO03")).thenReturn(false);
        CcfaFidoclient saved = service.create(client("FIDO03"));
        assertThat(saved.getStatus()).isEqualTo("ON");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_FIDOCLIENT CREATE FIDO03");
    }

    @Test void createKeepsGivenStatus() {
        when(repo.existsById("FIDO04")).thenReturn(false);
        CcfaFidoclient c = client("FIDO04"); c.setStatus("OFF");
        assertThat(service.create(c).getStatus()).isEqualTo("OFF");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        CcfaFidoclient existing = client("FIDO01");
        existing.setCreatedtime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById("FIDO01")).thenReturn(java.util.Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaFidoclient out = service.update("FIDO01", c -> c.setStatus("OFF"));
        assertThat(out.getCreatedtime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(out.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.UPDATE, "CCFA_FIDOCLIENT UPDATE FIDO01");
    }

    @Test void sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "servercode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("servercode", "servername", "status", "updatedtime");
    }
}
