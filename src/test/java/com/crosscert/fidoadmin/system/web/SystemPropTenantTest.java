package com.crosscert.fidoadmin.system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.service.SystemPropService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 시스템 설정은 복합키(PROP_KEY + COMPANY_IDX)다. 등록은 폼이 아니라 유효 테넌트로
 * companyIdx 를 채워야 한다(고객사 select 가 사라졌으므로 폼에는 값이 없다).
 * URL 크래프팅 방어(checkTenant() 가 이미 처리한 부분)의 확인은 {@link SystemPropUrlTenantTest} 에 있다.
 */
class SystemPropTenantTest {

    private final SelectedTenant selected = new SelectedTenant();
    private final TenantContext tenant = new TenantContext(selected);
    private final SystemPropController controller = new SystemPropController(
        mock(SystemPropService.class), mock(CompanyLookup.class), tenant);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void loginSuperSelecting(long targetIdx) {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        selected.select(targetIdx);
    }

    private SystemPropForm form(String key, String value) {
        SystemPropForm f = new SystemPropForm();
        f.setPropKey(key); f.setPropValue(value); f.setShareType("NO");
        return f;
    }

    @Test void 시스템_설정_등록은_선택한_고객사를_PK_로_쓴다() {
        loginSuperSelecting(9L);
        CcfaSystemProp e = controller.toEntity(form("PW_FAIL_LIMIT", "5"));
        assertThat(e.getId().getCompanyIdx()).isEqualTo(9L);
    }
}
