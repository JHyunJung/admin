package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class Fido2DemoAccessCodeServiceTest {

    val repo: Fido2DemoAccessCodeRepository = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = Fido2DemoAccessCodeService(repo, audit, em, tenant)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
        whenever(em.createNativeQuery(anyString())).thenReturn(mock<Query>())
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun code(accesscode: String): Fido2DemoAccessCode {
        val c = Fido2DemoAccessCode(); c.accesscode = accesscode; c.vendorname = "벤더"; return c
    }

    /** 할당형 PK: 기존 코드로 등록하면 덮어쓰지 않고 거부한다. */
    @Test fun duplicateAccessCodeIsRejectedWithoutPersist() {
        whenever(repo.existsById("DEMO-0001")).thenReturn(true)
        assertThatThrownBy { service.create(code("DEMO-0001")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("DEMO-0001")
        verify(em, never()).persist(anyOrNull())
        verify(repo, never()).save(anyOrNull<Fido2DemoAccessCode>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    @Test fun newAccessCodePersistsWithDefaultStatusAndAudits() {
        whenever(repo.existsById("DEMO-0003")).thenReturn(false)
        val saved = service.create(code("DEMO-0003"))
        assertThat(saved.status).isEqualTo("E")
        verify(em).persist(saved)
        verify(em).flush()
        verify(repo, never()).save(anyOrNull<Fido2DemoAccessCode>())
        verify(audit).log(AuditType.CREATE, "FIDO2_DEMO_ACCESS_CODE CREATE DEMO-0003")
    }

    @Test fun explicitStatusIsKept() {
        whenever(repo.existsById("DEMO-0004")).thenReturn(false)
        val c = code("DEMO-0004"); c.status = "D"
        assertThat(service.create(c).status).isEqualTo("D")
    }

    /** idx 가 없는 엔티티라 기본 정렬을 반드시 재정의해야 한다(기본 "idx" 면 조회 시 500). */
    @Test fun sortsByAccessCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "accesscode"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("accesscode", "vendorname", "status")
    }

    /** 공백뿐인 상태는 기본값 E 로 채우고, NBSP(U+00A0)는 Java 기준 공백이 아니므로 그대로 둔다. */
    @Test fun whitespaceOnlyStatusGetsDefaultButNbspIsKept() {
        whenever(repo.existsById(anyString())).thenReturn(false)
        val blank = code("DEMO-0005"); blank.status = "  "
        assertThat(service.create(blank).status).isEqualTo("E")
        val nbsp = code("DEMO-0006"); nbsp.status = "\u00A0"
        assertThat(service.create(nbsp).status).isEqualTo("\u00A0")
    }
}
