package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LicenseServiceTest {

    CcfaLicenseRepository repo = mock(CcfaLicenseRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    LicenseService service = new LicenseService(repo, mock(AuditLogger.class), companies,
        new TenantContext(new SelectedTenant()));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaCompany company(long idx, String name) { CcfaCompany c = new CcfaCompany(); c.setIdx(idx); c.setCompanyName(name); return c; }

    /** COMPANY_NAME 은 비정규화 컬럼이다. 폼이 아니라 CCFA_COMPANY 에서 채운다. */
    @Test void companyNameSyncedOnCreate() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행")));
        CcfaLicense l = new CcfaLicense(); l.setCompanyIdx(1L); l.setServiceName("kbstar");

        CcfaLicense saved = service.create(l);

        assertThat(saved.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
    }

    @Test void companyNameSyncedWhenCompanyChangesOnUpdate() {
        CcfaLicense existing = new CcfaLicense(); existing.setIdx(9L); existing.setCompanyIdx(1L); existing.setCompanyName("KB국민은행");
        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(companies.findById(2L)).thenReturn(Optional.of(company(2L, "테스트고객사")));

        service.update(9L, l -> l.setCompanyIdx(2L));

        assertThat(existing.getCompanyName()).isEqualTo("테스트고객사");
        assertThat(existing.getUpdatedtime()).isNotNull();
    }

    @Test void unknownCompanyLeavesNameNull() {
        when(companies.findById(77L)).thenReturn(Optional.empty());
        CcfaLicense l = new CcfaLicense(); l.setCompanyIdx(77L);
        assertThat(service.create(l).getCompanyName()).isNull();
    }

    @Test void sortableIncludesServiceName() {
        assertThat(service.sortableProperties()).contains("idx", "serviceName", "createdtime");
    }
}
