package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FdsMonitorQueryServiceTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    CcfaFdsPolicyRepository policies = mock(CcfaFdsPolicyRepository.class);
    FidoLogTable tables = mock(FidoLogTable.class);
    FdsMonitorQueryService service =
        new FdsMonitorQueryService(jdbc, new TenantContext(new SelectedTenant()), policies, tables);

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void policy(String andTerm, String orTerm) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(1L); p.setAndTerm(andTerm); p.setOrTerm(orTerm);
        when(policies.findById(1L)).thenReturn(Optional.of(p));
    }

    // ---- 정책 해석 ----

    @Test void andTermAloneIsUsed() {
        policy("30", null);
        assertThat(service.policyTerm()).contains(30);
    }

    @Test void orTermAloneIsUsed() {
        policy(null, "45");
        assertThat(service.policyTerm()).contains(45);
    }

    /** 둘 다 있으면 짧은 쪽 — 더 민감한 기준이 결과를 더 많이 잡는다. */
    @Test void bothTermsPickTheShorter() {
        policy("60", "20");
        assertThat(service.policyTerm()).contains(20);
    }

    @Test void noPolicyRowMeansAbsent() {
        when(policies.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.policyTerm()).isEmpty();
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장하고, 운영자가 "0" 이나 "30초" 를 넣어 둘 수 있다. 전부 없음이다. */
    @Test void unparsableOrNonPositiveTermsAreAbsent() {
        assertThat(FdsMonitorQueryService.parseTerm(null)).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("   ")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("0")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("-5")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("30초")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm(" 30 ")).contains(30);
    }

    @Test void policyTermReadsTheEffectiveTenant() {
        policy("10", null);
        service.policyTerm();
        org.mockito.Mockito.verify(policies).findById(1L);
    }
}
