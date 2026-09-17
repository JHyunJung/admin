package com.crosscert.fidoadmin.company.service;

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
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FdsPolicyServiceTest {

    CcfaFdsPolicyRepository repo = mock(CcfaFdsPolicyRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    FdsPolicyService service = new FdsPolicyService(repo, audit, em);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(7L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaFdsPolicy policy(Long companyIdx) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(companyIdx); p.setAndCountry("KR"); p.setOrCountry("KR");
        return p;
    }

    /** COMPANY 는 폼에 다른 고객사 IDX 를 넣어도 자기 고객사 행만 만들 수 있다(PK 가 COMPANY_IDX). */
    @Test void companyCreateForcesOwnCompanyAsKey() {
        login(1L);
        when(repo.existsById(1L)).thenReturn(false);

        CcfaFdsPolicy saved = service.create(policy(99L));

        assertThat(saved.getCompanyIdx()).isEqualTo(1L);
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(repo).existsById(1L);
        verify(em).persist(saved);
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "CCFA_FDS_POLICY CREATE 1");
    }

    @Test void duplicateCompanyIsRejectedWithoutOverwrite() {
        login(1L);
        when(repo.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(policy(1L))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void superKeepsChosenCompany() {
        login(0L);
        when(repo.existsById(2L)).thenReturn(false);
        assertThat(service.create(policy(2L)).getCompanyIdx()).isEqualTo(2L);
        verify(em).persist(any());
    }

    @Test void updateTouchesUpdatedtimeOnly() {
        login(1L);
        CcfaFdsPolicy existing = policy(1L);
        existing.setCreatedtime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, p -> p.setAndTerm("60"));

        assertThat(existing.getAndTerm()).isEqualTo("60");
        assertThat(existing.getCreatedtime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(existing.getUpdatedtime()).isAfter(existing.getCreatedtime());
        verify(audit).log(AuditType.UPDATE, "CCFA_FDS_POLICY UPDATE 1");
    }
}
