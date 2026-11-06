package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.common.TenantMismatchException
import com.crosscert.fidoadmin.fido.entity.Userinfo
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import java.util.Optional
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class UserinfoServiceTest {

    val repo: UserinfoRepository = mock()
    val audit: AuditLogger = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val service = UserinfoService(repo, audit, tenant)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun user(idx: Long, companyIdx: Long, status: String): Userinfo {
        val u = Userinfo(); u.idx = idx; u.companyIdx = companyIdx; u.userid = "user001"
        u.status = status; u.pubkey = "PUBKEY"; u.certificate = "CERT"
        return u
    }

    @Test fun changeStatusSavesAndAuditsOldToNew() {
        login(1L)
        val existing = user(5L, 1L, "O")
        whenever(repo.findById(5L)).thenReturn(Optional.of(existing))
        whenever(repo.save(any<Userinfo>())).thenAnswer { inv -> inv.getArgument<Userinfo>(0) }

        val out = service.changeStatus(5L, "X")

        assertThat(out.status).isEqualTo("X")
        verify(repo).save(existing)
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 5 O->X")
    }

    @Test fun changeStatusRejectsUnknownValueBeforeTouchingRepository() {
        login(1L)
        assertThatThrownBy { service.changeStatus(5L, "Z") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Z")
        verify(repo, never()).findById(anyOrNull())
        verify(repo, never()).save(anyOrNull<Userinfo>())
        verify(audit, never()).log(anyOrNull(), anyOrNull())
    }

    @Test fun changeStatusRejectsNull() {
        login(1L)
        assertThatThrownBy { service.changeStatus(5L, null) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    /** 상태 변경도 테넌트 검사를 거친다. 다른 고객사의 사용자는 404(TenantMismatchException). */
    @Test fun companyRoleCannotChangeOtherTenantStatus() {
        login(1L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "O")))
        assertThatThrownBy { service.changeStatus(9L, "X") }.isInstanceOf(TenantMismatchException::class.java)
        verify(repo, never()).save(anyOrNull<Userinfo>())
        verify(audit, never()).log(anyOrNull(), anyOrNull())
    }

    /**
     * 막아야 하는 구멍: SUPER 가 미선택 상태로건, 선택한 것과 다른 테넌트의 행이건
     * URL 로 상태를 바꿀 수 있어서는 안 된다. 소유(2)와 다른 테넌트(9)를 선택하면
     * TenantMismatchException 이어야 한다(404 로 처리됨, CrudService.checkTenant 참고).
     */
    @Test fun superCannotChangeOtherTenantStatus() {
        login(0L)
        selected.select(9L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "X")))
        assertThatThrownBy { service.changeStatus(9L, "O") }.isInstanceOf(TenantMismatchException::class.java)
        verify(repo, never()).save(anyOrNull<Userinfo>())
        verify(audit, never()).log(anyOrNull(), anyOrNull())
    }

    /** SUPER 가 자신이 선택한 테넌트의 행이라면 상태를 바꿀 수 있다(감사 로그 포함). */
    @Test fun superChangesSelectedTenantStatus() {
        login(0L)
        selected.select(2L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "X")))
        whenever(repo.save(any<Userinfo>())).thenAnswer { inv -> inv.getArgument<Userinfo>(0) }
        assertThat(service.changeStatus(9L, "O").status).isEqualTo("O")
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 9 X->O")
    }

    @Test fun sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "userid", "servicename", "regtime")
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }
}
