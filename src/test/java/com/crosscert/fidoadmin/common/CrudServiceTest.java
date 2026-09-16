package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CrudServiceTest {

    interface LicenseRepo extends AdminRepository<CcfaLicense, Long> {}

    LicenseRepo repo = mock(LicenseRepo.class);
    AuditLogger audit = mock(AuditLogger.class);

    /** COMPANY_IDX 가 있는 테이블의 대표 구현. */
    CrudService<CcfaLicense, Long, SearchForm> service = new CrudService<>(repo, audit) {
        @Override protected Specification<CcfaLicense> toSpecification(SearchForm f) { return null; }
        @Override protected String companyIdxAttribute() { return "companyIdx"; }
        @Override protected Long companyIdxOf(CcfaLicense e) { return e.getCompanyIdx(); }
        @Override protected void setCompanyIdx(CcfaLicense e, Long c) { e.setCompanyIdx(c); }
        @Override public String idOf(CcfaLicense e) { return String.valueOf(e.getIdx()); }
        @Override protected String tableName() { return "CCFA_LICENSE"; }
        @Override protected void touchCreated(CcfaLicense e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
        @Override protected void touchUpdated(CcfaLicense e, LocalDateTime now) { e.setUpdatedtime(now); }
    };

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaLicense license(long idx, long companyIdx) {
        CcfaLicense l = new CcfaLicense(); l.setIdx(idx); l.setCompanyIdx(companyIdx); return l;
    }

    @Test void getRejectsOtherTenant() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)));
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(TenantMismatchException.class);
    }

    @Test void superCanGetAnyTenant() {
        login(0L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)));
        assertThat(service.get(9L).getCompanyIdx()).isEqualTo(2L);
    }

    @Test void getThrowsWhenMissing() {
        login(0L);
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test void createForcesTenantAndTimestampsAndAudits() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaLicense l = inv.getArgument(0); l.setIdx(100L); return l; });
        CcfaLicense in = license(0L, 999L);

        CcfaLicense out = service.create(in);

        assertThat(out.getCompanyIdx()).isEqualTo(1L);
        assertThat(out.getCreatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_LICENSE CREATE 100");
    }

    @Test void superKeepsGivenTenantOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(license(0L, 5L)).getCompanyIdx()).isEqualTo(5L);
    }

    @Test void updateMutatesWithinTenant() {
        login(1L);
        CcfaLicense existing = license(9L, 1L);
        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(9L, l -> l.setServiceName("kbstar"));

        assertThat(existing.getServiceName()).isEqualTo("kbstar");
        assertThat(existing.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 9");
    }

    @Test void deleteChecksTenantAndAudits() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 1L)));
        service.delete(9L);
        verify(repo).delete(any(CcfaLicense.class));
        verify(audit).log(AuditType.DELETE, "CCFA_LICENSE DELETE 9");
    }

    @Test void searchAddsTenantFilterForCompanyRole() {
        login(1L);
        Page<CcfaLicense> page = new PageImpl<>(java.util.List.of());
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx")));

        ArgumentCaptor<Specification<CcfaLicense>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), eq(PageRequest.of(0, 20, Sort.by("idx"))));
        assertThat(captor.getValue()).isNotNull();
    }
}
