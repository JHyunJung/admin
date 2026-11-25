package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.dashboard.StatisticsQueryService
import java.time.LocalDate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/**
 * 관제 화면이 쓰는 `FIDO_STATISTICS` 읽기: 최신 통계 시각과 기간별 고객사 합계.
 * 읽기만 하며, GROUPBY 의 시간 형식 규칙은 [StatisticsQueryService] 와 같다.
 */
@Component
class StatBoardReader(private val jdbc: NamedParameterJdbcTemplate) {

    /** 시간 형식(yyyy/MM/dd/HH) GROUPBY 의 최댓값. 없으면 null. `day` 같은 옛 값은 형식 필터로 뺀다. */
    fun latestGroupby(): String? = jdbc.queryForObject(
        "SELECT MAX(GROUPBY) FROM FIDO_STATISTICS" +
            " WHERE REGEXP_LIKE(GROUPBY, '^[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9]{2}\$')",
        emptyMap<String, Any>(), String::class.java)

    /** [from, to] 날짜(양끝 포함)의 고객사별 합계. CCFA_COMPANY 에 없는 고객사는 뺀다. */
    fun totals(from: LocalDate, to: LocalDate): Map<Long, WeekTotals> {
        val params = mapOf(
            "fromKey" to from.format(StatisticsQueryService.DAY_KEY),
            "toKey" to to.plusDays(1).format(StatisticsQueryService.DAY_KEY),
        )
        // 'yyyy/MM/dd/HH' 의 사전순 = 시간순. day 같은 옛 값은 이 범위에 들어오지 않는다.
        val sql = "SELECT COMPANY_IDX," +
            " NVL(SUM(AUTH_S),0) AUTH_S, NVL(SUM(AUTH_F),0) AUTH_F, NVL(SUM(REG_S),0) REG_S," +
            " NVL(SUM(REG_F),0) REG_F, NVL(SUM(DEREG_S),0) DEREG_S" +
            " FROM FIDO_STATISTICS" +
            " WHERE GROUPBY >= :fromKey AND GROUPBY < :toKey" +
            " AND COMPANY_IDX IN (SELECT IDX FROM CCFA_COMPANY)" +
            " GROUP BY COMPANY_IDX"
        val result = HashMap<Long, WeekTotals>()
        jdbc.query(sql, params) { rs ->
            result[rs.getLong("COMPANY_IDX")] = WeekTotals(
                rs.getLong("AUTH_S"), rs.getLong("AUTH_F"), rs.getLong("REG_S"),
                rs.getLong("REG_F"), rs.getLong("DEREG_S"))
        }
        return result
    }
}
