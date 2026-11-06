package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class Fido2MetadataServiceTest {

    val repo: Fido2MetadataRepository = mock()
    val events: ApplicationEventPublisher = mock()
    val service = Fido2MetadataService(repo, mock<AuditLogger>(), TenantContext(SelectedTenant()), events)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용. 서비스 계층에서도 막힌다. */
    @Test fun companyRoleCannotSearch() {
        login(1L)
        assertThatThrownBy { service.search(Fido2MetadataSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test fun createFillsCreatedtime() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2Metadata>())).thenAnswer { inv -> inv.getArgument<Fido2Metadata>(0) }
        val m = Fido2Metadata()
        m.description = "YubiKey"; m.aaguid = "cb69481e-8ff7-4039-93ec-0a2729a154a8"
        val saved = service.create(m)
        assertThat(saved.createdtime).isNotNull()
    }

    @Test fun sortableIncludesListColumnsOnly() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "description", "aaguid", "protocolfamily", "createdtime")
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun createUpdateDeletePublishReload() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2Metadata>())).thenAnswer { inv ->
            val a = inv.getArgument<Fido2Metadata>(0); if (a.idx == null) a.idx = 12L; a
        }
        val created = service.create(Fido2Metadata())
        verify(events).publishEvent(FidoConfigChanged("FIDO2_METADATA CREATE 12"))

        whenever(repo.findById(12L)).thenReturn(Optional.of(created))
        service.update(12L) { }
        verify(events).publishEvent(FidoConfigChanged("FIDO2_METADATA UPDATE 12"))

        service.delete(12L)
        verify(events).publishEvent(FidoConfigChanged("FIDO2_METADATA DELETE 12"))
    }
}
