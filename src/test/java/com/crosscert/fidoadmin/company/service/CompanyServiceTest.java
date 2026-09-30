package com.crosscert.fidoadmin.company.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import java.util.List;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged;
import com.crosscert.fidoadmin.audit.AuditType;
import org.springframework.context.ApplicationEventPublisher;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
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
    CcfaSystemPropRepository props = mock(CcfaSystemPropRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    CcfaFdsPolicyRepository fdsPolicies = mock(CcfaFdsPolicyRepository.class);
    CriteriaQueryService criteria = mock(CriteriaQueryService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CompanyService service = new CompanyService(companies, audit, appids, users, managers, props,
        new TenantContext(new SelectedTenant()), fdsPolicies, criteria, events);

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

    private CcfaSystemProp prop(String key, long companyIdx, String value) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, companyIdx));
        p.setPropValue(value);
        p.setShareType("NO");
        return p;
    }

    /** 이전 어드민의 insertSystemProp: 회사 0 의 고객사별 설정이 새 고객사의 기본값이 된다. 있는 키는 덮지 않는다. */
    @SuppressWarnings("unchecked")
    @Test void createCopiesPerCompanyPropsFromGlobalWithoutOverwriting() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class)))
            .thenReturn(List.of(prop("SESSION_TIMEOUT", 0L, "30"), prop("SERVICE_NAME", 0L, "default")));
        when(props.existsById(new CcfaSystemPropId("SESSION_TIMEOUT", 7L))).thenReturn(true);
        when(props.existsById(new CcfaSystemPropId("SERVICE_NAME", 7L))).thenReturn(false);

        service.create(new CcfaCompany());

        ArgumentCaptor<CcfaSystemProp> saved = ArgumentCaptor.forClass(CcfaSystemProp.class);
        verify(props).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new CcfaSystemPropId("SERVICE_NAME", 7L));
        assertThat(saved.getValue().getPropValue()).isEqualTo("default");
        assertThat(saved.getValue().getShareType()).isEqualTo("NO");
    }

    /** 삭제 검사에서 막히면 설정 행도 건드리지 않는다 — super.delete 가 먼저 던진다. */
    @Test void blockedDeleteLeavesPropsAlone() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(appids.countByCompanyIdx(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);

        verify(props, never()).deleteAll(any());
    }

    @SuppressWarnings("unchecked")
    @Test void deleteRemovesThatCompanysPerCompanyProps() {
        when(companies.findById(3L)).thenReturn(Optional.of(company(3L)));
        List<CcfaSystemProp> rows = List.of(prop("SERVICE_NAME", 3L, "kb"));
        when(props.findAll(any(Specification.class))).thenReturn(rows);

        service.delete(3L);

        verify(companies).delete(any(CcfaCompany.class));
        verify(props).deleteAll(rows);
    }

    @Test void blankVendorCodeIsNotLookedUp() {
        assertThat(service.findByVendorCode("  ")).isEmpty();
        assertThat(service.findByVendorCode(null)).isEmpty();
        verify(companies, never()).findFirstByVendorCode(any());
    }

    @SuppressWarnings("unchecked")
    @Test void createDisablesAllAaidsAndAddsDefaultFdsPolicy() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.disableAllFor(7L)).thenReturn(3);
        when(fdsPolicies.existsById(7L)).thenReturn(false);

        service.create(new CcfaCompany());

        verify(criteria).disableAllFor(7L);
        ArgumentCaptor<CcfaFdsPolicy> saved = ArgumentCaptor.forClass(CcfaFdsPolicy.class);
        verify(fdsPolicies).save(saved.capture());
        assertThat(saved.getValue().getCompanyIdx()).isEqualTo(7L);
        assertThat(saved.getValue().getAndCountry()).isEqualTo("NO");
        assertThat(saved.getValue().getOrCountry()).isEqualTo("NO");
        assertThat(saved.getValue().getCreatedtime()).isNotNull();
        assertThat(saved.getValue().getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (3건)");
        verify(audit).log(AuditType.CREATE, "CCFA_FDS_POLICY 기본값 고객사 7");
        verify(events).publishEvent(new FidoConfigChanged("고객사 생성 7"));
    }

    @SuppressWarnings("unchecked")
    @Test void createKeepsExistingFdsPolicy() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(fdsPolicies.existsById(7L)).thenReturn(true);
        service.create(new CcfaCompany());
        verify(fdsPolicies, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test void createWithNoCriteriaSkipsAaidAudit() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.disableAllFor(7L)).thenReturn(0);
        service.create(new CcfaCompany());
        verify(audit, never()).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (0건)");
    }

    @SuppressWarnings("unchecked")
    @Test void deleteRemovesAaidRowsAndFdsPolicy() {
        when(companies.findById(7L)).thenReturn(Optional.of(company(7L)));
        when(appids.countByCompanyIdx(7L)).thenReturn(0L);
        when(users.countByCompanyIdx(7L)).thenReturn(0L);
        when(managers.countByCompanyIdx(7L)).thenReturn(0L);
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.deleteAllFor(7L)).thenReturn(5);
        when(fdsPolicies.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(criteria).deleteAllFor(7L);
        verify(fdsPolicies).deleteById(7L);
        verify(audit).log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 7 (5건)");
        verify(audit).log(AuditType.DELETE, "CCFA_FDS_POLICY DELETE 고객사 7");
    }

    @Test void blockedDeleteLeavesAaidAndFdsAlone() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(appids.countByCompanyIdx(1L)).thenReturn(2L);
        when(users.countByCompanyIdx(1L)).thenReturn(0L);
        when(managers.countByCompanyIdx(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
        verify(criteria, never()).deleteAllFor(any());
        verify(fdsPolicies, never()).deleteById(any());
    }
}
