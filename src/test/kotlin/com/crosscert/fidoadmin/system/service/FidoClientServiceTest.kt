package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Optional

class FidoClientServiceTest {

    val repo: CcfaFidoclientRepository = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = FidoClientService(repo, audit, em, tenant)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery(anyString())).thenReturn(mock<Query>())
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun client(code: String): CcfaFidoclient {
        val c = CcfaFidoclient(); c.servercode = code; c.servername = "n"; c.serverurl = "https://x"; return c
    }

    /** 컬럼은 BYTE 의미다. 한글 서버명을 글자 수로 자르면 ORA-12899 로 등록이 실패하므로 바이트로 자른다. */
    @Test fun registerCutsKoreanNamesByBytes() {
        whenever(repo.findFirstByServernameOrderByCreatedtimeAsc(anyString())).thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaFidoclient>())).thenAnswer { it.getArgument<CcfaFidoclient>(0) }
        val name = "가".repeat(400)   // 400자 = 1200바이트
        val saved = service.register(name, "https://x/" + "나".repeat(700))
        assertThat(saved.servercode!!.toByteArray(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64)
        assertThat(saved.servername!!.toByteArray(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(1024)
        assertThat(saved.serverurl!!.toByteArray(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(2048)
        assertThat(saved.servercode).isEqualTo("가".repeat(21))
    }

    @Test fun createRejectsDuplicateCode() {
        whenever(repo.existsById("FIDO01")).thenReturn(true)
        assertThatThrownBy { service.create(client("FIDO01")) }.isInstanceOf(DataIntegrityViolationException::class.java)
        verify(em, never()).persist(anyOrNull<Any>())
    }

    @Test fun createFillsDefaultsAndAudits() {
        whenever(repo.existsById("FIDO03")).thenReturn(false)
        val saved = service.create(client("FIDO03"))
        assertThat(saved.status).isEqualTo("ON")
        assertThat(saved.createdtime).isNotNull()
        assertThat(saved.updatedtime).isNotNull()
        verify(em).persist(saved)
        verify(em).flush()
        verify(audit).log(AuditType.CREATE, "CCFA_FIDOCLIENT CREATE FIDO03")
    }

    @Test fun createKeepsGivenStatus() {
        whenever(repo.existsById("FIDO04")).thenReturn(false)
        val c = client("FIDO04"); c.status = "OFF"
        assertThat(service.create(c).status).isEqualTo("OFF")
    }

    @Test fun updateTouchesOnlyUpdatedtime() {
        val existing = client("FIDO01")
        existing.createdtime = LocalDateTime.of(2026, 1, 1, 0, 0)
        whenever(repo.findById("FIDO01")).thenReturn(Optional.of(existing))
        whenever(repo.save(any<CcfaFidoclient>())).thenAnswer { it.getArgument<CcfaFidoclient>(0) }
        val out = service.update("FIDO01") { c -> c.status = "OFF" }
        assertThat(out.createdtime).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertThat(out.updatedtime).isNotNull()
        verify(audit).log(eq(AuditType.UPDATE), startsWith("CCFA_FIDOCLIENT UPDATE FIDO01"))
    }

    @Test fun sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "servercode"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("servercode", "servername", "status", "updatedtime")
    }
}
