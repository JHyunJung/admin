package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

/**
 * 고객사·라이선스 파생 쿼리를 실제 Oracle 에 실행해 현재 동작을 고정한다.
 * 단위 테스트는 저장소를 mock 으로 스텁하므로 "중복이 있을 때 무엇을 주는가"는 여기서만 확인된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CompanyDerivedQueryIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var licenses: CcfaLicenseRepository
    @Autowired lateinit var companies: CcfaCompanyRepository

    @Test
    fun duplicateHashvalueReturnsLowestIdxRow() {
        val first = licenses.saveAndFlush(license("IT-DUP-HASH", "first"))
        val second = licenses.saveAndFlush(license("IT-DUP-HASH", "second"))
        em.clear()
        assertThat(first.idx!!).isLessThan(second.idx!!)

        val found = licenses.findFirstByHashvalueOrderByIdxAsc("IT-DUP-HASH")

        assertThat(found).isPresent
        assertThat(found.get().idx).isEqualTo(first.idx)
        assertThat(found.get().license).isEqualTo("first")
    }

    @Test
    fun duplicateVendorCodeReturnsOneRowWithoutException() {
        val a = companies.saveAndFlush(company("IT-DUP-VC"))
        val b = companies.saveAndFlush(company("IT-DUP-VC"))
        em.clear()

        val found = companies.findFirstByVendorCode("IT-DUP-VC")

        assertThat(found).isPresent
        assertThat(found.get().vendorCode).isEqualTo("IT-DUP-VC")
        assertThat(found.get().idx).isIn(a.idx, b.idx)
    }

    private fun license(hash: String, body: String): CcfaLicense {
        val l = CcfaLicense()
        l.companyIdx = 1L; l.license = body; l.hashvalue = hash
        l.createdtime = LocalDateTime.now(); l.updatedtime = LocalDateTime.now()
        return l
    }

    private fun company(vendorCode: String): CcfaCompany {
        val c = CcfaCompany()
        c.companyName = "통합테스트"; c.vendorCode = vendorCode; c.enableType = "Y"
        c.maxAppid = 0L; c.maxAppserver = 0L; c.maxUser = 0L
        c.createdtime = LocalDateTime.now(); c.updatedtime = LocalDateTime.now()
        return c
    }
}
