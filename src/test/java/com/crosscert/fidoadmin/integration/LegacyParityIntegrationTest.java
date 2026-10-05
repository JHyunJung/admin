package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.PasswordAgeStore;
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Oracle 에서 insert·열 탐지·무인증 경로를 한 번씩 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyParityIntegrationTest extends OracleContainerSupport {

    @Autowired CompanyService companies;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordExpiryPolicy expiry;
    @Autowired PasswordAgeStore ages;
    @Autowired MockMvc mvc;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void newCompanyStartsWithAllAaidsBlockedAndNoFdsPolicy() {
        loginSuper();
        CcfaCompany c = new CcfaCompany();
        c.setCompanyName("IT 신규 고객사");
        c.setCompanyType("TEST");
        c.setVendorCode("IT001");
        c.setContact("it@test.local");
        c.setEnableType("N");
        c.setMaxAppid(1L);
        c.setMaxAppserver(1L);
        c.setMaxUser(100L);
        Long idx = companies.create(c).getIdx();

        Integer criteria = jdbc.queryForObject("SELECT COUNT(DISTINCT AAID) FROM CRITERIA WHERE AAID IS NOT NULL", Integer.class);
        Integer blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx);
        assertThat(blocked).isEqualTo(criteria);
        // 이상 징후 탐지를 지웠으므로 기본 정책 행을 만들지 않는다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_FDS_POLICY WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();

        companies.delete(idx);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();
    }

    @Test void passwordExpiryIsEnabledOnLocalSchemaAndTouchWrites() {
        assertThat(expiry.enabled()).isTrue();
        String userId = jdbc.queryForObject("SELECT USER_ID FROM CCFA_MANAGER WHERE ROWNUM = 1", String.class);
        jdbc.update("UPDATE CCFA_MANAGER SET LAST_PW_CHANGE_DATE = NULL WHERE USER_ID = ?", userId);
        assertThat(ages.lastChanged(userId)).isEmpty();
        assertThat(expiry.isExpiredOnLogin(userId)).isFalse();
        assertThat(ages.lastChanged(userId)).isPresent();
    }

    @Test void externalLicenseIsReachableWithoutLogin() throws Exception {
        mvc.perform(get("/external/license/it-no-such-hash")).andExpect(status().isOk());
    }
}
