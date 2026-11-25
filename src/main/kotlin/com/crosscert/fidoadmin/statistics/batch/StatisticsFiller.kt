package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.util.TreeMap
import org.slf4j.LoggerFactory

/**
 * 한 구간에서 FIDO_STATISTICS 에 GROUPBY 가 없는 시간을 찾아 오래된 것부터 채운다.
 * 정시 배치([FidoStatisticsJob])와 기간 지정 실행([StatisticsBackfill])이 같이 쓴다.
 *
 * 한 날짜를 읽지 못하거나 한 시간을 쓰지 못해도 멈추지 않고 다음으로 넘어간다(실패는 세어서 돌려준다).
 * 로그가 없는 시간은 행이 생기지 않는다.
 */
internal class StatisticsFiller(private val aggregator: HourlyAggregator, private val writer: StatisticsWriter) {

    /**
     * @param missingHours 구간 안에서 통계가 없던 시간 수
     * @param rows         넣은 행 수
     * @param failures     읽지 못한 날짜 수 + 쓰지 못한 시간 수
     */
    data class Result(val missingHours: Int, val rows: Int, val failures: Int)

    /** [oldest, latest] 양끝 포함. */
    fun fill(oldest: StatHour, latest: StatHour): Result {
        val done = writer.loadedGroupbys(oldest, latest)

        val missing = TreeMap<LocalDate, MutableList<StatHour>>()
        var count = 0
        var h = oldest
        while (h <= latest) {
            if (!done.contains(h.groupby())) {
                missing.computeIfAbsent(h.date()) { ArrayList() }.add(h)
                count++
            }
            h = h.plusHours(1)
        }

        var rows = 0
        var failures = 0
        for ((date, hours) in missing) {
            val byHour = try {
                aggregator.aggregate(date, hours)
            } catch (ex: RuntimeException) {
                log.error("statistics: {} 로그 집계 실패", date, ex)
                failures++
                continue
            }
            for (hour in hours) {
                val counts = byHour[hour]
                if (counts == null || counts.isEmpty()) continue
                try {
                    rows += writer.write(hour, counts)
                } catch (ex: RuntimeException) {
                    log.error("statistics: {} 적재 실패", hour.groupby(), ex)
                    failures++
                }
            }
        }
        return Result(count, rows, failures)
    }

    companion object {
        private val log = LoggerFactory.getLogger(StatisticsFiller::class.java)
    }
}
