package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import com.crosscert.fidoadmin.system.repository.CcfaSystemInfoRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class SystemInfoServiceTest {

    val repo: CcfaSystemInfoRepository = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = SystemInfoService(repo, audit, em, tenant)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery(anyString())).thenReturn(mock<Query>())
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun info(key: String): CcfaSystemInfo { val i = CcfaSystemInfo(); i.propKey = key; i.propValue = "v"; return i }

    @Test fun createRejectsDuplicateKey() {
        whenever(repo.existsById("VERSION")).thenReturn(true)
        assertThatThrownBy { service.create(info("VERSION")) }.isInstanceOf(DataIntegrityViolationException::class.java)
        verify(em, never()).persist(anyOrNull<Any>())
    }

    @Test fun createPersistsAndAudits() {
        whenever(repo.existsById("BUILD_NO")).thenReturn(false)
        val saved = service.create(info("BUILD_NO"))
        assertThat(saved.updatedtime).isNotNull()
        verify(em).persist(saved)
        verify(em).flush()
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_SYSTEM_INFO CREATE BUILD_NO")
    }

    @Test fun updateTouchesTimestamp() {
        whenever(repo.findById("VERSION")).thenReturn(Optional.of(info("VERSION")))
        whenever(repo.save(any<CcfaSystemInfo>())).thenAnswer { it.getArgument<CcfaSystemInfo>(0) }
        val out = service.update("VERSION") { i -> i.propValue = "1.1.0" }
        assertThat(out.propValue).isEqualTo("1.1.0")
        assertThat(out.updatedtime).isNotNull()
        verify(audit).logGlobal(eq(AuditType.UPDATE), startsWith("CCFA_SYSTEM_INFO UPDATE VERSION"))
    }

    @Test fun sortDefaultsToKey() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "propKey"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("propKey", "updatedtime")
    }

    /** COMPANY_IDX 가 없는 테이블: COMPANY 역할은 서비스 계층에서도 거부된다. */
    @Test fun companyRoleIsDeniedAtServiceLayer() {
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        assertThatThrownBy { service.get("VERSION") }
            .isInstanceOf(AccessDeniedException::class.java)
    }
}
