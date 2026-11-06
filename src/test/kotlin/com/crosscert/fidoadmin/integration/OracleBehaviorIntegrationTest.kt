package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.PasswordAgeStore
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.service.CompanyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 실제 Oracle 에서 insert·열 탐지·무인증 경로를 한 번씩 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OracleBehaviorIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var companies: CompanyService
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var expiry: PasswordExpiryPolicy
    @Autowired lateinit var ages: PasswordAgeStore
    @Autowired lateinit var mvc: MockMvc

    @AfterEach
    fun clear() { SecurityContextHolder.clearContext() }

    private fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @Test
    fun newCompanyStartsWithAllAaidsBlockedAndNoFdsPolicy() {
        loginSuper()
        val c = CcfaCompany()
        c.companyName = "IT 신규 고객사"
        c.companyType = "TEST"
        c.vendorCode = "IT001"
        c.contact = "it@test.local"
        c.enableType = "N"
        c.maxAppid = 1L
        c.maxAppserver = 1L
        c.maxUser = 100L
        val idx = companies.create(c).idx!!

        val criteria = jdbc.queryForObject("SELECT COUNT(DISTINCT AAID) FROM CRITERIA WHERE AAID IS NOT NULL", Int::class.javaObjectType)
        val blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Int::class.javaObjectType, idx)
        assertThat(blocked).isEqualTo(criteria)
        // 이상 징후 탐지를 지웠으므로 기본 정책 행을 만들지 않는다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_FDS_POLICY WHERE COMPANY_IDX = ?", Int::class.javaObjectType, idx)).isZero()

        companies.delete(idx)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Int::class.javaObjectType, idx)).isZero()
    }

    @Test
    fun passwordExpiryIsEnabledOnLocalSchemaAndTouchWrites() {
        assertThat(expiry.enabled()).isTrue()
        val userId = jdbc.queryForObject("SELECT USER_ID FROM CCFA_MANAGER WHERE ROWNUM = 1", String::class.java)
        jdbc.update("UPDATE CCFA_MANAGER SET LAST_PW_CHANGE_DATE = NULL WHERE USER_ID = ?", userId)
        assertThat(ages.lastChanged(userId)).isEmpty()
        assertThat(expiry.isExpiredOnLogin(userId)).isFalse()
        assertThat(ages.lastChanged(userId)).isPresent()
    }

    @Test
    fun externalLicenseIsReachableWithoutLogin() {
        mvc.perform(get("/external/license/it-no-such-hash")).andExpect(status().isOk())
    }
}
