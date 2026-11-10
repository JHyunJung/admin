package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaFields
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.function.Function

class FieldServiceTest {

    val repo: CcfaFieldsRepository = mock()
    val options: CcfaOptionRepository = mock()
    val audit: AuditLogger = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = FieldService(repo, audit, options, tenant)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun field(): CcfaFields {
        val f = CcfaFields(); f.fieldTable = "APPID"; f.fieldName = "STATUS"; f.fieldType = "select"; return f
    }

    @Test fun numericFlagsDefaultToZero() {
        login(0L)
        whenever(repo.save(any<CcfaFields>())).thenAnswer { inv -> val f = inv.getArgument<CcfaFields>(0); f.idx = 3L; f }
        val out = service.create(field())
        assertThat(out.pk).isZero()
        assertThat(out.fk).isZero()
        assertThat(out.editable).isZero()
        assertThat(out.optionIdx).isNull()
        verify(audit).log(AuditType.CREATE, "CCFA_FIELDS CREATE 3")
    }

    @Test fun explicitFlagsAreKept() {
        login(0L)
        whenever(repo.save(any<CcfaFields>())).thenAnswer { it.getArgument<CcfaFields>(0) }
        val `in` = field(); `in`.pk = 1L; `in`.editable = 1L; `in`.optionIdx = 1L
        val out = service.create(`in`)
        assertThat(out.pk).isEqualTo(1L)
        assertThat(out.editable).isEqualTo(1L)
        assertThat(out.optionIdx).isEqualTo(1L)
    }

    @Test fun optionsComeFromOptionRepositoryInIdxOrder() {
        login(0L)
        val o = CcfaOption(); o.idx = 1L; o.optionName = "STATUS"
        whenever(options.findAll(Sort.by("idx"))).thenReturn(listOf(o))
        assertThat(service.options()).extracting(Function<CcfaOption, String?> { it.optionName }).containsExactly("STATUS")
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx")))
        assertThat(service.sortableProperties()).contains("idx", "fieldTable", "fieldName")
    }

    @Test fun companyRoleIsDenied() {
        login(1L)
        assertThatThrownBy { service.options() }.isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { service.create(field()) }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test fun optionExistsAcceptsEmptyWithoutTouchingRepository() {
        assertThat(service.optionExists(null)).isTrue()
        verify(options, never()).existsById(anyOrNull<Long>())
    }

    @Test fun optionExistsDelegatesToRepository() {
        whenever(options.existsById(7L)).thenReturn(true)
        assertThat(service.optionExists(7L)).isTrue()

        whenever(options.existsById(999L)).thenReturn(false)
        assertThat(service.optionExists(999L)).isFalse()
    }

    @Test fun optionLooksUpSingleRowById() {
        login(0L)
        val o = CcfaOption(); o.idx = 1L; o.optionName = "STATUS"
        whenever(options.findById(1L)).thenReturn(java.util.Optional.of(o))
        whenever(options.findById(999L)).thenReturn(java.util.Optional.empty())
        assertThat(service.option(1L)).isSameAs(o)
        assertThat(service.option(999L)).isNull()
        verify(options, never()).findAll(any<Sort>())
    }

    @Test fun optionIsDeniedForCompanyRole() {
        login(1L)
        assertThatThrownBy { service.option(1L) }.isInstanceOf(AccessDeniedException::class.java)
        verify(options, never()).findById(any())
    }
}
