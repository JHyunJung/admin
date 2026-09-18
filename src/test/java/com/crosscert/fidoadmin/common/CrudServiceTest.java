package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);

    /** COMPANY_IDX 가 있는 테이블의 대표 구현. */
    CrudService<CcfaLicense, Long, SearchForm> service = new CrudService<>(repo, audit, tenant) {
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

    /** SUPER 로 로그인하고 고객사를 선택한다. */
    private void loginSuperSelecting(long companyIdx) {
        login(0L);
        selected.select(companyIdx);
    }

    private CcfaLicense license(long idx, long companyIdx) {
        CcfaLicense l = new CcfaLicense(); l.setIdx(idx); l.setCompanyIdx(companyIdx); return l;
    }

    @Test void getRejectsOtherTenant() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)));
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(TenantMismatchException.class);
    }

    /**
     * 이번 변경의 핵심. 예전에는 checkTenant() 가 isSuper() 면 그냥 통과해
     * SUPER 가 URL 로 임의 테넌트의 행을 열 수 있었다(superCanGetAnyTenant 로 불렸고
     * 그 통과를 정상 동작으로 단언했다). 이제는 선택한 테넌트와 다르면 거부된다.
     */
    @Test void superGetsRejectedForOtherTenant() {
        loginSuperSelecting(9L);
        when(repo.findById(1L)).thenReturn(Optional.of(license(1L, 3L)));

        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(TenantMismatchException.class);
    }

    @Test void superGetsSelectedTenantRow() {
        loginSuperSelecting(9L);
        when(repo.findById(1L)).thenReturn(Optional.of(license(1L, 9L)));

        assertThat(service.get(1L).getCompanyIdx()).isEqualTo(9L);
    }

    @Test void getThrowsWhenMissing() {
        loginSuperSelecting(9L);
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    /** 미선택 SUPER 는 조회 자체가 성립하지 않는다. */
    @Test void searchThrowsWhenSuperHasNoTenantSelected() {
        login(0L);
        assertThatThrownBy(() -> service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(NoTenantSelectedException.class);
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

    /** 등록은 역할과 무관하게 유효 테넌트 소유가 된다. 폼/엔티티에 실린 값은 무시된다. */
    @Test void superCreatedRowIsOwnedBySelectedTenant() {
        loginSuperSelecting(9L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(license(0L, 5L)).getCompanyIdx()).isEqualTo(9L);
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
        // Specification 을 실제로 실행해 companyIdx = 1 조건이 들어갔는지 확인한다.
        assertThat(companyIdxEqualsIn(captor.getValue())).isEqualTo(1L);
    }

    /**
     * 목록 조회의 테넌트 필터는 격리의 핵심이다. Specification 이 null 이 아닌지만 보면
     * 필터를 제거해도 테스트가 통과하므로, 실제로 평가해 companyIdx 등치 조건을 확인한다.
     *
     * 예전에는 SUPER 면 필터가 없어 전체가 조회됐다(이 단언이 isNull() 이었다).
     * 이제는 선택한 고객사로 걸린다.
     */
    @Test void searchFiltersSuperBySelectedCompany() {
        loginSuperSelecting(9L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(java.util.List.of()));

        service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx")));

        ArgumentCaptor<Specification<CcfaLicense>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(PageRequest.class));
        assertThat(companyIdxEqualsIn(captor.getValue())).isEqualTo(9L);
    }

    /**
     * Specification 을 가짜 Criteria API 위에서 평가해 companyIdx 에 걸린 등치 값을 뽑아낸다.
     * 조건이 없으면 null 을 돌려준다.
     */
    /**
     * Specification 을 실제 Criteria API 위에서 평가해 companyIdx 등치 값을 뽑아낸다.
     * 조건이 없으면 null.
     */
    private static Long companyIdxEqualsIn(Specification<CcfaLicense> spec) {
        return CriteriaProbe.equalsValueFor(spec, "companyIdx");
    }
    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용이어야 한다(설계 3.3). */
    @Test void globalTableIsSuperOnly() {
        CrudService<CcfaLicense, Long, SearchForm> global = new CrudService<>(repo, audit, tenant) {
            @Override protected Specification<CcfaLicense> toSpecification(SearchForm f) { return null; }
            @Override protected String companyIdxAttribute() { return null; }
            @Override protected Long companyIdxOf(CcfaLicense e) { return null; }
            @Override protected void setCompanyIdx(CcfaLicense e, Long c) {}
            @Override public String idOf(CcfaLicense e) { return "1"; }
            @Override protected String tableName() { return "CCFA_MENU"; }
        };

        login(1L);
        assertThatThrownBy(() -> global.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> global.get(1L))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(java.util.List.of()));
        global.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx")));
    }
}
