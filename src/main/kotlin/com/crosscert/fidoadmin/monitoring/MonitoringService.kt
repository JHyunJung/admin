package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
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
    private val exceptions: ExceptionCountReader,
    private val stats: StatBoardReader,
    private val companies: CompanyLookup,
    private val tenant: TenantContext,
    private val props: MonitoringProperties,
    private val clock: Clock,
) {

    @Autowired
    constructor(
        cache: DayProfileCache,
        exceptions: ExceptionCountReader,
        stats: StatBoardReader,
        companies: CompanyLookup,
        tenant: TenantContext,
        props: MonitoringProperties,
    ) : this(cache, exceptions, stats, companies, tenant, props, Clock.system(FidoStatisticsJob.ZONE))

    /** 판정 한 번에 쓰는 로그 값. [today] 가 null 이면 로그 열이 OK 가 아니다. */
    private class Logs(val state: ColumnState, val today: DayProfile?, val baseline: DayProfile?)

    /** SUPER 가 아니면 AccessDeniedException. */
    fun board(): MonitoringBoard {
        if (!tenant.require().isSuper) throw AccessDeniedException("SUPER 관리자만 볼 수 있습니다")

        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        val recentFrom = now.minusMinutes(RECENT_MINUTES)
        // IDX → 이름. IDX 0(전역 레코드)은 고객사 줄이 아니다
        val names = companies.all()
            .filter { it.idx != null && it.idx != 0L }
            .associate { it.idx!! to (it.companyName ?: "#${it.idx}") }

        val logs = readLogs(now.toLocalDate())
        val (exState, errors) = readExceptions(now)

        val today = logs.today
        val recentWindow = today?.let { window(it, recentFrom, now) }
        // 기준 구간은 최근 구간을 기준일까지 같은 일수만큼 뒤로 민 것이다
        val baselineWindow = logs.baseline?.let { b ->
            val days = ChronoUnit.DAYS.between(b.date, now.toLocalDate())
            window(b, recentFrom.minusDays(days), now.minusDays(days))
        }

        val live = names.map { (idx, name) ->
            val recent = recentWindow?.count(idx)
            val baseline = baselineWindow?.count(idx)
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

        val latest = stats.latestGroupby()?.let { LocalDateTime.parse(it, GROUPBY) }

        return MonitoringBoard(
            asOf = now,
            cacheSeconds = props.cacheSeconds,
            logs = logs.state,
            exceptions = exState,
            baselineDate = logs.baseline?.date,
            common = common,
            live = live,
            staleNotice = staleNotice(latest, now, logs.state),
            // 최신 통계 날짜는 아직 다 차지 않았을 수 있으니 그 전날에서 끝낸다
            week = latest?.let { weekTable(it.toLocalDate().minusDays(1), names) },
        )
    }

    /**
     * 당일 프로파일과 기준일 프로파일. 기준일은 7일 전에 로그가 있으면 그날, 없으면 전일이다.
     * "없다"는 테이블 없음과 조회 실패를 모두 포함한다. 당일을 못 읽으면 기준일도 읽지 않는다(쓸 값이 없다).
     */
    private fun readLogs(today: LocalDate): Logs {
        if (!props.liveLogsEnabled) return Logs(ColumnState.DISABLED, null, null)
        val profile = cache.get(today) ?: return Logs(ColumnState.FAILED, null, null)
        val baseline = BASELINE_DAYS.asSequence()
            .mapNotNull { cache.get(today.minusDays(it)) }
            .firstOrNull { it.anyBetween(it.date.atStartOfDay(), it.date.plusDays(1).atStartOfDay()) }
        return Logs(ColumnState.OK, profile, baseline)
    }

    /** 예외 맵. 꺼져 있거나 실패하면 null 이다. */
    private fun readExceptions(now: LocalDateTime): Pair<ColumnState, Map<Long, ErrorCounts>?> {
        if (!props.exceptionsEnabled) return ColumnState.DISABLED to null
        return try {
            ColumnState.OK to exceptions.read(now)
        } catch (e: DataAccessException) {
            log.warn("monitoring: 예외 건수 조회 실패 — {}", e.javaClass.simpleName)
            ColumnState.FAILED to null
        }
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
     * 통계가 `정각 - 24시간 - staleHours` 보다 늦고, 집계되지 않은 구간 `[latest+1시간, due)` 에 원본 로그가 있으면 알린다.
     * 원본은 그 구간의 가장 최근 [STALE_LOOKBACK_DAYS] 일만 본다. 로그 열을 껐으면 원본을 보지 않고 늦었다는 것만으로 알린다.
     */
    private fun staleNotice(latest: LocalDateTime?, now: LocalDateTime, logs: ColumnState): String? {
        if (latest == null) return "집계된 통계가 없습니다."
        val due = now.truncatedTo(ChronoUnit.HOURS).minusHours(24L + props.staleHours)
        if (!latest.isBefore(due)) return null
        val notice = "통계 집계가 ${latest.format(NOTICE)}시에서 멈췄습니다."
        if (logs == ColumnState.DISABLED) return notice

        val from = latest.plusHours(1)
        if (!from.isBefore(due)) return null
        val first = from.toLocalDate()
        val last = due.minusMinutes(1).toLocalDate()
        val start = maxOf(first, last.minusDays(STALE_LOOKBACK_DAYS - 1))
        val hasLogs = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .any { cache.get(it)?.anyBetween(from, due) == true }
        return if (hasLogs) notice else null
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
        val log = LoggerFactory.getLogger(MonitoringService::class.java)

        const val RECENT_MINUTES = 60L

        /** 기준일 후보(며칠 전). 같은 요일을 먼저 본다. */
        val BASELINE_DAYS = listOf(7L, 1L)

        const val STALE_LOOKBACK_DAYS = 3L

        val GROUPBY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")
        val NOTICE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH")
    }
}
