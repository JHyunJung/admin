package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class SystemPropServiceTest {

    val repo: CcfaSystemPropRepository = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val service = SystemPropService(repo, audit, em, tenant)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery(anyString())).thenReturn(mock<Query>())
        selected.select(1L) // create() 가 id.companyIdx 를 이 값으로 덮어쓴다(유효 테넌트가 소유자를 정한다)
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun prop(key: String, company: Long): CcfaSystemProp {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId(key, company)
        p.propValue = "v"
        return p
    }

    /**
     * 같은 (PROP_KEY, COMPANY_IDX) 가 있으면 MERGE 로 덮어쓰지 않고 거부한다.
     *
     * `SystemPropService.setCompanyIdx()` 는 EmbeddedId 안에 값을 쓰고
     * `assignedId()` 가 그 id 를 그대로 읽으므로, `CrudService.create()` 가
     * `setCompanyIdx()` → `insert()` 순으로 부르는 이상 `existsById(assignedId(e))`
     * 는 폼의 companyIdx 가 아니라 **유효 테넌트가 박힌 키**로 조회한다. 스텁을 선택 테넌트(1)에 맞춘다.
     */
    @Test fun createRejectsDuplicateCompositeKey() {
        whenever(repo.existsById(CcfaSystemPropId("PW_FAIL_LIMIT", 1L))).thenReturn(true)
        assertThatThrownBy { service.create(prop("PW_FAIL_LIMIT", 0L)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        verify(em, never()).persist(anyOrNull<Any>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /**
     * 이름은 중립적이었지만 원래 감사 로그 단언이 "PW_FAIL_LIMIT@0" 을 기대했다 — 즉 폼에 넣은
     * companyIdx(0) 가 결과 키에 그대로 남는지를 검증하고 있었다. 새 모델에서는 유효 테넌트가
     * companyIdx 를 정하므로, 저장된 id.companyIdx 가 선택 테넌트(1)인지로
     * 단언을 바꾼다. 기본값·타임스탬프·감사 호출 검증은 그대로 둔다.
     */
    @Test fun createPersistsWithSelectedTenantAsCompanyIdx() {
        whenever(repo.existsById(any<CcfaSystemPropId>())).thenReturn(false)
        val saved = service.create(prop("PW_FAIL_LIMIT", 0L))
        assertThat(saved.id!!.companyIdx).`as`("유효 테넌트가 companyIdx 를 정한다").isEqualTo(1L)
        assertThat(saved.shareType).isEqualTo("NO")
        assertThat(saved.updatedtime).isNotNull()
        verify(em).persist(saved)
        verify(em).flush()
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_PROP CREATE PW_FAIL_LIMIT@1")
    }

    @Test fun idOfIsPathValue() {
        assertThat(service.idOf(prop("SESSION_TIMEOUT", 0L))).isEqualTo("SESSION_TIMEOUT@0")
    }

    @Test fun defaultSortIsByKeyThenCompany() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("id.propKey", "id.companyIdx", "updatedtime")
    }
}
