package com.crosscert.fidoadmin.monitoring

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/** 고객사별 ERROR 예외 건수. [recent] 는 최근 60분, [today] 는 오늘 0시부터. */
data class ErrorCounts(val recent: Long, val today: Long)

/**
 * `CCFA_EXCEPTIONS` 의 ERROR 건수를 고객사별로 센다.
 *
 * CREATEDTIME 은 `yyyy-MM-dd HH:mm:ss` 문자열(VARCHAR2)이라 범위 비교도 문자열로 한다. NULL 은 어느 범위에도
 * 들지 않는다. 한 번의 조회로 두 구간을 세려고 더 넓은 쪽(`min(오늘 0시, now-60분)`)부터 읽고
 * `SUM(CASE …)` 로 나눈다: 자정 직후에는 최근 60분이 어제까지 걸치기 때문이다.
 */
@Component
class ExceptionCountReader(dataSource: DataSource, props: MonitoringProperties) {

    private val jdbc = NamedParameterJdbcTemplate(JdbcTemplate(dataSource).also {
        it.fetchSize = DayProfileReader.FETCH_SIZE
        it.queryTimeout = props.queryTimeoutSeconds
    })

    /** 키는 COMPANY_IDX(NULL 은 0 으로 모은다). 실패는 예외를 던진다. now 는 분으로 내림한다. */
    fun read(now: LocalDateTime): Map<Long, ErrorCounts> {
        val minute = now.withSecond(0).withNano(0)
        val dayStart = minute.toLocalDate().atStartOfDay()
        val recentFrom = minute.minusMinutes(RECENT_MINUTES)
        val params = mapOf(
            "start" to format(minOf(dayStart, recentFrom)),
            "end" to format(minute.plusMinutes(1)),
            "recentFrom" to format(recentFrom),
            "dayStart" to format(dayStart),
        )
        val sql = "SELECT NVL(COMPANY_IDX, 0) AS CIDX," +
            " SUM(CASE WHEN CREATEDTIME >= :recentFrom THEN 1 ELSE 0 END) AS RECENT," +
            " SUM(CASE WHEN CREATEDTIME >= :dayStart THEN 1 ELSE 0 END) AS TODAY" +
            " FROM CCFA_EXCEPTIONS" +
            " WHERE E_LEVEL = 'ERROR' AND CREATEDTIME >= :start AND CREATEDTIME < :end" +
            " GROUP BY NVL(COMPANY_IDX, 0)"
        val result = HashMap<Long, ErrorCounts>()
        jdbc.query(sql, params) { rs ->
            result[rs.getLong("CIDX")] = ErrorCounts(rs.getLong("RECENT"), rs.getLong("TODAY"))
        }
        return result
    }

    private companion object {
        const val RECENT_MINUTES = 60L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun format(t: LocalDateTime): String = t.format(TIME)
    }
}
