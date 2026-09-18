package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.List;
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

class CriteriaQueryServiceTest {

    CriteriaRepository repo = mock(CriteriaRepository.class);
    CriteriaQueryService service = new CriteriaQueryService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** COMPANY_IDX 가 없는 테이블이라 SUPER 전용(설계 3.3). URL 매처가 빠져도 서비스가 막는다. */
    @Test void companyRoleIsDeniedOnSearchAndGet() {
        login(1L);
        assertThatThrownBy(() -> service.search(new CriteriaSearchForm(), PageRequest.of(0, 20, service.defaultSort())))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).findAll(any(Specification.class), any(PageRequest.class));
        verify(repo, never()).findById(any());
    }

    @Test void superRoleCanSearchAndGet() {
        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        Criteria c = new Criteria(); c.setIdx(1L); c.setAaid("0012#0001");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        CriteriaSearchForm f = new CriteriaSearchForm(); f.setAaid("0012");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        assertThat(service.get(1L).getAaid()).isEqualTo("0012#0001");
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test void defaultSortIsIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void sortablePropertiesIncludeAaidAndUpdatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "aaid", "updatedtime");
    }
}
