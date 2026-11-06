package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Challenge
import com.crosscert.fidoadmin.fido.repository.ChallengeRepository
import com.crosscert.fidoadmin.fido.web.ChallengeSearchForm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class ChallengeQueryServiceTest {

    val repo: ChallengeRepository = mock()
    val service = ChallengeQueryService(repo, mock<AuditLogger>(), TenantContext(SelectedTenant()))

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime")
    }

    @Test fun idOfIsIdx() {
        val c = Challenge(); c.idx = 42L
        assertThat(service.idOf(c)).isEqualTo("42")
    }

    /** COMPANY 로그인으로 검색하면 Specification 이 만들어져 repository 에 전달된다(테넌트 필터는 기반 테스트가 검증). */
    @Test fun searchPassesSpecificationToRepository() {
        login(1L)
        whenever(repo.findContentExcluding(any(), any<Specification<Challenge>>(), any<Pageable>(), any<Set<String>>()))
            .thenReturn(listOf())
        val f = ChallengeSearchForm()
        f.userid = "user001"
        service.search(f, PageRequest.of(0, 20, service.defaultSort()))
        // 건수 캐시를 쓰므로 본문은 findContentExcluding 으로 읽는다.
        verify(repo).findContentExcluding(any(), any<Specification<Challenge>>(), any<Pageable>(), any<Set<String>>())
    }
}
