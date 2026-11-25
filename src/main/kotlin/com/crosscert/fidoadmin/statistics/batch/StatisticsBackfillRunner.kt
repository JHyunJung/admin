package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

/**
 * `fido-admin.statistics.backfill.from` 이 있으면 뜬 직후 그 기간의 통계를 채우고 프로세스를 끝낸다.
 * 서비스 중인 어드민과 따로 띄우는 일회용 실행이다(`scripts/fido-admin.sh backfill-statistics`).
 *
 * 종료 코드: 0 = 끝까지 돌았다, 1 = 일부 날짜·시간이 실패했다(로그 확인 후 다시 돌리면 빠진 것만 채운다),
 * 2 = 기간이 잘못됐다.
 */
@Component
@ConditionalOnProperty(prefix = "fido-admin.statistics.backfill", name = ["from"])
class StatisticsBackfillRunner(
    private val backfill: StatisticsBackfill,
    private val context: ConfigurableApplicationContext,
    @Value("\${fido-admin.statistics.backfill.from}") private val from: String,
    @Value("\${fido-admin.statistics.backfill.to:}") private val to: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val code = execute()
        exitProcess(SpringApplication.exit(context, { code }))
    }

    private fun execute(): Int {
        try {
            val r = backfill.run(parse("시작일", from), parse("끝일", to), LocalDateTime.now(FidoStatisticsJob.ZONE))
            log.info("statistics: 기간 지정 실행 끝 — {} ~ {}, 통계가 없던 시간 {}개, 넣은 행 {}개, 실패 {}건",
                r.from.groupby(), r.to.groupby(), r.missingHours, r.rows, r.failures)
            return if (r.failures == 0) 0 else 1
        } catch (ex: IllegalArgumentException) {
            log.error("statistics: 기간 지정 실행을 시작하지 못했습니다 — {}", ex.message)
            return 2
        } catch (ex: RuntimeException) {
            log.error("statistics: 기간 지정 실행 실패", ex)
            return 1
        }
    }

    private fun parse(name: String, value: String): LocalDate = try {
        LocalDate.parse(value.trim())
    } catch (ex: DateTimeParseException) {
        throw IllegalArgumentException("$name 은 yyyy-MM-dd 로 적습니다: '$value'")
    }

    companion object {
        private val log = LoggerFactory.getLogger(StatisticsBackfillRunner::class.java)
    }
}
