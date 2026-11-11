package com.crosscert.fidoadmin.system.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.entity.CcfaOptions
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.function.Function

class OptionServiceTest {

    val groups: CcfaOptionRepository = mock()
    val items: CcfaOptionsRepository = mock()
    val audit: AuditLogger = mock()
    val tenant = TenantContext(SelectedTenant())
    val service = OptionService(groups, audit, items, tenant)

    @BeforeEach fun loginSuper() { login(0L) }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun group(idx: Long): CcfaOption { val g = CcfaOption(); g.idx = idx; g.optionName = "STATUS"; return g }
    private fun item(idx: Long, optionIdx: Long): CcfaOptions { val i = CcfaOptions(); i.idx = idx; i.optionIdx = optionIdx; i.optionValue = "use"; return i }

    @Test fun addItemAssignsGroupAndAudits() {
        whenever(groups.findById(1L)).thenReturn(Optional.of(group(1L)))
        whenever(items.save(any<CcfaOptions>())).thenAnswer { inv -> val i = inv.getArgument<CcfaOptions>(0); i.idx = 5L; i }
        val `in` = CcfaOptions(); `in`.optionValue = "unuse"; `in`.optionIdx = 999L // 폼이 무엇을 보내든 경로의 그룹이 이긴다
        val out = service.addItem(1L, `in`)
        assertThat(out.optionIdx).isEqualTo(1L)
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_OPTIONS CREATE 5")
    }

    @Test fun addItemToMissingGroupFails() {
        whenever(groups.findById(9L)).thenReturn(Optional.empty())
        assertThatThrownBy { service.addItem(9L, CcfaOptions()) }.isInstanceOf(EntityNotFoundException::class.java)
        verify(items, never()).save(anyOrNull<CcfaOptions>())
    }

    @Test fun removeItemDeletesAndAudits() {
        whenever(groups.findById(1L)).thenReturn(Optional.of(group(1L)))
        whenever(items.findById(5L)).thenReturn(Optional.of(item(5L, 1L)))
        service.removeItem(1L, 5L)
        verify(items).delete(any<CcfaOptions>())
        verify(audit).logGlobal(AuditType.DELETE, "CCFA_OPTIONS DELETE 5")
    }

    /** 다른 그룹의 코드를 경로만 바꿔 지우지 못한다. */
    @Test fun removeItemOfOtherGroupIsRejected() {
        whenever(groups.findById(1L)).thenReturn(Optional.of(group(1L)))
        whenever(items.findById(5L)).thenReturn(Optional.of(item(5L, 2L)))
        assertThatThrownBy { service.removeItem(1L, 5L) }.isInstanceOf(EntityNotFoundException::class.java)
        verify(items, never()).delete(any<CcfaOptions>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    @Test fun deleteGroupBlockedWhenItemsExist() {
        whenever(groups.findById(1L)).thenReturn(Optional.of(group(1L)))
        whenever(items.countByOptionIdx(1L)).thenReturn(2L)
        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("코드 2건")
        verify(groups, never()).delete(any<CcfaOption>())
    }

    @Test fun deleteEmptyGroupSucceeds() {
        whenever(groups.findById(1L)).thenReturn(Optional.of(group(1L)))
        whenever(items.countByOptionIdx(1L)).thenReturn(0L)
        service.delete(1L)
        verify(groups).delete(any<CcfaOption>())
        verify(audit).logGlobal(AuditType.DELETE, "CCFA_OPTION DELETE 1")
    }

    @Test fun itemsAreListedInIdxOrder() {
        whenever(items.findByOptionIdxOrderByIdxAsc(1L)).thenReturn(listOf(item(1L, 1L), item(2L, 1L)))
        assertThat(service.items(1L)).extracting(Function<CcfaOptions, Long?> { it.idx }).containsExactly(1L, 2L)
    }

    @Test fun companyRoleIsDeniedForItemsToo() {
        login(1L)
        assertThatThrownBy { service.items(1L) }.isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { service.addItem(1L, CcfaOptions()) }.isInstanceOf(AccessDeniedException::class.java)
    }
}
