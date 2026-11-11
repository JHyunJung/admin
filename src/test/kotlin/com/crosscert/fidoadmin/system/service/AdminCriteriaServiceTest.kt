package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime
import java.util.Optional

class AdminCriteriaServiceTest {

    val repo: CcfaCriteriaRepository = mock()
    val audit: AuditLogger = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = AdminCriteriaService(repo, audit, tenant)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun criteria(idx: Long): CcfaCriteria { val c = CcfaCriteria(); c.idx = idx; c.aaid = "0012#0001"; return c }

    @Test fun createSetsBothTimestampsAndAudits() {
        login(0L)
        whenever(repo.save(any<CcfaCriteria>())).thenAnswer { inv -> val c = inv.getArgument<CcfaCriteria>(0); c.idx = 7L; c }
        val out = service.create(criteria(0L))
        assertThat(out.createtime).isNotNull()
        assertThat(out.updatedtime).isEqualTo(out.createtime)
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_CRITERIA CREATE 7")
    }

    @Test fun updateTouchesOnlyUpdatedtime() {
        login(0L)
        val existing = criteria(7L)
        existing.createtime = LocalDateTime.of(2026, 1, 1, 0, 0)
        whenever(repo.findById(7L)).thenReturn(Optional.of(existing))
        whenever(repo.save(any<CcfaCriteria>())).thenAnswer { it.getArgument<CcfaCriteria>(0) }
        service.update(7L) { c -> c.metahash = "h2" }
        assertThat(existing.createtime).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertThat(existing.updatedtime).isAfter(existing.createtime)
        verify(audit).logGlobal(eq(AuditType.UPDATE), startsWith("CCFA_CRITERIA UPDATE 7"))
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용. */
    @Test fun companyRoleIsDenied() {
        login(1L)
        assertThatThrownBy { service.search(AdminCriteriaSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { service.get(7L) }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test fun superSearchesWithoutTenantFilter() {
        login(0L)
        whenever(repo.findAllExcluding(any<Class<CcfaCriteria>>(), any<Specification<CcfaCriteria>>(), any<PageRequest>(), any<Set<String>>()))
            .thenReturn(PageImpl(listOf()))
        val f = AdminCriteriaSearchForm()
        f.aaid = "0012"
        service.search(f, PageRequest.of(0, 20, service.defaultSort()))
        verify(repo).findAllExcluding(any<Class<CcfaCriteria>>(), any<Specification<CcfaCriteria>>(), any<PageRequest>(), eq(setOf("jsondata")))
        assertThat(service.sortableProperties()).contains("idx", "aaid", "updatedtime")
    }
}
