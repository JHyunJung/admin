package com.crosscert.fidoadmin.company.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.FdsPolicyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;

/**
 * FDS 정책은 COMPANY_IDX 가 그대로 PK 다(할당형 PK, 고객사당 1건). Task 10 처럼 select 를
 * 지우는 것만으로는 등록 시 식별자를 채울 방법이 없어지므로, 컨트롤러가 폼이 아니라
 * 유효 테넌트로 식별자를 채우는지를 직접 확인한다.
 */
class FdsPolicyTenantTest {

    private final SelectedTenant selected = new SelectedTenant();
    private final TenantContext tenant = new TenantContext(selected);
    private final FdsPolicyController controller = new FdsPolicyController(
        mock(FdsPolicyService.class), mock(CompanyLookup.class), tenant);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void loginSuperSelecting(long targetIdx) {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        selected.select(targetIdx);
    }

    /** 폼에는 companyIdx 가 없다(select 를 지웠다). 유효 테넌트가 식별자를 정한다. */
    @Test void FDS_정책_등록은_선택한_고객사를_PK_로_쓴다() {
        loginSuperSelecting(9L);
        CcfaFdsPolicy e = controller.toEntity(new FdsPolicyForm());
        assertThat(e.getCompanyIdx()).isEqualTo(9L);
    }

    /** 폼에 값이 있어도(과거 화면의 잔재나 크래프팅된 요청) 무시하고 유효 테넌트를 쓴다. */
    @Test void 폼에_값이_있어도_유효_테넌트로_덮어쓴다() {
        loginSuperSelecting(9L);
        FdsPolicyForm f = new FdsPolicyForm();
        f.setCompanyIdx(3L);
        CcfaFdsPolicy e = controller.toEntity(f);
        assertThat(e.getCompanyIdx()).isEqualTo(9L);
    }
}
