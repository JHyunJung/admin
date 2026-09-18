package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 고객사 조회는 2부의 여러 화면에서 select 옵션·이름 표시에 쓰인다.
 * 범위를 서비스 안에서 강제하지 않으면 COMPANY 화면에 다른 고객사 이름이 노출된다.
 */
class CompanyLookupTest {

    private final CcfaCompanyRepository repository = mock(CcfaCompanyRepository.class);
    private final CompanyLookup lookup = new CompanyLookup(repository, new TenantContext(new SelectedTenant()));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaCompany company(long idx, String name) {
        CcfaCompany c = new CcfaCompany(); c.setIdx(idx); c.setCompanyName(name); return c;
    }

    @Test void companyRoleSeesOnlyItsOwnCompany() {
        login(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")));

        assertThat(lookup.all()).extracting(CcfaCompany::getIdx).containsExactly(1L);
        verify(repository, never()).findAll(any(Sort.class));
    }

    @Test void superSeesAllCompanies() {
        login(0L);
        when(repository.findAll(any(Sort.class)))
            .thenReturn(List.of(company(0L, "전역"), company(1L, "국민은행"), company(2L, "테스트")));

        assertThat(lookup.all()).hasSize(3);
    }

    @Test void companyRoleCannotResolveAnotherCompanyName() {
        login(1L);

        assertThat(lookup.name(2L)).isEqualTo("#2");   // 이름을 알려주지 않는다
        verify(repository, never()).findById(2L);
    }

    @Test void companyRoleCanResolveItsOwnName() {
        login(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")));

        assertThat(lookup.name(1L)).isEqualTo("국민은행");
    }
}
