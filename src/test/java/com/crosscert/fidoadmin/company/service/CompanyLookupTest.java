package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * 고객사 이름은 사이드바(요청마다)와 감사 로그(기록마다)가 묻는다. 거의 바뀌지 않는 값을
     * 요청마다 DB 에서 다시 읽지 않는다. 바뀌면 {@link CompanyLookup#evict} 가 지운다.
     */
    @Test void nameIsReadFromDatabaseOnlyOnce() {
        login(0L);
        when(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")));

        assertThat(lookup.name(1L)).isEqualTo("국민은행");
        assertThat(lookup.name(1L)).isEqualTo("국민은행");

        verify(repository, times(1)).findById(1L);
    }

    /** 없는 고객사("#IDX")는 기억하지 않는다 — 나중에 만들어지면 바로 보여야 한다. */
    @Test void missingCompanyIsNotCached() {
        login(0L);
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThat(lookup.name(7L)).isEqualTo("#7");

        when(repository.findById(7L)).thenReturn(Optional.of(company(7L, "새 고객사")));
        assertThat(lookup.name(7L)).isEqualTo("새 고객사");
    }

    @Test void evictMakesNextLookupHitTheDatabase() {
        login(0L);
        when(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")));
        lookup.name(1L);

        lookup.evict(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행")));

        assertThat(lookup.name(1L)).isEqualTo("KB국민은행");
        verify(repository, times(2)).findById(1L);
    }

    /** 캐시는 이름만 기억한다. 범위 규칙(다른 고객사 이름 비노출)은 캐시 앞에서 그대로 적용된다. */
    @Test void cacheDoesNotLeakNamesAcrossRoles() {
        login(0L);
        when(repository.findById(2L)).thenReturn(Optional.of(company(2L, "테스트")));
        assertThat(lookup.name(2L)).isEqualTo("테스트");

        SecurityContextHolder.clearContext();
        login(1L);
        assertThat(lookup.name(2L)).isEqualTo("#2");
    }
}
