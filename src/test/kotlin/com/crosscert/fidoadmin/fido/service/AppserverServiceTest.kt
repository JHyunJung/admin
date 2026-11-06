package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Appserver
import com.crosscert.fidoadmin.fido.repository.AppserverRepository
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class AppserverServiceTest {

    val repo: AppserverRepository = mock()
    val audit: AuditLogger = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val events: ApplicationEventPublisher = mock()
    val memberCodes: MemberCodeGenerator = mock()
    val service = AppserverService(repo, audit, tenant, events, memberCodes)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @Test fun typeDefaultsToUseAndTimestampsSet() {
        login(0L)
        selected.select(1L) // 미선택 SUPER 는 NoTenantSelectedException; 테넌시는 이 테스트의 관심사가 아니다
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> val a = inv.getArgument<Appserver>(0); a.idx = 7L; a }
        val input = Appserver(); input.memberCode = "KB01"; input.memberId = "kbsvr"

        val out = service.create(input)

        assertThat(out.type).isEqualTo("use")
        assertThat(out.createdtime).isNotNull()
        assertThat(out.updatedtime).isEqualTo(out.createdtime)
        verify(audit).log(AuditType.CREATE, "APPSERVER CREATE 7")
    }

    @Test fun updateTouchesOnlyUpdatedtime() {
        login(1L)
        val existing = Appserver(); existing.idx = 3L; existing.companyIdx = 1L
        val created = LocalDateTime.of(2026, 1, 1, 0, 0)
        existing.createdtime = created; existing.updatedtime = created
        whenever(repo.findById(3L)).thenReturn(Optional.of(existing))
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> inv.getArgument<Appserver>(0) }

        service.update(3L) { a -> a.note = "변경" }

        assertThat(existing.createdtime).isEqualTo(created)
        assertThat(existing.updatedtime).isAfter(created)
        verify(audit).log(eq(AuditType.UPDATE), startsWith("APPSERVER UPDATE 3"))
    }

    @Test fun companyRoleCreateForcesOwnTenant() {
        login(1L)
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> inv.getArgument<Appserver>(0) }
        val input = Appserver(); input.memberCode = "X"; input.memberId = "Y"; input.companyIdx = 999L
        assertThat(service.create(input).companyIdx).isEqualTo(1L)
    }

    @Test fun duplicateCheckIsScopedToTenantAndExcludesSelf() {
        login(1L)
        whenever(repo.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(1L, "M01", "ID01", 5L)).thenReturn(true)
        assertThat(service.existsDuplicate("M01", "ID01", 5L)).isTrue()
    }

    @Test fun duplicateCheckOnCreateUsesExistsWithoutExclusion() {
        login(1L)
        whenever(repo.existsByCompanyIdxAndMemberCodeAndMemberId(1L, "M01", "ID01")).thenReturn(true)
        assertThat(service.existsDuplicate("M01", "ID01", null)).isTrue()
    }

    @Test fun blankValuesAreNeverDuplicates() {
        assertThat(service.existsDuplicate(null, "ID01", null)).isFalse()
        assertThat(service.existsDuplicate("M01", " ", null)).isFalse()
    }

    @Test fun sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "memberCode", "memberId", "createdtime")
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun createUpdateDeletePublishReload() {
        login(1L)
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> val a = inv.getArgument<Appserver>(0); if (a.idx == null) a.idx = 12L; a }
        val created = service.create(Appserver())
        verify(events).publishEvent(FidoConfigChanged("APPSERVER CREATE 12"))

        whenever(repo.findById(12L)).thenReturn(Optional.of(created))
        service.update(12L) { a -> a.memberId = "m" }
        verify(events).publishEvent(FidoConfigChanged("APPSERVER UPDATE 12"))

        service.delete(12L)
        verify(events).publishEvent(FidoConfigChanged("APPSERVER DELETE 12"))
    }

    @Test fun createFillsGeneratedMemberCode() {
        login(1L)
        whenever(memberCodes.generate()).thenReturn("K7Q2M9XA4D")
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> inv.getArgument<Appserver>(0) }
        val input = Appserver(); input.memberId = "kbsvr"

        assertThat(service.create(input).memberCode).isEqualTo("K7Q2M9XA4D")
    }

    @Test fun createOverwritesAnyMemberCodeFromCaller() {
        login(1L)
        whenever(memberCodes.generate()).thenReturn("K7Q2M9XA4D")
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> inv.getArgument<Appserver>(0) }
        val input = Appserver(); input.memberCode = "TYPED"; input.memberId = "kbsvr"

        assertThat(service.create(input).memberCode).isEqualTo("K7Q2M9XA4D")
    }

    @Test fun updateNeverGeneratesMemberCode() {
        login(1L)
        val existing = Appserver(); existing.idx = 3L; existing.companyIdx = 1L; existing.memberCode = "OLD0000001"
        whenever(repo.findById(3L)).thenReturn(Optional.of(existing))
        whenever(repo.save(any<Appserver>())).thenAnswer { inv -> inv.getArgument<Appserver>(0) }

        service.update(3L) { a -> a.note = "변경" }

        assertThat(existing.memberCode).isEqualTo("OLD0000001")
        verify(memberCodes, never()).generate()
    }
}
