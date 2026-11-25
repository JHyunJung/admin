package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Component

/**
 * 기간을 지정해 통계를 한 번 채운다(`scripts/fido-admin.sh backfill-statistics`).
 * 정시 배치가 오래 멈춰 `catch-up-hours` 밖으로 밀린 구간을 재시작 없이 메우는 용도다.
 *
 * 정시 배치와 같은 규칙을 쓴다: 통계가 없는 시간만 채우고, 24시간이 지나지 않은 시간은 세지 않는다.
 * 그래서 여러 번 돌리거나 정시 배치와 겹쳐도 중복이 생기지 않는다.
 */
@Component
class StatisticsBackfill(aggregator: HourlyAggregator, writer: StatisticsWriter) {

    private val filler = StatisticsFiller(aggregator, writer)

    /** @param from 실제로 본 첫 시간. @param to 실제로 본 마지막 시간(24시간 규칙으로 줄었을 수 있다) */
    data class Result(val from: StatHour, val to: StatHour, val missingHours: Int, val rows: Int, val failures: Int)

    /**
     * @param from 시작일(그날 0시부터)
     * @param to   끝일(그날 23시까지)
     * @throws IllegalArgumentException 기간이 잘못됐거나 아직 집계할 수 없는 기간일 때
     */
    fun run(from: LocalDate, to: LocalDate, now: LocalDateTime): Result {
        if (from.isAfter(to)) throw IllegalArgumentException("시작일($from)이 끝일($to)보다 늦습니다")
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw IllegalArgumentException("기간은 ${MAX_DAYS}일을 넘을 수 없습니다: $from ~ $to")
        }
        val limit = StatHour.of(now).plusHours(-FidoStatisticsJob.DELAY_HOURS.toLong())
        val oldest = StatHour.of(from.atStartOfDay())
        if (oldest > limit) {
            val at = limit.start()
            throw IllegalArgumentException(
                "아직 집계할 수 없는 기간입니다. 지금은 ${at.toLocalDate()} ${at.hour}시까지만 집계할 수 있습니다")
        }
        val lastOfTo = StatHour.of(to.atTime(23, 0))
        val latest = if (lastOfTo > limit) limit else lastOfTo

        val r = filler.fill(oldest, latest)
        return Result(oldest, latest, r.missingHours, r.rows, r.failures)
    }

    companion object {
        internal const val MAX_DAYS = 366L
    }
}
