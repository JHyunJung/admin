package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Appid
import com.crosscert.fidoadmin.fido.repository.AppidRepository
import com.crosscert.fidoadmin.fido.web.AppidSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class AppidServiceTest {

    val repo: AppidRepository = mock()
    val audit: AuditLogger = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val events: ApplicationEventPublisher = mock()
    val service = AppidService(repo, audit, tenant, events)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @Test fun defaultsAndTimestampsFilledOnCreate() {
        login(0L)
        selected.select(1L) // 대상 행의 소유 COMPANY_IDX 와 맞춘다
        whenever(repo.save(any<Appid>())).thenAnswer { inv -> val a = inv.getArgument<Appid>(0); a.idx = 10L; a }
        val input = Appid(); input.appid = "https://kbstar.com/facets.json"; input.companyIdx = 1L

        val out = service.create(input)

        assertThat(out.status).isEqualTo("use")
        assertThat(out.deviceDefault).isEqualTo("F")
        assertThat(out.createdtime).isNotNull()
        assertThat(out.updatedtime).isNotNull()
        verify(audit).log(AuditType.CREATE, "APPID CREATE 10")
    }

    @Test fun explicitValuesAreKept() {
        login(0L)
        selected.select(1L) // 미선택 SUPER 는 NoTenantSelectedException; 테넌시는 이 테스트의 관심사가 아니다
        whenever(repo.save(any<Appid>())).thenAnswer { inv -> inv.getArgument<Appid>(0) }
        val input = Appid(); input.appid = "x"; input.status = "unuse"; input.deviceDefault = "T"
        val out = service.create(input)
        assertThat(out.status).isEqualTo("unuse")
        assertThat(out.deviceDefault).isEqualTo("T")
    }

    /** companyIdxAttribute 배선 확인: COMPANY 역할 등록은 폼의 고객사 값을 무시하고 자기 고객사로 강제된다. */
    @Test fun companyRoleCreateForcesOwnTenant() {
        login(1L)
        whenever(repo.save(any<Appid>())).thenAnswer { inv -> inv.getArgument<Appid>(0) }
        val input = Appid(); input.appid = "x"; input.companyIdx = 999L
        assertThat(service.create(input).companyIdx).isEqualTo(1L)
    }

    @Test fun searchGoesThroughSpecificationWithGivenPageable() {
        login(1L)
        whenever(repo.findAll(any<Specification<Appid>>(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        val f = AppidSearchForm(); f.appid = "kb"; f.status = "use"
        val p: Pageable = PageRequest.of(0, 20, Sort.by("idx"))

        service.search(f, p)

        verify(repo).findAll(any<Specification<Appid>>(), eq(p))
    }

    @Test fun sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "appid", "servicename", "createdtime")
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun createUpdateDeletePublishReload() {
        login(1L)
        whenever(repo.save(any<Appid>())).thenAnswer { inv -> val a = inv.getArgument<Appid>(0); if (a.idx == null) a.idx = 12L; a }
        val created = service.create(Appid())
        verify(events).publishEvent(FidoConfigChanged("APPID CREATE 12"))

        whenever(repo.findById(12L)).thenReturn(Optional.of(created))
        service.update(12L) { a -> a.memo = "m" }
        verify(events).publishEvent(FidoConfigChanged("APPID UPDATE 12"))

        service.delete(12L)
        verify(events).publishEvent(FidoConfigChanged("APPID DELETE 12"))
    }
}
