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
    SelectedTenant selected = new SelectedTenant();
    LicenseService service = new LicenseService(repo, mock(AuditLogger.class), companies,
        new TenantContext(selected));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        selected.select(1L); // 각 테스트가 다루는 행의 소유 COMPANY_IDX 와 맞춘다
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

    /**
     * COMPANY_NAME 은 매 수정마다 CCFA_COMPANY 에서 다시 읽어 온다(생성 때만이 아니라).
     * 예전 테스트는 수정 폼으로 COMPANY_IDX 자체를 바꿔 보았지만, 유효 테넌트가 소유자를
     * 정하는 새 모델에서는 update() 가 companyIdx 를 선택 테넌트로 되돌려 그 경로가 성립하지
     * 않는다(Task 3). 그래서 소속은 그대로 두고 CCFA_COMPANY 쪽 이름이 바뀐 상황으로 검증한다 —
     * 동기화 의도(폼이 아니라 CCFA_COMPANY 가 이긴다)는 그대로 유지된다.
     */
    @Test void companyNameResyncedFromCompanyOnUpdate() {
        CcfaLicense existing = new CcfaLicense(); existing.setIdx(9L); existing.setCompanyIdx(1L); existing.setCompanyName("KB국민은행(구)");
        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행(신)")));

        service.update(9L, l -> l.setServiceName("kbstar-v2"));

        assertThat(existing.getCompanyName()).isEqualTo("KB국민은행(신)");
        assertThat(existing.getUpdatedtime()).isNotNull();
    }

    /**
     * "없는 고객사" 를 검증하려면 create() 가 실제로 조회하는 키(선택 테넌트, 폼 값이 아니다)가
     * 없는 고객사여야 한다. 선택 테넌트를 77 로 바꾸고 그 키를 비워 둔다.
     */
    @Test void unknownCompanyLeavesNameNull() {
        selected.select(77L);
        when(companies.findById(77L)).thenReturn(Optional.empty());
        CcfaLicense l = new CcfaLicense(); l.setCompanyIdx(77L);
        assertThat(service.create(l).getCompanyName()).isNull();
    }

    @Test void sortableIncludesServiceName() {
        assertThat(service.sortableProperties()).contains("idx", "serviceName", "createdtime");
    }
}
