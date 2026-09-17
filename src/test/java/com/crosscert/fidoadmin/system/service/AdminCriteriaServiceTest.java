package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository;
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminCriteriaServiceTest {

    CcfaCriteriaRepository repo = mock(CcfaCriteriaRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    AdminCriteriaService service = new AdminCriteriaService(repo, audit);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaCriteria criteria(long idx) { CcfaCriteria c = new CcfaCriteria(); c.setIdx(idx); c.setAaid("0012#0001"); return c; }

    @Test void createSetsBothTimestampsAndAudits() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaCriteria c = inv.getArgument(0); c.setIdx(7L); return c; });
        CcfaCriteria out = service.create(criteria(0L));
        assertThat(out.getCreatetime()).isNotNull();
        assertThat(out.getUpdatedtime()).isEqualTo(out.getCreatetime());
        verify(audit).log(AuditType.CREATE, "CCFA_CRITERIA CREATE 7");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        login(0L);
        CcfaCriteria existing = criteria(7L);
        existing.setCreatetime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById(7L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(7L, c -> c.setMetahash("h2"));
        assertThat(existing.getCreatetime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(existing.getUpdatedtime()).isAfter(existing.getCreatetime());
        verify(audit).log(AuditType.UPDATE, "CCFA_CRITERIA UPDATE 7");
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용(설계 3.3). */
    @Test void companyRoleIsDenied() {
        login(1L);
        assertThatThrownBy(() -> service.search(new AdminCriteriaSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(7L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void superSearchesWithoutTenantFilter() {
        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(java.util.List.of()));
        AdminCriteriaSearchForm f = new AdminCriteriaSearchForm();
        f.setAaid("0012");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
        assertThat(service.sortableProperties()).contains("idx", "aaid", "updatedtime");
    }
}
