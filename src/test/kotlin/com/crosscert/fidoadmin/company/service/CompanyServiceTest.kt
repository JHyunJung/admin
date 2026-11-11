package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.fido.repository.AppidRepository
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class CompanyServiceTest {

    val companies: CcfaCompanyRepository = mock()
    val appids: AppidRepository = mock()
    val users: UserinfoRepository = mock()
    val managers: CcfaManagerRepository = mock()
    val props: CcfaSystemPropRepository = mock()
    val audit: AuditLogger = mock()
    val criteria: CriteriaQueryService = mock()
    val events: ApplicationEventPublisher = mock()
    val lookup: CompanyLookup = mock()
    val service = CompanyService(companies, audit, appids, users, managers, props,
        TenantContext(SelectedTenant()), criteria, events, lookup)

    /** 고객사 이름은 CompanyLookup 이 캐시한다. 이 서비스가 행을 바꾸면 그 캐시를 지워야 이름 변경이 바로 보인다. */
    @Test fun changesEvictTheCachedCompanyName() {
        service.afterChange("UPDATE", company(3L))
        verify(lookup).evict(3L)
    }

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun company(idx: Long): CcfaCompany { val c = CcfaCompany(); c.idx = idx; return c }

    @Test fun deleteBlockedWhenDependentsExist() {
        whenever(companies.findById(1L)).thenReturn(Optional.of(company(1L)))
        whenever(appids.countByCompanyIdx(1L)).thenReturn(2L)
        whenever(users.countByCompanyIdx(1L)).thenReturn(0L)
        whenever(managers.countByCompanyIdx(1L)).thenReturn(0L)
        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("앱 ID 2건")
    }

    @Test fun deleteGlobalCompanyIsAlwaysBlocked() {
        whenever(companies.findById(0L)).thenReturn(Optional.of(company(0L)))
        assertThatThrownBy { service.delete(0L) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test fun defaultsFilledOnCreate() {
        whenever(companies.save(anyOrNull<CcfaCompany>())).thenAnswer { inv -> inv.getArgument<CcfaCompany>(0) }
        val c = service.create(CcfaCompany())
        assertThat(c.enableType).isEqualTo("Y")
        assertThat(c.maxAppid).isZero()
        assertThat(c.maxAppserver).isZero()
        assertThat(c.maxUser).isZero()
        assertThat(c.starttime).isNotNull()
        assertThat(c.endtime).isEqualTo(java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59))
        assertThat(c.creator).isEqualTo(1L)
    }

    private fun prop(key: String, companyIdx: Long, value: String): CcfaSystemProp {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId(key, companyIdx)
        p.propValue = value
        p.shareType = "NO"
        return p
    }

    /** 회사 0 의 고객사별 설정이 새 고객사의 기본값이 된다. 있는 키는 덮지 않는다. */
    @Test fun createCopiesPerCompanyPropsFromGlobalWithoutOverwriting() {
        whenever(companies.save(anyOrNull<CcfaCompany>())).thenAnswer { inv -> val c = inv.getArgument<CcfaCompany>(0); c.idx = 7L; c }
        whenever(props.findAll(any<Specification<CcfaSystemProp>>()))
            .thenReturn(listOf(prop("SESSION_TIMEOUT", 0L, "30"), prop("SERVICE_NAME", 0L, "default")))
        whenever(props.existsById(CcfaSystemPropId("SESSION_TIMEOUT", 7L))).thenReturn(true)
        whenever(props.existsById(CcfaSystemPropId("SERVICE_NAME", 7L))).thenReturn(false)

        service.create(CcfaCompany())

        val saved = argumentCaptor<CcfaSystemProp>()
        verify(props).save(saved.capture())
        assertThat(saved.lastValue.id).isEqualTo(CcfaSystemPropId("SERVICE_NAME", 7L))
        assertThat(saved.lastValue.propValue).isEqualTo("default")
        assertThat(saved.lastValue.shareType).isEqualTo("NO")
    }

    /** 삭제 검사에서 막히면 설정 행도 건드리지 않는다 — super.delete 가 먼저 던진다. */
    @Test fun blockedDeleteLeavesPropsAlone() {
        whenever(companies.findById(1L)).thenReturn(Optional.of(company(1L)))
        whenever(appids.countByCompanyIdx(1L)).thenReturn(2L)

        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)

        verify(props, never()).deleteAll(anyOrNull<Iterable<CcfaSystemProp>>())
    }

    @Test fun deleteRemovesThatCompanysPerCompanyProps() {
        whenever(companies.findById(3L)).thenReturn(Optional.of(company(3L)))
        val rows = listOf(prop("SERVICE_NAME", 3L, "kb"))
        whenever(props.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(rows)

        service.delete(3L)

        verify(companies).delete(any<CcfaCompany>())
        verify(props).deleteAll(rows)
    }

    @Test fun blankVendorCodeIsNotLookedUp() {
        assertThat(service.findByVendorCode("  ")).isEmpty()
        assertThat(service.findByVendorCode(null)).isEmpty()
        verify(companies, never()).findFirstByVendorCode(anyOrNull())
    }

    @Test fun createDisablesAllAaids() {
        whenever(companies.save(anyOrNull<CcfaCompany>())).thenAnswer { inv -> val c = inv.getArgument<CcfaCompany>(0); c.idx = 7L; c }
        whenever(props.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())
        whenever(criteria.disableAllFor(7L)).thenReturn(3)

        service.create(CcfaCompany())

        verify(criteria).disableAllFor(7L)
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (3건)")
        verify(audit, never()).log(eq(AuditType.CREATE), startsWith("CCFA_FDS_POLICY"))
        verify(events).publishEvent(FidoConfigChanged("고객사 생성 7"))
    }

    @Test fun createWithNoCriteriaSkipsAaidAudit() {
        whenever(companies.save(anyOrNull<CcfaCompany>())).thenAnswer { inv -> val c = inv.getArgument<CcfaCompany>(0); c.idx = 7L; c }
        whenever(props.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())
        whenever(criteria.disableAllFor(7L)).thenReturn(0)
        service.create(CcfaCompany())
        verify(audit, never()).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (0건)")
    }

    @Test fun deleteRemovesAaidRows() {
        whenever(companies.findById(7L)).thenReturn(Optional.of(company(7L)))
        whenever(appids.countByCompanyIdx(7L)).thenReturn(0L)
        whenever(users.countByCompanyIdx(7L)).thenReturn(0L)
        whenever(managers.countByCompanyIdx(7L)).thenReturn(0L)
        whenever(props.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())
        whenever(criteria.deleteAllFor(7L)).thenReturn(5)

        service.delete(7L)

        verify(criteria).deleteAllFor(7L)
        verify(audit).logGlobal(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 7 (5건)")
        verify(audit, never()).log(eq(AuditType.DELETE), startsWith("CCFA_FDS_POLICY"))
    }

    @Test fun blockedDeleteLeavesAaidAlone() {
        whenever(companies.findById(1L)).thenReturn(Optional.of(company(1L)))
        whenever(appids.countByCompanyIdx(1L)).thenReturn(2L)
        whenever(users.countByCompanyIdx(1L)).thenReturn(0L)
        whenever(managers.countByCompanyIdx(1L)).thenReturn(0L)
        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)
        verify(criteria, never()).deleteAllFor(anyOrNull())
    }
}
