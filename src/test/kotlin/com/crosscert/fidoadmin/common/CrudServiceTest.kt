package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class CrudServiceTest {

    interface LicenseRepo : AdminRepository<CcfaLicense, Long>, PartialFetch<CcfaLicense>

    val repo: LicenseRepo = mock()
    val audit: AuditLogger = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)

    /** COMPANY_IDX 가 있는 테이블의 대표 구현. */
    val service: CrudService<CcfaLicense, Long, SearchForm> = object : CrudService<CcfaLicense, Long, SearchForm>(repo, audit, tenant) {
        override fun toSpecification(form: SearchForm): Specification<CcfaLicense>? = null
        override fun companyIdxAttribute(): String? = "companyIdx"
        override fun companyIdxOf(entity: CcfaLicense): Long? = entity.companyIdx
        override fun setCompanyIdx(entity: CcfaLicense, companyIdx: Long?) { entity.companyIdx = companyIdx }
        override fun idOf(entity: CcfaLicense): String = entity.idx.toString()
        override fun tableName(): String = "CCFA_LICENSE"
        override fun touchCreated(entity: CcfaLicense, now: LocalDateTime) { entity.createdtime = now; entity.updatedtime = now }
        override fun touchUpdated(entity: CcfaLicense, now: LocalDateTime) { entity.updatedtime = now }
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    /** SUPER 로 로그인하고 고객사를 선택한다. */
    private fun loginSuperSelecting(companyIdx: Long) {
        login(0L)
        selected.select(companyIdx)
    }

    private fun license(idx: Long, companyIdx: Long): CcfaLicense {
        val l = CcfaLicense(); l.idx = idx; l.companyIdx = companyIdx; return l
    }

    @Test fun getRejectsOtherTenant() {
        login(1L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)))
        assertThatThrownBy { service.get(9L) }.isInstanceOf(TenantMismatchException::class.java)
    }

    /**
     * 이번 변경의 핵심. 예전에는 checkTenant() 가 isSuper() 면 그냥 통과해
     * SUPER 가 URL 로 임의 테넌트의 행을 열 수 있었다(superCanGetAnyTenant 로 불렸고
     * 그 통과를 정상 동작으로 단언했다). 이제는 선택한 테넌트와 다르면 거부된다.
     */
    @Test fun superGetsRejectedForOtherTenant() {
        loginSuperSelecting(9L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(license(1L, 3L)))

        assertThatThrownBy { service.get(1L) }.isInstanceOf(TenantMismatchException::class.java)
    }

    @Test fun superGetsSelectedTenantRow() {
        loginSuperSelecting(9L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(license(1L, 9L)))

        assertThat(service.get(1L).companyIdx).isEqualTo(9L)
    }

    @Test fun getThrowsWhenMissing() {
        loginSuperSelecting(9L)
        whenever(repo.findById(9L)).thenReturn(Optional.empty())
        assertThatThrownBy { service.get(9L) }.isInstanceOf(EntityNotFoundException::class.java)
    }

    /**
     * CLOB 컬럼이 있는 테이블의 목록은 그 컬럼을 빼고 읽는다(listExcludedProperties).
     * Hibernate 는 @Lob String 을 항상 즉시 읽어 목록 DTO 가 버리는 값을 매 행 전송하므로,
     * 제외 속성을 선언한 서비스는 repository.findAllExcluding 으로 간다. 테넌트 필터는 그대로 들어간다.
     */
    @Suppress("UNCHECKED_CAST")
    @Test fun searchExcludesDeclaredPropertiesButKeepsTenantFilter() {
        val clobService = object : CrudService<CcfaLicense, Long, SearchForm>(repo, audit, tenant) {
            override fun toSpecification(form: SearchForm): Specification<CcfaLicense>? = null
            override fun companyIdxAttribute(): String? = "companyIdx"
            override fun companyIdxOf(entity: CcfaLicense): Long? = entity.companyIdx
            override fun setCompanyIdx(entity: CcfaLicense, companyIdx: Long?) { entity.companyIdx = companyIdx }
            override fun idOf(entity: CcfaLicense): String = entity.idx.toString()
            override fun tableName(): String = "CCFA_LICENSE"
            override fun listExcludedProperties(): Set<String> = setOf("license")
        }
        login(1L)
        val pageable = PageRequest.of(0, 20, Sort.by("idx"))
        whenever(repo.findAllExcluding(eq(CcfaLicense::class.java), any<Specification<CcfaLicense>>(), eq(pageable), eq(setOf("license"))))
            .thenReturn(PageImpl(listOf()))

        clobService.search(SearchForm(), pageable)

        val captor = argumentCaptor<Specification<CcfaLicense>>()
        verify(repo).findAllExcluding(eq(CcfaLicense::class.java), captor.capture(), eq(pageable), eq(setOf("license")))
        verify(repo, never()).findAll(any<Specification<CcfaLicense>>(), any<PageRequest>())
        val root = Mockito.mock(Root::class.java, Mockito.RETURNS_DEEP_STUBS) as Root<CcfaLicense>
        val cb = Mockito.mock(CriteriaBuilder::class.java, Mockito.RETURNS_DEEP_STUBS)
        captor.lastValue.toPredicate(root, Mockito.mock(CriteriaQuery::class.java), cb)
        verify(root).get<Any>("companyIdx")
    }

    /** 제외 속성이 없는 보통 서비스는 지금처럼 findAll 로 간다. */
    @Test fun searchWithoutExclusionsUsesPlainFindAll() {
        login(1L)
        val pageable = PageRequest.of(0, 20, Sort.by("idx"))
        whenever(repo.findAll(any<Specification<CcfaLicense>>(), eq<Pageable>(pageable))).thenReturn(PageImpl(listOf()))

        service.search(SearchForm(), pageable)

        verify(repo).findAll(any<Specification<CcfaLicense>>(), eq<Pageable>(pageable))
        verify(repo, never()).findAllExcluding(any(), any(), any(), any())
    }

    /** 건수 캐시를 켠 서비스. 대용량 로그 목록이 이렇게 선언한다. */
    val cachedCountService: CrudService<CcfaLicense, Long, SearchForm> = object : CrudService<CcfaLicense, Long, SearchForm>(repo, audit, tenant) {
        override fun toSpecification(form: SearchForm): Specification<CcfaLicense>? = null
        override fun companyIdxAttribute(): String? = "companyIdx"
        override fun companyIdxOf(entity: CcfaLicense): Long? = entity.companyIdx
        override fun setCompanyIdx(entity: CcfaLicense, companyIdx: Long?) { entity.companyIdx = companyIdx }
        override fun idOf(entity: CcfaLicense): String = entity.idx.toString()
        override fun tableName(): String = "CCFA_LICENSE"
        override fun countCacheTtl(): Duration = Duration.ofSeconds(30)
    }

    /**
     * 같은 조건으로 페이지만 넘기면 COUNT(*) 를 다시 보내지 않는다. 본문은 매번 DB 에서 읽는다.
     * 첫 페이지가 꽉 차야 총 건수가 필요하므로 페이지 크기만큼 돌려준다.
     */
    @Test fun countIsCachedAcrossPagesForSameConditions() {
        login(1L)
        whenever(repo.findContentExcluding(eq(CcfaLicense::class.java), any<Specification<CcfaLicense>>(), any(), eq(setOf())))
            .thenReturn(listOf(license(1, 1), license(2, 1)))
        whenever(repo.count(any<Specification<CcfaLicense>>())).thenReturn(10L)

        val first = cachedCountService.search(SearchForm(), PageRequest.of(0, 2))
        val second = cachedCountService.search(SearchForm(), PageRequest.of(1, 2))

        assertThat(first.totalElements).isEqualTo(10L)
        assertThat(second.totalElements).isEqualTo(10L)
        verify(repo, times(1)).count(any<Specification<CcfaLicense>>())
        verify(repo, times(2)).findContentExcluding(any(), any(), any(), any())
        verify(repo, never()).findAll(any<Specification<CcfaLicense>>(), any<PageRequest>())
    }

    /** 조건이나 테넌트가 다르면 건수를 따로 센다. 캐시가 테넌트 경계를 넘지 않는다. */
    @Test fun countCacheIsKeyedByConditionsAndTenant() {
        whenever(repo.findContentExcluding(any(), any<Specification<CcfaLicense>>(), any(), any()))
            .thenReturn(listOf(license(1, 1), license(2, 1)))
        whenever(repo.count(any<Specification<CcfaLicense>>())).thenReturn(10L, 20L, 30L)

        login(1L)
        cachedCountService.search(SearchForm(), PageRequest.of(0, 2))
        val other = SearchForm()
        other.fromDate = LocalDate.of(2026, 10, 1)
        cachedCountService.search(other, PageRequest.of(0, 2))
        SecurityContextHolder.clearContext()
        login(2L)
        val tenant2 = cachedCountService.search(SearchForm(), PageRequest.of(0, 2))

        assertThat(tenant2.totalElements).isEqualTo(30L)
        verify(repo, times(3)).count(any<Specification<CcfaLicense>>())
    }

    /** 미선택 SUPER 는 조회 자체가 성립하지 않는다. */
    @Test fun searchThrowsWhenSuperHasNoTenantSelected() {
        login(0L)
        assertThatThrownBy { service.search(SearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(NoTenantSelectedException::class.java)
    }

    @Test fun createForcesTenantAndTimestampsAndAudits() {
        login(1L)
        whenever(repo.save(any<CcfaLicense>())).thenAnswer { inv -> val l = inv.getArgument<CcfaLicense>(0); l.idx = 100L; l }
        val input = license(0L, 999L)

        val out = service.create(input)

        assertThat(out.companyIdx).isEqualTo(1L)
        assertThat(out.createdtime).isNotNull()
        verify(audit).log(AuditType.CREATE, "CCFA_LICENSE CREATE 100")
    }

    /** 등록은 역할과 무관하게 유효 테넌트 소유가 된다. 폼/엔티티에 실린 값은 무시된다. */
    @Test fun superCreatedRowIsOwnedBySelectedTenant() {
        loginSuperSelecting(9L)
        whenever(repo.save(any<CcfaLicense>())).thenAnswer { inv -> inv.getArgument<CcfaLicense>(0) }
        assertThat(service.create(license(0L, 5L)).companyIdx).isEqualTo(9L)
    }

    @Test fun updateMutatesWithinTenant() {
        login(1L)
        val existing = license(9L, 1L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(existing))
        whenever(repo.save(any<CcfaLicense>())).thenAnswer { inv -> inv.getArgument<CcfaLicense>(0) }

        service.update(9L) { l -> l.serviceName = "kbstar" }

        assertThat(existing.serviceName).isEqualTo("kbstar")
        assertThat(existing.updatedtime).isNotNull()
        verify(audit).log(eq(AuditType.UPDATE), startsWith("CCFA_LICENSE UPDATE 9"))
    }

    @Test fun deleteChecksTenantAndAudits() {
        login(1L)
        whenever(repo.findById(9L)).thenReturn(Optional.of(license(9L, 1L)))
        service.delete(9L)
        verify(repo).delete(any<CcfaLicense>())
        verify(audit).log(AuditType.DELETE, "CCFA_LICENSE DELETE 9")
    }

    @Test fun searchAddsTenantFilterForCompanyRole() {
        login(1L)
        val page: Page<CcfaLicense> = PageImpl(listOf())
        whenever(repo.findAll(any<Specification<CcfaLicense>>(), any<PageRequest>())).thenReturn(page)

        service.search(SearchForm(), PageRequest.of(0, 20, Sort.by("idx")))

        val captor = argumentCaptor<Specification<CcfaLicense>>()
        verify(repo).findAll(captor.capture(), eq<Pageable>(PageRequest.of(0, 20, Sort.by("idx"))))
        assertThat(captor.lastValue).isNotNull()
        // Specification 을 실제로 실행해 companyIdx = 1 조건이 들어갔는지 확인한다.
        assertThat(companyIdxEqualsIn(captor.lastValue)).isEqualTo(1L)
    }

    /**
     * 목록 조회의 테넌트 필터는 격리의 핵심이다. Specification 이 null 이 아닌지만 보면
     * 필터를 제거해도 테스트가 통과하므로, 실제로 평가해 companyIdx 등치 조건을 확인한다.
     *
     * 예전에는 SUPER 면 필터가 없어 전체가 조회됐다(이 단언이 isNull() 이었다).
     * 이제는 선택한 고객사로 걸린다.
     */
    @Test fun searchFiltersSuperBySelectedCompany() {
        loginSuperSelecting(9L)
        whenever(repo.findAll(any<Specification<CcfaLicense>>(), any<PageRequest>()))
            .thenReturn(PageImpl(listOf()))

        service.search(SearchForm(), PageRequest.of(0, 20, Sort.by("idx")))

        val captor = argumentCaptor<Specification<CcfaLicense>>()
        verify(repo).findAll(captor.capture(), any<PageRequest>())
        assertThat(companyIdxEqualsIn(captor.lastValue)).isEqualTo(9L)
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용이어야 한다. */
    @Test fun globalTableIsSuperOnly() {
        val global = object : CrudService<CcfaLicense, Long, SearchForm>(repo, audit, tenant) {
            override fun toSpecification(form: SearchForm): Specification<CcfaLicense>? = null
            override fun companyIdxAttribute(): String? = null
            override fun companyIdxOf(entity: CcfaLicense): Long? = null
            override fun setCompanyIdx(entity: CcfaLicense, companyIdx: Long?) {}
            override fun idOf(entity: CcfaLicense): String = "1"
            override fun tableName(): String = "CCFA_MENU"
        }

        login(1L)
        assertThatThrownBy { global.search(SearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy { global.get(1L) }
            .isInstanceOf(AccessDeniedException::class.java)

        login(0L)
        whenever(repo.findAll(any<Specification<CcfaLicense>>(), any<PageRequest>()))
            .thenReturn(PageImpl(listOf()))
        global.search(SearchForm(), PageRequest.of(0, 20, Sort.by("idx")))
    }

    private companion object {
        /**
         * Specification 을 가짜 Criteria API 위에서 평가해 companyIdx 에 걸린 등치 값을 뽑아낸다.
         * 조건이 없으면 null 을 돌려준다.
         */
        /**
         * Specification 을 실제 Criteria API 위에서 평가해 companyIdx 등치 값을 뽑아낸다.
         * 조건이 없으면 null.
         */
        fun companyIdxEqualsIn(spec: Specification<CcfaLicense>?): Long? =
            CriteriaProbe.equalsValueFor(spec, "companyIdx")
    }
}
