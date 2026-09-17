package com.crosscert.fidoadmin.common;

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
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AssignedIdCrudServiceTest {

    interface InfoRepo extends AdminRepository<CcfaSystemInfo, String> {}

    InfoRepo repo = mock(InfoRepo.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);

    AssignedIdCrudService<CcfaSystemInfo, String, SearchForm> service =
        new AssignedIdCrudService<>(repo, audit, em) {
            @Override protected Specification<CcfaSystemInfo> toSpecification(SearchForm f) { return null; }
            @Override protected String companyIdxAttribute() { return null; }
            @Override protected Long companyIdxOf(CcfaSystemInfo e) { return null; }
            @Override protected void setCompanyIdx(CcfaSystemInfo e, Long c) {}
            @Override public String idOf(CcfaSystemInfo e) { return e.getPropKey(); }
            @Override protected String tableName() { return "CCFA_SYSTEM_INFO"; }
            @Override protected String assignedId(CcfaSystemInfo e) { return e.getPropKey(); }
        };

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemInfo info(String key) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue("v"); return i; }

    /** 핵심: 기존 행이 있으면 save() 의 MERGE 로 덮어쓰지 않고 거부한다. */
    @Test void createRejectsExistingKeyWithoutTouchingRow() {
        when(repo.existsById("VERSION")).thenReturn(true);
        assertThatThrownBy(() -> service.create(info("VERSION")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("VERSION");
        verify(em, never()).persist(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void createPersistsNewKeyAndAudits() {
        when(repo.existsById("NEW_KEY")).thenReturn(false);
        CcfaSystemInfo saved = service.create(info("NEW_KEY"));
        assertThat(saved.getPropKey()).isEqualTo("NEW_KEY");
        verify(em).persist(saved);
        verify(em).flush();
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_INFO CREATE NEW_KEY");
    }

    @Test void createRejectsBlankKey() {
        assertThatThrownBy(() -> service.create(info(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(info("  "))).isInstanceOf(IllegalArgumentException.class);
        verify(em, never()).persist(any());
    }

    /** 존재 검사와 INSERT 사이에 다른 세션이 같은 키를 넣은 경우(경쟁) 도 같은 예외로 화면에 전달된다. */
    @Test void persistFailureBecomesDataIntegrityViolation() {
        when(repo.existsById("RACE")).thenReturn(false);
        Mockito.doThrow(new PersistenceException("ORA-00001")).when(em).flush();
        assertThatThrownBy(() -> service.create(info("RACE"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(audit, never()).log(any(), any());
    }

    /** update 는 기존 경로(get → mutator → save) 그대로다. */
    @Test void updateStillUsesSave() {
        when(repo.findById("VERSION")).thenReturn(java.util.Optional.of(info("VERSION")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaSystemInfo out = service.update("VERSION", i -> i.setPropValue("2"));
        assertThat(out.getPropValue()).isEqualTo("2");
        verify(repo).save(any());
        verify(em, never()).persist(any());
    }
}
