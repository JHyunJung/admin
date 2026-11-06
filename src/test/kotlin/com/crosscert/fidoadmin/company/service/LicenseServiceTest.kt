package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class LicenseServiceTest {

    val repo: CcfaLicenseRepository = mock()
    val companies: CcfaCompanyRepository = mock()
    val selected = SelectedTenant()
    val service = LicenseService(repo, mock<AuditLogger>(), companies,
        TenantContext(selected))

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
        whenever(repo.save(anyOrNull<CcfaLicense>())).thenAnswer { inv -> inv.getArgument<CcfaLicense>(0) }
        selected.select(1L) // 각 테스트가 다루는 행의 소유 COMPANY_IDX 와 맞춘다
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun company(idx: Long, name: String): CcfaCompany { val c = CcfaCompany(); c.idx = idx; c.companyName = name; return c }

    /** COMPANY_NAME 은 비정규화 컬럼이다. 폼이 아니라 CCFA_COMPANY 에서 채운다. */
    @Test fun companyNameSyncedOnCreate() {
        whenever(companies.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행")))
        val l = CcfaLicense(); l.companyIdx = 1L; l.serviceName = "kbstar"

        val saved = service.create(l)

        assertThat(saved.companyName).isEqualTo("KB국민은행")
        assertThat(saved.createdtime).isNotNull()
        assertThat(saved.updatedtime).isNotNull()
    }

    /**
     * COMPANY_NAME 은 매 수정마다 CCFA_COMPANY 에서 다시 읽어 온다(생성 때만이 아니라).
     * 예전 테스트는 수정 폼으로 COMPANY_IDX 자체를 바꿔 보았지만, 유효 테넌트가 소유자를
     * 정하는 새 모델에서는 update() 가 companyIdx 를 선택 테넌트로 되돌려 그 경로가 성립하지
     * 않는다. 그래서 소속은 그대로 두고 CCFA_COMPANY 쪽 이름이 바뀐 상황으로 검증한다 —
     * 동기화 의도(폼이 아니라 CCFA_COMPANY 가 이긴다)는 그대로 유지된다.
     */
    @Test fun companyNameResyncedFromCompanyOnUpdate() {
        val existing = CcfaLicense(); existing.idx = 9L; existing.companyIdx = 1L; existing.companyName = "KB국민은행(구)"
        whenever(repo.findById(9L)).thenReturn(Optional.of(existing))
        whenever(companies.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행(신)")))

        service.update(9L) { l -> l.serviceName = "kbstar-v2" }

        assertThat(existing.companyName).isEqualTo("KB국민은행(신)")
        assertThat(existing.updatedtime).isNotNull()
    }

    /**
     * "없는 고객사" 를 검증하려면 create() 가 실제로 조회하는 키(선택 테넌트, 폼 값이 아니다)가
     * 없는 고객사여야 한다. 선택 테넌트를 77 로 바꾸고 그 키를 비워 둔다.
     */
    @Test fun unknownCompanyLeavesNameNull() {
        selected.select(77L)
        whenever(companies.findById(77L)).thenReturn(Optional.empty())
        val l = CcfaLicense(); l.companyIdx = 77L
        assertThat(service.create(l).companyName).isNull()
    }

    @Test fun sortableIncludesServiceName() {
        assertThat(service.sortableProperties()).contains("idx", "serviceName", "createdtime")
    }
}
