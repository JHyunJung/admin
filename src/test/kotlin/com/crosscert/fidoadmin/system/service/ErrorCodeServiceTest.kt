package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository
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
import java.util.Optional

class ErrorCodeServiceTest {

    val repo: CcfaErrorTableRepository = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = ErrorCodeService(repo, audit, em, tenant)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery(anyString())).thenReturn(mock<Query>())
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun code(c: String): CcfaErrorTable { val e = CcfaErrorTable(); e.errorCode = c; e.errorMessage = "m"; return e }

    @Test fun createRejectsDuplicateCode() {
        whenever(repo.existsById("1200")).thenReturn(true)
        assertThatThrownBy { service.create(code("1200")) }.isInstanceOf(DataIntegrityViolationException::class.java)
        verify(em, never()).persist(anyOrNull<Any>())
    }

    @Test fun createPersistsAndAudits() {
        whenever(repo.existsById("1499")).thenReturn(false)
        val saved = service.create(code("1499"))
        verify(em).persist(saved)
        verify(em).flush()
        verify(audit).log(AuditType.CREATE, "CCFA_ERROR_TABLE CREATE 1499")
    }

    @Test fun deleteAudits() {
        whenever(repo.findById("1498")).thenReturn(Optional.of(code("1498")))
        service.delete("1498")
        verify(repo).delete(any<CcfaErrorTable>())
        verify(audit).log(AuditType.DELETE, "CCFA_ERROR_TABLE DELETE 1498")
    }

    @Test fun sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "errorCode"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("errorCode", "errorType")
    }
}
