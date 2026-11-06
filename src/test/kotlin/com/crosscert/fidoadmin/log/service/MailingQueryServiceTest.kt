package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository
import com.crosscert.fidoadmin.log.web.MailingSearchForm
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class MailingQueryServiceTest {

    val repo: CcfaMailingRepository = mock()
    val selected = SelectedTenant()
    val service = MailingQueryService(repo, mock<AuditLogger>(), TenantContext(selected))

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(1L) // search() 는 유효 테넌트를 요구한다; 테넌시는 이 테스트의 관심사가 아니다
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "status", "sendtime")
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx")
    }

    /**
     * 수신자 검색은 예약어 컬럼 "TO" 에 매핑된 엔티티 속성 `to` 를 써야 한다.
     * 속성명을 잘못 적으면(예: "TO", "receiver") 조회 시 500 이 나므로 실제 경로를 확인한다.
     */
    @Test fun recipientSearchUsesToAttribute() {
        whenever(repo.findAll(any<Specification<CcfaMailing>>(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        val f = MailingSearchForm()
        f.status = "SENT"; f.to = "ops@"; f.smsStatus = "SENT"

        service.search(f, PageRequest.of(0, 20, service.defaultSort()))

        val captor = argumentCaptor<Specification<CcfaMailing>>()
        verify(repo).findAll(captor.capture(), any<Pageable>())
        val root: Root<CcfaMailing> = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val cb: CriteriaBuilder = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        captor.firstValue.toPredicate(root, mock<CriteriaQuery<*>>(), cb)
        verify(root).get<Any>("to")
        verify(root).get<Any>("status")
        verify(root).get<Any>("smsStatus")
    }
}
