package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import java.util.Optional
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Sort
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * 고객사 조회는 여러 화면에서 select 옵션·이름 표시에 쓰인다.
 * 범위를 서비스 안에서 강제하지 않으면 COMPANY 화면에 다른 고객사 이름이 노출된다.
 */
class CompanyLookupTest {

    private val repository: CcfaCompanyRepository = mock()
    private val lookup = CompanyLookup(repository, TenantContext(SelectedTenant()))

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }

    private fun company(idx: Long, name: String): CcfaCompany {
        val c = CcfaCompany(); c.idx = idx; c.companyName = name; return c
    }

    @Test fun companyRoleSeesOnlyItsOwnCompany() {
        login(1L)
        whenever(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")))

        assertThat(lookup.all()).extracting(Function<CcfaCompany, Long?> { it.idx }).containsExactly(1L)
        verify(repository, never()).findAll(any<Sort>())
    }

    @Test fun superSeesAllCompanies() {
        login(0L)
        whenever(repository.findAll(any<Sort>()))
            .thenReturn(listOf(company(0L, "전역"), company(1L, "국민은행"), company(2L, "테스트")))

        assertThat(lookup.all()).hasSize(3)
    }

    @Test fun companyRoleCannotResolveAnotherCompanyName() {
        login(1L)

        assertThat(lookup.name(2L)).isEqualTo("#2")   // 이름을 알려주지 않는다
        verify(repository, never()).findById(2L)
    }

    @Test fun companyRoleCanResolveItsOwnName() {
        login(1L)
        whenever(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")))

        assertThat(lookup.name(1L)).isEqualTo("국민은행")
    }

    /**
     * 고객사 이름은 사이드바(요청마다)와 감사 로그(기록마다)가 묻는다. 거의 바뀌지 않는 값을
     * 요청마다 DB 에서 다시 읽지 않는다. 바뀌면 [CompanyLookup.evict] 가 지운다.
     */
    @Test fun nameIsReadFromDatabaseOnlyOnce() {
        login(0L)
        whenever(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")))

        assertThat(lookup.name(1L)).isEqualTo("국민은행")
        assertThat(lookup.name(1L)).isEqualTo("국민은행")

        verify(repository, times(1)).findById(1L)
    }

    /** 없는 고객사("#IDX")는 기억하지 않는다 — 나중에 만들어지면 바로 보여야 한다. */
    @Test fun missingCompanyIsNotCached() {
        login(0L)
        whenever(repository.findById(7L)).thenReturn(Optional.empty())
        assertThat(lookup.name(7L)).isEqualTo("#7")

        whenever(repository.findById(7L)).thenReturn(Optional.of(company(7L, "새 고객사")))
        assertThat(lookup.name(7L)).isEqualTo("새 고객사")
    }

    @Test fun evictMakesNextLookupHitTheDatabase() {
        login(0L)
        whenever(repository.findById(1L)).thenReturn(Optional.of(company(1L, "국민은행")))
        lookup.name(1L)

        lookup.evict(1L)
        whenever(repository.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행")))

        assertThat(lookup.name(1L)).isEqualTo("KB국민은행")
        verify(repository, times(2)).findById(1L)
    }

    /** 캐시는 이름만 기억한다. 범위 규칙(다른 고객사 이름 비노출)은 캐시 앞에서 그대로 적용된다. */
    @Test fun cacheDoesNotLeakNamesAcrossRoles() {
        login(0L)
        whenever(repository.findById(2L)).thenReturn(Optional.of(company(2L, "테스트")))
        assertThat(lookup.name(2L)).isEqualTo("테스트")

        SecurityContextHolder.clearContext()
        login(1L)
        assertThat(lookup.name(2L)).isEqualTo("#2")
    }
}
