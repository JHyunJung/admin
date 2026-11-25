package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * FIDO_LOGS_yyyyMMdd → FIDO_STATISTICS 시간별 집계. 구 어드민의 FIDOLogScheduler 를 대신한다.
 *
 * 매 실행마다 최근 `catch-up-hours` 안에서 GROUPBY 가 없는 시간을 오래된 것부터 모두 처리한다.
 * 가장 늦은 대상은 `정각(now) - 24h` 이다(구 배치와 같은 지연 — 그 날짜 테이블이 다 찬 뒤에 센다).
 * 로그가 없는 시간은 행이 생기지 않으므로 다음 실행에 다시 확인한다.
 */
@Component
@ConditionalOnProperty(prefix = "fido-admin.statistics", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FidoStatisticsJob(
    private val props: StatisticsBatchProperties,
    aggregator: HourlyAggregator,
    writer: StatisticsWriter,
) {
    private val filler = StatisticsFiller(aggregator, writer)
    private val running = AtomicBoolean()

    @Scheduled(cron = "\${fido-admin.statistics.cron:0 15 * * * *}", zone = "Asia/Seoul")
    fun run() {
        run(LocalDateTime.now(ZONE))
    }

    /** @return 이번 실행에서 넣은 행 수. 이전 실행이 아직 돌고 있으면 -1 */
    internal fun run(now: LocalDateTime): Int {
        if (!running.compareAndSet(false, true)) {
            log.warn("statistics: 이전 실행이 아직 끝나지 않아 건너뜁니다")
            return -1
        }
        try {
            return process(now)
        } finally {
            running.set(false)
        }
    }

    private fun process(now: LocalDateTime): Int {
        val latest = StatHour.of(now).plusHours(-DELAY_HOURS.toLong())
        val oldest = latest.plusHours(-(props.catchUpHours - 1L))
        return filler.fill(oldest, latest).rows
    }

    companion object {
        private val log = LoggerFactory.getLogger(FidoStatisticsJob::class.java)

        @JvmField
        internal val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        internal const val DELAY_HOURS = 24
    }
}
