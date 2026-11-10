package com.crosscert.fidoadmin.fido2.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm
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

class Fido2CredentialParamsServiceTest {

    val repo: Fido2CredentialParamsRepository = mock()
    val events: ApplicationEventPublisher = mock()
    val service = Fido2CredentialParamsService(repo, mock<AuditLogger>(), TenantContext(SelectedTenant()), events)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }

    @Test fun defaultsFilledOnCreate() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2CredentialParams>())).thenAnswer { inv -> inv.getArgument<Fido2CredentialParams>(0) }
        val p = Fido2CredentialParams()
        p.credAlg = -7L
        val saved = service.create(p)
        assertThat(saved.credType).isEqualTo("public-key")
        assertThat(saved.status).isEqualTo("T")
        assertThat(saved.createdtime).isNotNull()
    }

    @Test fun explicitValuesAreKept() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2CredentialParams>())).thenAnswer { inv -> inv.getArgument<Fido2CredentialParams>(0) }
        val p = Fido2CredentialParams()
        p.credType = "custom"; p.credAlg = -257L; p.status = "F"
        val saved = service.create(p)
        assertThat(saved.credType).isEqualTo("custom")
        assertThat(saved.status).isEqualTo("F")
    }

    @Test fun companyRoleCannotSearch() {
        login(1L)
        assertThatThrownBy { service.search(Fido2CredentialParamsSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test fun createUpdateDeletePublishReload() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2CredentialParams>())).thenAnswer { inv ->
            val a = inv.getArgument<Fido2CredentialParams>(0); if (a.idx == null) a.idx = 12L; a
        }
        val created = service.create(Fido2CredentialParams())
        verify(events).publishEvent(FidoConfigChanged("FIDO2_CREDENTIAL_PARAMS CREATE 12"))

        whenever(repo.findById(12L)).thenReturn(Optional.of(created))
        service.update(12L) { }
        verify(events).publishEvent(FidoConfigChanged("FIDO2_CREDENTIAL_PARAMS UPDATE 12"))

        service.delete(12L)
        verify(events).publishEvent(FidoConfigChanged("FIDO2_CREDENTIAL_PARAMS DELETE 12"))
    }

    /** 공백뿐인 값은 기본값으로 채우고, NBSP(U+00A0)는 Java 기준 공백이 아니므로 그대로 둔다. */
    @Test fun whitespaceOnlyGetsDefaultsButNbspIsKept() {
        login(0L)
        whenever(repo.save(anyOrNull<Fido2CredentialParams>())).thenAnswer { inv -> inv.getArgument<Fido2CredentialParams>(0) }
        val blank = Fido2CredentialParams()
        blank.credType = " "; blank.status = ""
        val savedBlank = service.create(blank)
        assertThat(savedBlank.credType).isEqualTo("public-key")
        assertThat(savedBlank.status).isEqualTo("T")

        val nbsp = Fido2CredentialParams()
        nbsp.credType = "\u00A0"; nbsp.status = "\u00A0"
        val savedNbsp = service.create(nbsp)
        assertThat(savedNbsp.credType).isEqualTo("\u00A0")
        assertThat(savedNbsp.status).isEqualTo("\u00A0")
    }
}
