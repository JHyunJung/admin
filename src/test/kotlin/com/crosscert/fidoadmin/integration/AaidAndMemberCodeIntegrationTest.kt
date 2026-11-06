package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.fido.entity.Appserver
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.crosscert.fidoadmin.fido.service.AppserverService
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles

/**
 * AAID 등록과 멤버코드 생성을 실제 Oracle 에서 한 번씩 돌린다.
 * 단위 테스트는 저장소와 JDBC 를 목으로 바꾸므로 시퀀스·CLOB·JPA 플러시와 JDBC insert 의 순서는 여기서만 확인된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AaidAndMemberCodeIntegrationTest : OracleContainerSupport() {

    companion object {
        const val AAID = "IT99#0001"
    }

    @Autowired lateinit var criteria: CriteriaQueryService
    @Autowired lateinit var appservers: AppserverService
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun clear() {
        SecurityContextHolder.clearContext()
        jdbc.update("DELETE FROM CCFA_COMPANY_AAID WHERE AAID = ?", AAID)
        jdbc.update("DELETE FROM CRITERIA WHERE AAID = ?", AAID)
        jdbc.update("DELETE FROM APPSERVER WHERE MEMBER_ID = 'it-member-code'")
    }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "it", null, "IT", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @Test
    fun superRegistersAaidBlockedForEveryCompanyAndRejectsDuplicate() {
        login(0L)
        val json = "{\"aaid\":\"" + AAID + "\",\"keyProtection\":2,\"attestationTypes\":[15879]}"

        val saved: Criteria = criteria.create(json)

        assertThat(saved.idx).isNotNull()
        assertThat(jdbc.queryForObject("SELECT DBMS_LOB.SUBSTR(JSONDATA, 4000, 1) FROM CRITERIA WHERE IDX = ?",
            String::class.java, saved.idx)).isEqualTo(json)
        assertThat(jdbc.queryForObject("SELECT METAHASH FROM CRITERIA WHERE IDX = ?", String::class.java, saved.idx))
            .isEqualTo(saved.metahash).hasSize(43)
        val companies = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY", Int::class.javaObjectType)
        val blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE AAID = ?", Int::class.javaObjectType, AAID)
        assertThat(blocked).isEqualTo(companies)

        assertThatThrownBy { criteria.create(json) }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage("이미 등록된 AAID 입니다.")
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CRITERIA WHERE AAID = ?", Int::class.javaObjectType, AAID)).isEqualTo(1)
    }

    @Test
    fun appserverCreateStoresGeneratedMemberCode() {
        login(1L)
        val `in` = Appserver()
        `in`.memberId = "it-member-code"

        val idx: Long? = appservers.create(`in`).idx

        assertThat(jdbc.queryForObject("SELECT MEMBER_CODE FROM APPSERVER WHERE IDX = ?", String::class.java, idx))
            .matches("[A-Z0-9]{10}")
    }
}
