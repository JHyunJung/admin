package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

/**
 * SUPER 관제 화면 모델을 조립한다. 읽기는 모두 부품(캐시·리더)에 맡기고 여기서는 판정과 정렬만 한다.
 *
 * 로그·예외는 따로 실패할 수 있어 열마다 [ColumnState] 를 둔다: 한쪽을 못 읽어도 다른 쪽과 통계는 보여 준다.
 * 통계 조회 실패는 잡지 않는다 — 기존 대시보드와 같은 DB 라 화면 전체 오류가 맞다.
 *
 * 고객사를 고르지 않은 SUPER 도 열어야 하므로 `tenant.companyIdx()` 는 부르지 않는다(미선택 SUPER 에게 예외다).
 */
@Service
class MonitoringService internal constructor(
    private val cache: DayProfileCache,
    private val exceptions: ExceptionCountCache,
    private val stats: StatBoardReader,
    private val companies: CompanyLookup,
    private val tenant: TenantContext,
    private val props: MonitoringProperties,
    private val clock: Clock,
) {

    @Autowired
    internal constructor(
        cache: DayProfileCache,
        exceptions: ExceptionCountCache,
        stats: StatBoardReader,
        companies: CompanyLookup,
        tenant: TenantContext,
        props: MonitoringProperties,
    ) : this(cache, exceptions, stats, companies, tenant, props, Clock.system(FidoStatisticsJob.ZONE))

    /**
     * 판정 한 번에 쓰는 로그 값. 값이 null 이면 옆의 상태가 이유다.
     * [today] 가 null 이면 로그 열이 OK 가 아니고, 그때는 두 구간도 같은 상태다.
     */
    private class Logs(
        val state: ColumnState,
        val today: DayProfile?,
        val recentState: ColumnState,
        val recent: Window?,
        val baselineState: ColumnState,
        val baseline: Window?,
        val baselineDate: LocalDate?,
    ) {
        companion object {
            fun unread(state: ColumnState) = Logs(state, null, state, null, state, null, null)
        }
    }

    /** SUPER 가 아니면 AccessDeniedException. */
    fun board(): MonitoringBoard {
        if (!tenant.require().isSuper) throw AccessDeniedException("SUPER 관리자만 볼 수 있습니다")

        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        val recentFrom = now.minusMinutes(RECENT_MINUTES)
        // IDX → 이름. IDX 0(전역 레코드)은 고객사 줄이 아니다
        val names = companies.all()
            .filter { it.idx != null && it.idx != 0L }
            .associate { it.idx!! to (it.companyName ?: "#${it.idx}") }

        val logs = readLogs(recentFrom, now)
        val (exState, errors) = readExceptions(now)

        val today = logs.today
        val live = names.map { (idx, name) ->
            val recent = logs.recent?.count(idx)
            val baseline = logs.baseline?.count(idx)
            val todayTotal = today?.total(idx)
            val err = errors?.let { it[idx] ?: ErrorCounts(0, 0) }
            LiveRow(
                companyIdx = idx,
                name = name,
                status = LiveStatus.judge(recent, baseline, todayTotal, err?.recent, props),
                recent = recent,
                baseline = baseline,
                todayTotal = todayTotal,
                lastLog = today?.lastLog(idx),
                recentErrors = err?.recent,
                todayErrors = err?.today,
            )
        }.sortedWith(
            compareBy<LiveRow> { it.status.ordinal }
                .thenBy(nullsLast(reverseOrder())) { it.recent }
                .thenBy { it.name })

        val common = errors?.let { map ->
            val others = map.filterKeys { it !in names }.values
            val recent = others.sumOf { it.recent }
            CommonRow(recent, others.sumOf { it.today }, recent >= props.exceptionThreshold)
        }

        // GROUPBY 형식 필터를 지나도 13월 같은 값은 해석되지 않는다. 그 한 줄 때문에 화면 전체가 깨지지 않게 한다
        val raw = stats.latestGroupby()
        val latest = raw?.let { parseGroupby(it) }
        val unparsed = raw != null && latest == null

        return MonitoringBoard(
            asOf = now,
            cacheSeconds = props.cacheSeconds,
            logs = logs.state,
            recentLogs = logs.recentState,
            baselineLogs = logs.baselineState,
            exceptions = exState,
            baselineDate = logs.baselineDate,
            common = common,
            live = live,
            staleNotice = if (unparsed) "통계의 최신 집계 시각을 해석하지 못했습니다: $raw" else staleNotice(latest, now, logs.state),
            // 최신 통계 날짜는 아직 다 차지 않았을 수 있으니 그 전날에서 끝낸다
            week = latest?.let { weekTable(it.toLocalDate().minusDays(1), names) },
        )
    }

    private fun parseGroupby(v: String): LocalDateTime? =
        try {
            LocalDateTime.parse(v, GROUPBY)
        } catch (e: DateTimeParseException) {
            null
        }

    /**
     * 당일 프로파일, 최근 구간 `[recentFrom, now)`, 기준 구간. 당일을 못 읽으면 나머지도 읽지 않는다(쓸 값이 없다).
     *
     * 기준 구간은 최근 구간을 기준일 후보(7일 전, 다음 전일)까지 같은 일수만큼 뒤로 민 것이다. 후보는 그 구간이
     * 걸친 날짜(자정 직후면 전날까지)를 모두 읽었고 그중 하루라도 로그가 있을 때 쓴다 — 기준일 자체가 비어도
     * 자정 전 꼬리에 로그가 있으면 끊김을 판정할 수 있다. 읽지 못한 날짜가 있는 후보는 건너뛰고,
     * 쓸 후보가 없을 때 그런 후보가 있었으면 기준은 "확인 불가", 없었으면 "기준일 없음" 이다.
     */
    private fun readLogs(recentFrom: LocalDateTime, now: LocalDateTime): Logs {
        if (!props.liveLogsEnabled) return Logs.unread(ColumnState.DISABLED)
        val today = now.toLocalDate()
        val profile = cache.get(today) ?: return Logs.unread(ColumnState.FAILED)
        val recent = window(profile, recentFrom, now)
        val recentState = if (recent == null) ColumnState.FAILED else ColumnState.OK

        var failed = false
        for (days in BASELINE_DAYS) {
            val date = today.minusDays(days)
            val w = cache.get(date)?.let { window(it, recentFrom.minusDays(days), now.minusDays(days)) }
            if (w == null) {
                failed = true
                continue
            }
            if (w.days.any { it.hasAnyLog() }) {
                return Logs(ColumnState.OK, profile, recentState, recent, ColumnState.OK, w, date)
            }
        }
        val baselineState = if (failed) ColumnState.FAILED else ColumnState.OK
        return Logs(ColumnState.OK, profile, recentState, recent, baselineState, null, null)
    }

    private fun DayProfile.hasAnyLog(): Boolean = anyBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())

    /** 예외 맵. 꺼져 있거나 실패하면 null 이다. 꺼져 있으면 보관소도 부르지 않는다(조회가 없다). */
    private fun readExceptions(now: LocalDateTime): Pair<ColumnState, Map<Long, ErrorCounts>?> {
        if (!props.exceptionsEnabled) return ColumnState.DISABLED to null
        val counts = exceptions.get(now) ?: return ColumnState.FAILED to null
        return ColumnState.OK to counts
    }

    /**
     * `to` 가 [day] 의 날짜 안에 있는 구간. `from` 이 전날로 넘어가면(자정 직후) 전날 프로파일도 함께 센다.
     * 그 전날을 못 읽으면 null 이다 — 일부만 센 값을 내면 끊김·감소로 잘못 판정한다.
     */
    private fun window(day: DayProfile, from: LocalDateTime, to: LocalDateTime): Window? {
        if (!from.toLocalDate().isBefore(day.date)) return Window(from, to, listOf(day))
        val prev = cache.get(day.date.minusDays(1)) ?: return null
        return Window(from, to, listOf(prev, day))
    }

    /** [from, to) 와 그 구간을 덮는 날짜 프로파일들. */
    private class Window(val from: LocalDateTime, val to: LocalDateTime, val days: List<DayProfile>) {
        fun count(companyIdx: Long): Long = days.sumOf { it.count(companyIdx, from, to) }
    }

    /**
     * 통계가 `정각 - 24시간 - staleHours`(due 칸) 보다 늦고, 통계가 빠진 칸에 원본 로그가 있으면 알린다.
     * 빠진 칸의 원본을 하루라도 읽지 못했고 읽은 날에는 로그가 없으면, 멈췄는지 모른다는 것을 따로 알린다
     * (모르는 것을 "괜찮다"로 보이지 않게).
     *
     * latest·due 는 GROUPBY 시간 칸이다. 빠진 칸은 latest < H <= due 이므로 시각으로는 `[latest+1시간, due+1시간)` 이다:
     * latest 칸은 이미 집계됐고, due 칸은 집계됐어야 한다. 원본은 그 구간의 가장 최근 [STALE_LOOKBACK_DAYS] 일만 본다.
     * 로그 열을 껐으면 원본을 보지 않고 늦었다는 것만으로 알린다.
     */
    private fun staleNotice(latest: LocalDateTime?, now: LocalDateTime, logs: ColumnState): String? {
        if (latest == null) return "집계된 통계가 없습니다."
        val due = now.truncatedTo(ChronoUnit.HOURS).minusHours(24L + props.staleHours)
        if (!latest.isBefore(due)) return null
        val notice = "통계 집계가 ${latest.format(NOTICE)}시에서 멈췄습니다."
        if (logs == ColumnState.DISABLED) return notice

        val from = latest.plusHours(1)
        val to = due.plusHours(1) // latest < due 이므로 from <= due < to: 구간은 비지 않는다
        // 끝이 자정이면 그날은 구간에 들지 않으니 1분 앞의 날짜가 마지막 날이다
        val last = to.minusMinutes(1).toLocalDate()
        val start = maxOf(from.toLocalDate(), last.minusDays(STALE_LOOKBACK_DAYS - 1))
        var unreadable = false
        for (date in generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }) {
            val day = cache.get(date)
            if (day == null) unreadable = true
            else if (day.anyBetween(from, to)) return notice
        }
        if (unreadable) return "통계 집계가 ${latest.format(NOTICE)}시 이후 확인되지 않습니다. 원본 로그를 읽지 못해 멈춤 여부는 확인 불가입니다."
        return null
    }

    /** 최근 주 `[end-6, end]` 와 직전 주. 어느 쪽이든 합계가 있는 고객사만 줄을 만든다. */
    private fun weekTable(end: LocalDate, names: Map<Long, String>): WeekTable {
        val from = end.minusDays(6)
        val cur = stats.totals(from, end)
        val prev = stats.totals(end.minusDays(13), end.minusDays(7))
        val rows = names.filterKeys { it in cur || it in prev }.map { (idx, name) ->
            val c = cur[idx] ?: WeekTotals(0, 0, 0, 0, 0)
            val p = prev[idx]
            WeekRow(idx, name, c, p, c.dropped(p, props))
        }.sortedWith(
            compareByDescending<WeekRow> { it.dropped }
                .thenBy(nullsLast()) { it.cur.authRatePointChange(it.prev) }
                .thenBy { it.name })
        return WeekTable(from, end, rows)
    }

    private companion object {
        const val RECENT_MINUTES = 60L

        /** 기준일 후보(며칠 전). 같은 요일을 먼저 본다. */
        val BASELINE_DAYS = listOf(7L, 1L)

        const val STALE_LOOKBACK_DAYS = 3L

        val GROUPBY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")
        val NOTICE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH")
    }
}
