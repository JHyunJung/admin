package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.web.AppidSearchForm;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AppidServiceTest {

    AppidRepository repo = mock(AppidRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    AppidService service = new AppidService(repo, audit, tenant);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void defaultsAndTimestampsFilledOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { Appid a = inv.getArgument(0); a.setIdx(10L); return a; });
        Appid in = new Appid(); in.setAppid("https://kbstar.com/facets.json"); in.setCompanyIdx(1L);

        Appid out = service.create(in);

        assertThat(out.getStatus()).isEqualTo("use");
        assertThat(out.getDeviceDefault()).isEqualTo("F");
        assertThat(out.getCreatedtime()).isNotNull();
        assertThat(out.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "APPID CREATE 10");
    }

    @Test void explicitValuesAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appid in = new Appid(); in.setAppid("x"); in.setStatus("unuse"); in.setDeviceDefault("T");
        Appid out = service.create(in);
        assertThat(out.getStatus()).isEqualTo("unuse");
        assertThat(out.getDeviceDefault()).isEqualTo("T");
    }

    /** companyIdxAttribute 배선 확인: COMPANY 역할 등록은 폼의 고객사 값을 무시하고 자기 고객사로 강제된다. */
    @Test void companyRoleCreateForcesOwnTenant() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appid in = new Appid(); in.setAppid("x"); in.setCompanyIdx(999L);
        assertThat(service.create(in).getCompanyIdx()).isEqualTo(1L);
    }

    @Test void searchGoesThroughSpecificationWithGivenPageable() {
        login(1L);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        AppidSearchForm f = new AppidSearchForm(); f.setAppid("kb"); f.setStatus("use");
        Pageable p = PageRequest.of(0, 20, Sort.by("idx"));

        service.search(f, p);

        verify(repo).findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(p));
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "appid", "servicename", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
