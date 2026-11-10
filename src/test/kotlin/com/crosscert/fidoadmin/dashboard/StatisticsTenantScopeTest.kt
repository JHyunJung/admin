package com.crosscert.fidoadmin.dashboard

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.NoTenantSelectedException
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * 대시보드 집계의 테넌트 격리. COMPANY 역할은 쿼리스트링으로 다른 고객사를 지정해도
 * 자기 COMPANY_IDX 로 강제돼야 한다. 이 경로는 화면에서 가장 민감한 집계다.
 */
class StatisticsTenantScopeTest {

    private val jdbc: NamedParameterJdbcTemplate = mock()
    private val selected = SelectedTenant()
    private val service = StatisticsQueryService(jdbc, TenantContext(selected))

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }

    private fun form(): DashboardSearchForm {
        val f = DashboardSearchForm()
        f.fromDate = LocalDate.now().minusDays(7)
        f.toDate = LocalDate.now()
        return f
    }

    private fun capturedCompanyIdx(): Long? {
        val params = argumentCaptor<Map<String, Any?>>()
        verify(jdbc).query(anyString(), params.capture(), any<RowMapper<Any>>())
        return params.lastValue["companyIdx"] as Long?
    }

    private fun stubDailyQuery() {
        whenever(jdbc.query(anyString(), any<Map<String, *>>(), any<RowMapper<Any>>()))
            .thenReturn(listOf())
    }

    @Test
    fun companyRoleCannotWidenScopeViaQueryString() {
        login(1L)
        stubDailyQuery()

        service.daily(form()) // 폼에는 고객사 칸이 없다 — 세션 테넌트만 쓴다

        assertThat(capturedCompanyIdx()).isEqualTo(1L) // 자기 것으로 강제
    }

    /** SUPER 의 필터는 더 이상 폼 파라미터가 아니라 세션에서 고른 테넌트로 걸린다. */
    @Test
    fun superCanFilterByChosenCompany() {
        login(0L)
        selected.select(2L)
        stubDailyQuery()

        service.daily(form())

        assertThat(capturedCompanyIdx()).isEqualTo(2L)
    }

    /** `?fromDate=` 처럼 빈 값이 오면 필드 기본값이 null 로 덮여 NPE(500)가 났다. */
    @Test
    fun blankDatesFallBackInsteadOfThrowing() {
        login(1L)
        stubDailyQuery()

        val f = form()
        f.fromDate = null
        f.toDate = null

        service.daily(f) // 예외가 나지 않아야 한다

        val params = argumentCaptor<Map<String, Any?>>()
        verify(jdbc).query(anyString(), params.capture(), any<RowMapper<Any>>())
        assertThat(params.lastValue["fromKey"]).isNotNull()
        assertThat(params.lastValue["toKey"]).isNotNull()
    }

    /**
     * 예전에는 미선택 SUPER 가 전체 고객사 합산을 볼 수 있었다.
     * 이제는 유효 테넌트가 없으므로 조회 자체가 예외로 막힌다.
     */
    @Test
    fun 미선택_SUPER_는_집계를_볼_수_없다() {
        login(0L)

        assertThatThrownBy { service.daily(form()) }
            .isInstanceOf(NoTenantSelectedException::class.java)
    }

    /**
     * 날짜는 로그 시간 구간인 GROUPBY('yyyy/MM/dd/HH')로 거른다. 종료일은 다음 날 0시 키 미만이다.
     * 마지막 집계일 조회도 테넌트를 걸고 GROUPBY 에서 날짜를 읽는다.
     */
    @Test
    fun 기간은_GROUPBY_키로_거르고_마지막_집계일도_테넌트를_건다() {
        login(1L)
        stubDailyQuery()
        val f = form()
        f.fromDate = LocalDate.of(2026, 10, 1)
        f.toDate = LocalDate.of(2026, 10, 31)
        service.daily(f)
        val sql = argumentCaptor<String>()
        val dailyParams = argumentCaptor<Map<String, Any?>>()
        verify(jdbc).query(sql.capture(), dailyParams.capture(), any<RowMapper<Any>>())
        assertThat(sql.lastValue).contains("GROUPBY >= :fromKey AND GROUPBY < :toKey")
        assertThat(dailyParams.lastValue).containsEntry("fromKey", "2026/10/01/00").containsEntry("toKey", "2026/11/01/00")

        whenever(jdbc.queryForObject(anyString(), any<Map<String, *>>(), any<Class<String>>())).thenReturn("2026/10/15/14")
        assertThat(service.lastStatDate(f)).contains(LocalDate.of(2026, 10, 15))
        val params = argumentCaptor<Map<String, Any?>>()
        verify(jdbc).queryForObject(anyString(), params.capture(), any<Class<String>>())
        assertThat(params.lastValue["companyIdx"]).isEqualTo(1L)
    }

    @Test
    fun 집계가_없으면_마지막_집계일이_비어_있다() {
        login(1L)
        whenever(jdbc.queryForObject(anyString(), any<Map<String, *>>(), any<Class<String>>())).thenReturn(null)
        assertThat(service.lastStatDate(form())).isEmpty()
    }

    /**
     * 서비스 이름 필터는 null·빈 문자열·Java 기준 공백뿐이면 null(전체)로 바인드하고, 그 밖의 값은 그대로 넘긴다.
     * NBSP(U+00A0)는 Java Character.isWhitespace 기준 공백이 아니므로 값이 유지된다.
     */
    @Test
    fun 서비스_이름_필터는_Java_기준_공백이면_비운다() {
        login(1L)
        stubDailyQuery()
        val inputs = listOf(null, "", "  ", "\u00A0", "kbstar")
        for (input in inputs) {
            val f = form()
            f.serviceName = input
            service.daily(f)
        }
        val params = argumentCaptor<Map<String, Any?>>()
        verify(jdbc, times(inputs.size)).query(anyString(), params.capture(), any<RowMapper<Any>>())
        assertThat(params.allValues.map { it["serviceName"] }).containsExactly(null, null, null, "\u00A0", "kbstar")
    }
}
