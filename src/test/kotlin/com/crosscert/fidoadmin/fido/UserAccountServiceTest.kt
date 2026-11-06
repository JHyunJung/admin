package com.crosscert.fidoadmin.fido

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import com.crosscert.fidoadmin.fido.service.UserAccountRow
import com.crosscert.fidoadmin.fido.service.UserAccountService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class UserAccountServiceTest {

    val repo: UserinfoRepository = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val service = UserAccountService(repo, tenant)

    @BeforeEach fun loginSuperSelecting() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(9L)
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun row(userid: String, devices: Long, active: Long): UserAccountRow =
        UserAccountRow(userid, "kbstar", devices, active, LocalDateTime.now())

    // Java 원본의 any() 는 null 도 받는다. 인자가 null 일 수 있는 자리는 anyOrNull() 로 옮긴다.

    /**
     * 이 서비스는 CrudService 를 거치지 않으므로 테넌트 경계가 여기 말고는 없다.
     * 유효 테넌트가 질의로 전달되지 않으면 다른 고객사의 사용자가 보인다.
     */
    @Test fun 목록은_유효_테넌트로만_조회한다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(listOf())
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(0L)

        service.search(null, null, PageRequest.of(0, 20))

        verify(repo).findUserAccounts(eq(9L), isNull(), isNull(), any<Pageable>())
        verify(repo).countUserAccounts(eq(9L), isNull(), isNull())
    }

    /** 기기 목록도 같은 경계를 지킨다. */
    @Test fun 기기_목록도_유효_테넌트로만_조회한다() {
        whenever(repo.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(listOf())

        service.credentialsOf("user001", "kbstar")

        verify(repo).findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(9L, "user001", "kbstar")
    }

    /** 테넌트를 바꾸면 그 값으로 조회한다. 선택이 질의에 실제로 반영되는지 본다. */
    @Test fun 테넌트를_바꾸면_바뀐_값으로_조회한다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(listOf())
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(0L)

        selected.select(2L)
        service.search(null, null, PageRequest.of(0, 20))

        verify(repo).findUserAccounts(eq(2L), anyOrNull(), anyOrNull(), any<Pageable>())
    }

    /** 빈 검색어는 조건을 걸지 않는다. 빈 문자열을 그대로 넘기면 LIKE '%%' 가 되어 의도가 흐려진다. */
    @Test fun 빈_검색어는_null_로_넘어간다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(listOf())
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(0L)

        service.search("  ", "", PageRequest.of(0, 20))

        verify(repo).findUserAccounts(eq(9L), isNull(), isNull(), any<Pageable>())
    }

    /** 검색어가 있으면 그대로 넘긴다. */
    @Test fun 검색어는_그대로_전달된다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(listOf())
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(0L)

        service.search("user0", "kb", PageRequest.of(0, 20))

        verify(repo).findUserAccounts(eq(9L), eq("user0"), eq("kb"), any<Pageable>())
    }

    /** 총건수는 그룹 개수여야 한다 — 행 개수를 쓰면 쪽수가 부풀어 빈 페이지가 생긴다. */
    @Test fun 총건수는_그룹_개수를_쓴다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>()))
            .thenReturn(listOf(row("user001", 3, 2), row("user002", 1, 1)))
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(2L)

        val page = service.search(null, null, PageRequest.of(0, 20))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).hasSize(2)
    }

    /** 집계값이 화면까지 그대로 온다. */
    @Test fun 기기수와_정상수를_그대로_돌려준다() {
        whenever(repo.findUserAccounts(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(listOf(row("user001", 3, 2)))
        whenever(repo.countUserAccounts(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(1L)

        val first = service.search(null, null, PageRequest.of(0, 20)).content[0]

        assertThat(first.userid).isEqualTo("user001")
        assertThat(first.deviceCount).isEqualTo(3)
        assertThat(first.activeCount).isEqualTo(2)
    }
}
