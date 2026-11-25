package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.QueryTimeoutException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * 관제 화면 모델 조립. 읽기 부품은 모두 mock 이고 시계는 고정한다.
 * 기본 시각은 2026-11-25 14:30:15(서울)이라 최근 구간은 13:30~14:30, 7일 전 기준일은 2026-11-18 이다.
 */
class MonitoringServiceTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val weekAgo = LocalDate.of(2026, 11, 18)
    private val yesterday = LocalDate.of(2026, 11, 24)

    private val cache = mock<DayProfileCache>()
    private val exceptions = mock<ExceptionCountReader>()
    private val stats = mock<StatBoardReader>()
    private val companies = mock<CompanyLookup>()
    private val selected = SelectedTenant()
    private val props = MonitoringProperties(true, true, 20, 0.5, 10, 5.0, 100, 3, 60, 10)

    /** 날짜 → 프로파일. 값이 null 이면 조회 실패, 키가 없으면 테이블 없음(빈 프로파일)이다. */
    private val days = HashMap<LocalDate, DayProfile?>()

    @BeforeEach
    fun setUp() {
        login(0L)
        whenever(companies.all()).thenReturn(listOf(company(0, "크로스서트"), company(1, "A은행"), company(2, "B카드")))
        whenever(cache.get(any())).thenAnswer {
            val d = it.getArgument<LocalDate>(0)
            if (days.containsKey(d)) days[d] else DayProfile.empty(d)
        }
        whenever(exceptions.read(any())).thenReturn(emptyMap())
        whenever(stats.latestGroupby()).thenReturn("2026/11/25/13")
        whenever(stats.totals(any(), any())).thenReturn(emptyMap())
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext()
            .setAuthentication(UsernamePasswordAuthenticationToken(u, null, u.authorities))
    }

    private fun company(idx: Long, name: String) = CcfaCompany().also {
        it.idx = idx
        it.companyName = name
    }

    private fun clockAt(t: String): Clock =
        Clock.fixed(LocalDateTime.parse(t).atZone(FidoStatisticsJob.ZONE).toInstant(), FidoStatisticsJob.ZONE)

    private fun service(p: MonitoringProperties = props, clock: Clock = clockAt("2026-11-25T14:30:15")) =
        MonitoringService(cache, exceptions, stats, companies, TenantContext(selected), p, clock)

    /** 고객사 idx 의 hh:mm 분에 n 건. */
    private fun log(idx: Long, hm: String, n: Long) = Triple(idx, LocalTime.parse(hm), n)

    private fun day(date: LocalDate, vararg logs: Triple<Long, LocalTime, Long>): DayProfile {
        val b = DayProfile.Builder(date)
        for ((idx, t, n) in logs) b.add(idx, t.hour * 60 + t.minute, n, date.atTime(t))
        return b.build()
    }

    private fun MonitoringBoard.row(name: String): LiveRow = live.single { it.name == name }

    private fun week(authS: Long, authF: Long) = WeekTotals(authS, authF, 0, 0, 0)

    @Test
    fun COMPANY_운영자는_거부한다() {
        login(1L)

        assertThatThrownBy { service().board() }.isInstanceOf(AccessDeniedException::class.java)

        verifyNoInteractions(cache, exceptions, stats, companies)
    }

    @Test
    fun 고객사를_고르지_않은_SUPER도_열린다() {
        login(0L) // selected 는 비어 있다

        val board = service().board()

        assertThat(board.asOf).isEqualTo(LocalDateTime.of(2026, 11, 25, 14, 30))
        assertThat(board.cacheSeconds).isEqualTo(60L)
        assertThat(board.live.map { it.name }).containsExactlyInAnyOrder("A은행", "B카드")
    }

    @Test
    fun 끊긴_고객사가_맨_위에_온다() {
        days[today] = day(today, log(1, "10:00", 500), log(2, "13:40", 85))
        days[weekAgo] = day(weekAgo, log(1, "13:45", 412), log(2, "14:00", 301))

        val board = service().board()

        assertThat(board.live.map { it.status }).containsExactly(LiveStatus.DOWN, LiveStatus.DROP)
        assertThat(board.live[0].name).isEqualTo("A은행")
        assertThat(board.row("A은행").recent).isEqualTo(0L)
        assertThat(board.row("A은행").baseline).isEqualTo(412L)
        assertThat(board.row("B카드").recent).isEqualTo(85L)
        assertThat(board.row("B카드").baseline).isEqualTo(301L)

        // 이름 순이 아니라 상태 순이다: 끊긴 쪽이 B카드면 B카드가 위다
        days[today] = day(today, log(1, "13:40", 85), log(2, "10:00", 500))
        days[weekAgo] = day(weekAgo, log(1, "14:00", 301), log(2, "13:45", 412))

        assertThat(service().board().live.map { it.name }).containsExactly("B카드", "A은행")
    }

    @Test
    fun IDX_0_고객사는_줄을_만들지_않는다() {
        days[today] = day(today, log(0, "14:00", 50), log(1, "14:00", 5))

        val board = service().board()

        assertThat(board.live.map { it.companyIdx }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun 로그가_없는_고객사도_줄이_있다() {
        days[today] = day(today, log(1, "14:00", 5))
        days[weekAgo] = day(weekAgo, log(1, "14:00", 5))

        val b = service().board().row("B카드")

        assertThat(b.todayTotal).isEqualTo(0L)
        assertThat(b.lastLog).isNull()
        assertThat(b.recent).isEqualTo(0L)
        assertThat(b.baseline).isEqualTo(0L)
        assertThat(b.status).isEqualTo(LiveStatus.NONE)
    }

    @Test
    fun 칠일_전_테이블이_없으면_전일을_기준으로_쓴다() {
        days[today] = day(today, log(1, "14:00", 40))
        days[yesterday] = day(yesterday, log(1, "13:45", 30), log(1, "15:00", 99))

        val board = service().board()

        assertThat(board.baselineDate).isEqualTo(yesterday)
        assertThat(board.row("A은행").baseline).isEqualTo(30L)

        // 7일 전 조회가 실패해도 비었다고 보고 전일을 쓴다
        days[weekAgo] = null

        assertThat(service().board().baselineDate).isEqualTo(yesterday)
    }

    @Test
    fun 기준일이_전혀_없으면_판정하지_않는다() {
        days[today] = day(today, log(1, "10:00", 5)) // 최근 60분은 0 건
        days[yesterday] = null // 7일 전은 테이블 없음, 전일은 조회 실패

        val board = service().board()

        assertThat(board.baselineDate).isNull()
        assertThat(board.row("A은행").baseline).isNull()
        assertThat(board.row("A은행").recent).isEqualTo(0L)
        assertThat(board.row("A은행").status).isEqualTo(LiveStatus.OK)
    }

    @Test
    fun 자정_직후에는_전날_꼬리를_더한다() {
        val clock = clockAt("2026-11-25T00:20:40") // 최근 구간 11-24 23:20 ~ 11-25 00:20
        days[yesterday] = day(yesterday, log(1, "23:10", 100), log(1, "23:30", 5))
        days[today] = day(today, log(1, "00:10", 3), log(1, "00:20", 50))
        val dayBeforeWeekAgo = weekAgo.minusDays(1) // 기준 구간 11-17 23:20 ~ 11-18 00:20
        days[dayBeforeWeekAgo] = day(dayBeforeWeekAgo, log(1, "23:19", 100), log(1, "23:40", 7))
        days[weekAgo] = day(weekAgo, log(1, "00:05", 4), log(1, "00:20", 100))

        val a = service(clock = clock).board().row("A은행")

        assertThat(a.recent).isEqualTo(8L)
        assertThat(a.baseline).isEqualTo(11L)
        assertThat(a.todayTotal).isEqualTo(53L)

        // 꼬리를 읽을 전날 조회가 실패하면 그 값은 모른다
        days[yesterday] = null
        days[dayBeforeWeekAgo] = null

        val unknown = service(clock = clock).board().row("A은행")

        assertThat(unknown.recent).isNull()
        assertThat(unknown.baseline).isNull()
        assertThat(unknown.todayTotal).isEqualTo(53L)
    }

    @Test
    fun 당일_테이블이_없으면_0이고_기준이_있으면_끊김() {
        days[weekAgo] = day(weekAgo, log(1, "13:45", 412)) // today 는 키가 없다 = 빈 프로파일

        val board = service().board()

        assertThat(board.logs).isEqualTo(ColumnState.OK)
        assertThat(board.row("A은행").recent).isEqualTo(0L)
        assertThat(board.row("A은행").todayTotal).isEqualTo(0L)
        assertThat(board.row("A은행").status).isEqualTo(LiveStatus.DOWN)
    }

    @Test
    fun 로그_조회가_실패해도_예외와_통계는_나온다() {
        days[today] = null
        days[weekAgo] = day(weekAgo, log(1, "13:45", 412))
        whenever(exceptions.read(any())).thenReturn(mapOf(1L to ErrorCounts(3, 7)))

        val board = service().board()

        assertThat(board.logs).isEqualTo(ColumnState.FAILED)
        assertThat(board.baselineDate).isNull()
        for (r in board.live) {
            assertThat(r.recent).isNull()
            assertThat(r.baseline).isNull()
            assertThat(r.todayTotal).isNull()
            assertThat(r.lastLog).isNull()
            assertThat(r.status).isEqualTo(LiveStatus.UNKNOWN)
        }
        assertThat(board.exceptions).isEqualTo(ColumnState.OK)
        assertThat(board.row("A은행").recentErrors).isEqualTo(3L)
        assertThat(board.row("A은행").todayErrors).isEqualTo(7L)
        assertThat(board.common).isEqualTo(CommonRow(0, 0, false))
        assertThat(board.week).isNotNull()
    }

    @Test
    fun 예외_조회가_실패하면_확인_불가이고_로그_판정은_한다() {
        days[today] = day(today, log(1, "10:00", 5), log(2, "14:00", 300))
        days[weekAgo] = day(weekAgo, log(1, "13:45", 412), log(2, "14:00", 301))
        whenever(exceptions.read(any())).thenThrow(QueryTimeoutException("timeout"))

        val board = service().board()

        assertThat(board.exceptions).isEqualTo(ColumnState.FAILED)
        assertThat(board.common).isNull()
        for (r in board.live) {
            assertThat(r.recentErrors).isNull()
            assertThat(r.todayErrors).isNull()
        }
        assertThat(board.row("A은행").status).isEqualTo(LiveStatus.DOWN)
        assertThat(board.row("B카드").status).isEqualTo(LiveStatus.OK)
    }

    @Test
    fun 목록에_없는_고객사의_예외는_공통으로_모은다() {
        whenever(exceptions.read(any())).thenReturn(
            mapOf(0L to ErrorCounts(2, 2), 999L to ErrorCounts(9, 9), 1L to ErrorCounts(3, 4)))

        val board = service().board()

        assertThat(board.common).isEqualTo(CommonRow(11, 11, true))
        assertThat(board.row("A은행").recentErrors).isEqualTo(3L)
        assertThat(board.row("A은행").todayErrors).isEqualTo(4L)
        assertThat(board.row("B카드").recentErrors).isEqualTo(0L)
        assertThat(board.row("B카드").todayErrors).isEqualTo(0L)
        verify(exceptions).read(LocalDateTime.of(2026, 11, 25, 14, 30))
    }

    @Test
    fun 설정으로_끄면_읽지_않는다() {
        whenever(stats.latestGroupby()).thenReturn("2026/11/23/09") // 늦다
        val off = props.copy(liveLogsEnabled = false, exceptionsEnabled = false)

        val board = service(off).board()

        verifyNoInteractions(cache, exceptions)
        assertThat(board.logs).isEqualTo(ColumnState.DISABLED)
        assertThat(board.exceptions).isEqualTo(ColumnState.DISABLED)
        assertThat(board.baselineDate).isNull()
        assertThat(board.common).isNull()
        for (r in board.live) {
            assertThat(r.recent).isNull()
            assertThat(r.baseline).isNull()
            assertThat(r.todayTotal).isNull()
            assertThat(r.lastLog).isNull()
            assertThat(r.recentErrors).isNull()
            assertThat(r.todayErrors).isNull()
        }
        // 로그 열을 끄면 원본을 보지 않고 늦었다는 것만으로 알린다
        assertThat(board.staleNotice).isEqualTo("통계 집계가 2026-11-23 09시에서 멈췄습니다.")
    }

    /**
     * latest·due 는 시간 칸이다. due = 14시 - 24시간 - 3시간 = 11-24 11시 칸.
     * 통계가 빠진 칸은 latest < H <= due 이므로 시각으로는 [11-23 10:00, 11-24 12:00) 이다.
     */
    private fun staleWithOnlyLogAt(date: LocalDate, hm: String): String? {
        days.clear()
        days[date] = day(date, log(1, hm, 1))
        return service().board().staleNotice
    }

    @Test
    fun 통계가_늦고_그_구간에_로그가_있으면_멈춤을_알린다() {
        whenever(stats.latestGroupby()).thenReturn("2026/11/23/09")
        val notice = "통계 집계가 2026-11-23 09시에서 멈췄습니다."
        val nov23 = LocalDate.of(2026, 11, 23)

        assertThat(staleWithOnlyLogAt(yesterday, "08:00")).isEqualTo(notice)
        assertThat(staleWithOnlyLogAt(nov23, "10:00")).isEqualTo(notice)    // latest+1시간: 첫 미집계 칸
        assertThat(staleWithOnlyLogAt(yesterday, "11:00")).isEqualTo(notice) // due 칸의 시작
        assertThat(staleWithOnlyLogAt(yesterday, "11:59")).isEqualTo(notice) // due 칸의 끝
    }

    @Test
    fun 통계가_늦어도_그_구간에_로그가_없으면_알리지_않는다() {
        whenever(stats.latestGroupby()).thenReturn("2026/11/23/09")
        val nov23 = LocalDate.of(2026, 11, 23)

        assertThat(staleWithOnlyLogAt(nov23, "09:59")).isNull()     // latest 칸은 이미 집계됐다
        assertThat(staleWithOnlyLogAt(yesterday, "12:00")).isNull() // due+1시간 칸은 아직 집계할 때가 아니다
        assertThat(staleWithOnlyLogAt(today, "14:00")).isNull()
    }

    @Test
    fun 멈춤_구간이_자정에서_끝나도_그날까지_본다() {
        // 02:10 → due = 11-23 23시 칸, 구간 끝 = 11-24 00:00. 최근 3일은 11-21·22·23 이다(11-24 가 아니다)
        val clock = clockAt("2026-11-25T02:10:00")
        whenever(stats.latestGroupby()).thenReturn("2026/11/10/00")
        days[LocalDate.of(2026, 11, 21)] = day(LocalDate.of(2026, 11, 21), log(1, "12:00", 1))

        assertThat(service(clock = clock).board().staleNotice).isEqualTo("통계 집계가 2026-11-10 00시에서 멈췄습니다.")

        days.clear()
        days[LocalDate.of(2026, 11, 23)] = day(LocalDate.of(2026, 11, 23), log(1, "23:59", 1))

        assertThat(service(clock = clock).board().staleNotice).isEqualTo("통계 집계가 2026-11-10 00시에서 멈췄습니다.")
    }

    @Test
    fun 멈춤_확인은_최근_3일만_본다() {
        whenever(stats.latestGroupby()).thenReturn("2026/11/10/00")
        days[LocalDate.of(2026, 11, 15)] = day(LocalDate.of(2026, 11, 15), log(1, "12:00", 1))

        assertThat(service().board().staleNotice).isNull()
        verify(cache).get(LocalDate.of(2026, 11, 22))
        verify(cache, never()).get(LocalDate.of(2026, 11, 21))
        verify(cache, never()).get(LocalDate.of(2026, 11, 15))
    }

    @Test
    fun 통계가_없으면_주간_표가_없고_알린다() {
        whenever(stats.latestGroupby()).thenReturn(null)

        val board = service().board()

        assertThat(board.week).isNull()
        assertThat(board.staleNotice).isEqualTo("집계된 통계가 없습니다.")
        verify(stats, never()).totals(any(), any())
    }

    @Test
    fun 주간_표는_다_찬_마지막_날에서_끝난다() {
        whenever(stats.latestGroupby()).thenReturn("2026/11/24/13")
        whenever(stats.totals(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23)))
            .thenReturn(mapOf(1L to week(90, 10)))
        whenever(stats.totals(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16)))
            .thenReturn(mapOf(1L to week(80, 20)))

        val board = service().board()

        assertThat(board.week!!.to).isEqualTo(LocalDate.of(2026, 11, 23))
        assertThat(board.week!!.from).isEqualTo(LocalDate.of(2026, 11, 17))
        assertThat(board.week!!.rows).containsExactly(WeekRow(1, "A은행", week(90, 10), week(80, 20), false))
        assertThat(board.staleNotice).isNull()
        verify(stats).totals(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23))
        verify(stats).totals(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16))
    }

    @Test
    fun 직전_주가_없으면_prev가_null이고_하락이_아니다() {
        whenever(stats.totals(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24)))
            .thenReturn(mapOf(1L to week(100, 900)))

        val rows = service().board().week!!.rows

        assertThat(rows).containsExactly(WeekRow(1, "A은행", week(100, 900), null, false)) // 합계가 없는 B카드는 줄이 없다
    }

    @Test
    fun 하락한_고객사가_주간_표_맨_위에_온다() {
        // A은행: 표본이 적어(50 < 100) 하락이 아니지만 변화는 -20%p. B카드: 90 → 80%, 하락
        whenever(stats.totals(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24)))
            .thenReturn(mapOf(1L to week(40, 10), 2L to week(800, 200), 0L to week(1, 1)))
        whenever(stats.totals(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 17)))
            .thenReturn(mapOf(1L to week(50, 0), 2L to week(900, 100)))

        val rows = service().board().week!!.rows

        assertThat(rows.map { it.name }).containsExactly("B카드", "A은행")
        assertThat(rows.map { it.dropped }).containsExactly(true, false)
    }
}
