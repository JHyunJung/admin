package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CompanyServiceTest {

    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    AppidRepository appids = mock(AppidRepository.class);
    UserinfoRepository users = mock(UserinfoRepository.class);
    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CompanyService service = new CompanyService(companies, mock(AuditLogger.class), appids, users, managers);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaCompany company(long idx) { CcfaCompany c = new CcfaCompany(); c.setIdx(idx); return c; }

    @Test void deleteBlockedWhenDependentsExist() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(appids.countByCompanyIdx(1L)).thenReturn(2L);
        when(users.countByCompanyIdx(1L)).thenReturn(0L);
        when(managers.countByCompanyIdx(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("앱 ID 2건");
    }

    @Test void deleteGlobalCompanyIsAlwaysBlocked() {
        when(companies.findById(0L)).thenReturn(Optional.of(company(0L)));
        assertThatThrownBy(() -> service.delete(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test void defaultsFilledOnCreate() {
        when(companies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaCompany c = service.create(new CcfaCompany());
        assertThat(c.getEnableType()).isEqualTo("Y");
        assertThat(c.getMaxAppid()).isZero();
        assertThat(c.getMaxAppserver()).isZero();
        assertThat(c.getMaxUser()).isZero();
        assertThat(c.getStarttime()).isNotNull();
        assertThat(c.getEndtime()).isEqualTo(java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        assertThat(c.getCreator()).isEqualTo(1L);
    }
}
