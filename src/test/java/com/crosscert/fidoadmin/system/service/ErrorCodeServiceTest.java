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
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ErrorCodeServiceTest {

    CcfaErrorTableRepository repo = mock(CcfaErrorTableRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    TenantContext tenant = new TenantContext(new SelectedTenant());
    ErrorCodeService service = new ErrorCodeService(repo, audit, em, tenant);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery(anyString())).thenReturn(mock(Query.class));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaErrorTable code(String c) { CcfaErrorTable e = new CcfaErrorTable(); e.setErrorCode(c); e.setErrorMessage("m"); return e; }

    @Test void createRejectsDuplicateCode() {
        when(repo.existsById("1200")).thenReturn(true);
        assertThatThrownBy(() -> service.create(code("1200"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
    }

    @Test void createPersistsAndAudits() {
        when(repo.existsById("1499")).thenReturn(false);
        CcfaErrorTable saved = service.create(code("1499"));
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_ERROR_TABLE CREATE 1499");
    }

    @Test void deleteAudits() {
        when(repo.findById("1498")).thenReturn(java.util.Optional.of(code("1498")));
        service.delete("1498");
        verify(repo).delete(any(CcfaErrorTable.class));
        verify(audit).log(AuditType.DELETE, "CCFA_ERROR_TABLE DELETE 1498");
    }

    @Test void sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "errorCode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("errorCode", "errorType");
    }
}
