package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository
import com.crosscert.fidoadmin.log.web.AuditLogSearchForm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class AuditLogQueryServiceTest {

    val repo: CcfaAuditLogRepository = mock()
    val service = AuditLogQueryService(repo, mock<AuditLogger>(), TenantContext(SelectedTenant()))

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    /**
     * 건수 캐시 목록은 본문만 PartialFetch 로 읽는다(뺄 속성은 없다). 리포지터리가 PartialFetch 를 직접 상속해야
     * 이 경로가 열린다 — 상속이 빠지면 search() 가 IllegalStateException 으로 실패한다.
     */
    @Test fun searchReadsContentThroughPartialFetch() {
        val u = ManagerUserDetails(1L, "u", null, "n", 1L, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(repo.findContentExcluding(any<Class<CcfaAuditLog>>(), any<Specification<CcfaAuditLog>>(), any<Pageable>(), any<Set<String>>()))
            .thenReturn(listOf())

        service.search(AuditLogSearchForm(), PageRequest.of(0, 20, service.defaultSort()))

        verify(repo).findContentExcluding(eq(CcfaAuditLog::class.java), any<Specification<CcfaAuditLog>>(), any<Pageable>(),
            eq(setOf()))
        verify(repo, never()).findAll(any<Specification<CcfaAuditLog>>(), any<Pageable>())
    }
}
