package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextTest {

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var user = new ManagerUserDetails(1L, "u", null, "이름", companyIdx, "회사", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test void superWhenCompanyZero() {
        login(0L);
        assertThat(TenantContext.isSuper()).isTrue();
        assertThat(TenantContext.companyIdx()).isEqualTo(0L);
    }

    @Test void companyRoleOtherwise() {
        login(7L);
        assertThat(TenantContext.isSuper()).isFalse();
        assertThat(TenantContext.companyIdx()).isEqualTo(7L);
        assertThat(TenantContext.require().getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    @Test void requireThrowsWhenAnonymous() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
    }

    /** null COMPANY_IDX 를 0 으로 보정하면 ROLE_SUPER 로 권한이 상승한다. 거부해야 한다. */
    @Test void nullCompanyIdxIsRejectedInsteadOfBecomingSuper() {
        assertThatThrownBy(() ->
            new com.crosscert.fidoadmin.auth.ManagerUserDetails(
                1L, "broken", null, "이름", null, "회사", true, true))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
