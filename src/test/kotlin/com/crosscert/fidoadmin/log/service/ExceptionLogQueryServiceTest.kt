package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.entity.CcfaExceptions
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class ExceptionLogQueryServiceTest {

    val repo: CcfaExceptionsRepository = mock()
    val selected = SelectedTenant()
    val service = ExceptionLogQueryService(repo, mock<AuditLogger>(), TenantContext(selected))

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(1L) // search() 는 유효 테넌트를 요구한다; 테넌시는 이 테스트의 관심사가 아니다
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime", "eType", "eLevel")
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx")
    }

    /**
     * CREATEDTIME 은 VARCHAR2 라 기간(between) 이 아니라 문자열 like 로 검색한다.
     * 실수로 base fromDate 를 between 에 넣으면 문자열 컬럼 비교로 조회가 깨지므로,
     * Specification 이 createdtime 을 like 경로로만 쓰는지 확인한다.
     */
    @Test fun createdtimeIsSearchedAsStringLike() {
        whenever(repo.findAllExcluding(any(), any<Specification<CcfaExceptions>>(), any<Pageable>(), any()))
            .thenReturn(PageImpl(listOf()))
        val f = ExceptionLogSearchForm()
        f.eType = "AUTH"; f.eLevel = "ERROR"; f.message = "signature"; f.createdtime = "2026-09-15"

        service.search(f, PageRequest.of(0, 20, service.defaultSort()))

        val captor = argumentCaptor<Specification<CcfaExceptions>>()
        verify(repo).findAllExcluding(any(), captor.capture(), any<Pageable>(), any()) // CLOB(exceptionData) 을 빼고 읽는다
        val root: Root<CcfaExceptions> = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val cb: CriteriaBuilder = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        captor.firstValue.toPredicate(root, mock<CriteriaQuery<*>>(), cb)
        verify(root).get<Any>("eType")
        verify(root).get<Any>("eLevel")
        verify(root).get<Any>("exceptionMessage")
        verify(root).get<Any>("createdtime")
        verify(cb, never()).greaterThanOrEqualTo(any<Expression<LocalDateTime>>(), any<LocalDateTime>())
        verify(cb, never()).lessThan(any<Expression<LocalDateTime>>(), any<LocalDateTime>())
    }
}
