package com.crosscert.fidoadmin.statistics.batch

import com.crosscert.fidoadmin.log.service.FidoLogTable
import java.sql.Timestamp
import java.time.LocalDate
import java.util.TreeMap
import java.util.TreeSet
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

/**
 * 날짜 테이블 `FIDO_LOGS_yyyyMMdd` 를 **한 번** 읽어 그 날짜의 여러 시간을 (시간, 회사, 서비스)별로 센다.
 *
 * 일자 테이블에는 CREATEDTIME 인덱스가 없어(PK 만 있다) 시간마다 읽으면 하루 24번 전체를 훑는다.
 * 그래서 빠진 시간들을 한 쿼리로 읽는다. 트랜잭션·잠금 없이 읽기만 한다.
 */
@Component
class HourlyAggregator(dataSource: DataSource, private val tables: FidoLogTable) {

    private val jdbc: NamedParameterJdbcTemplate

    init {
        val t = JdbcTemplate(dataSource)
        t.fetchSize = FETCH_SIZE
        jdbc = NamedParameterJdbcTemplate(t)
    }

    /**
     * @param date  날짜 테이블의 날짜
     * @param hours 그 날짜에서 셀 시간들(모두 `date` 에 속해야 한다)
     * @return 시간 → (회사, 서비스) → 누적값. 로그가 없는 시간은 키가 없다. 테이블이 없으면 빈 맵
     */
    fun aggregate(date: LocalDate, hours: Collection<StatHour>): Map<StatHour, Map<StatKey, StatCounts>> {
        if (hours.isEmpty()) return java.util.Map.of()
        for (h in hours) {
            if (h.date() != date) throw IllegalArgumentException(h.groupby() + " 은(는) " + date + " 이 아닙니다")
        }
        val table = tables.nameFor(date)
        if (!tables.exists(table)) {
            log.info("statistics: {} 테이블이 없어 건너뜁니다", table)
            return java.util.Map.of()
        }
        val sorted = TreeSet(hours)
        val wanted = HashMap<String, StatHour>()
        for (h in sorted) wanted[h.groupby()] = h

        // 테이블 이름은 FidoLogTable.nameFor 가 LocalDate 로만 만든다(신뢰 경계). 나머지는 모두 바인드 값이다.
        val sql = ("SELECT TO_CHAR(CREATEDTIME,'YYYY/MM/DD/HH24') AS GROUPBY, COMPANY_IDX, SERVICENAME, JSONDATA"
            + " FROM " + table
            + " WHERE CREATEDTIME >= :start AND CREATEDTIME < :end"
            + " AND COMPANY_IDX IN (SELECT IDX FROM CCFA_COMPANY)")
        // JdbcTemplate 은 Hibernate jdbc.time_zone 을 쓰지 않는다. Timestamp.valueOf 와 드라이버가 같은 JVM 시간대를 써서
        // 서로 상쇄되므로 벽시계 값 그대로 비교된다.
        val p = MapSqlParameterSource()
            .addValue("start", Timestamp.valueOf(sorted.first().start()))
            .addValue("end", Timestamp.valueOf(sorted.last().end()))

        val result = TreeMap<StatHour, MutableMap<StatKey, StatCounts>>()
        val broken = TreeMap<StatHour, IntArray>()
        jdbc.query(sql, p, RowCallbackHandler { rs ->
            val h = wanted[rs.getString("GROUPBY")] ?: return@RowCallbackHandler // 범위 안이지만 이미 적재된 시간
            val key = StatKey(rs.getLong("COMPANY_IDX"), rs.getString("SERVICENAME"))
            // 로그가 하나라도 있으면 (회사, 서비스) 행이 생긴다 — 셀 수 없는 op 만 있어도 모두 0 인 행(구 배치와 같다).
            val counts = result.computeIfAbsent(h) { LinkedHashMap() }.computeIfAbsent(key) { StatCounts() }
            LogClassifier.classify(rs.getString("JSONDATA")).ifPresentOrElse(
                { r -> counts.add(r.kind, r.success) },
                { broken.computeIfAbsent(h) { IntArray(1) }[0]++ })
        })
        broken.forEach { (h, n) -> log.warn("statistics: {} 디코드 실패 {}건(세지 않음)", h.groupby(), n[0]) }
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(HourlyAggregator::class.java)

        /** 한 번에 받아 오는 행 수. Oracle 드라이버 기본(10행)으로는 큰 테이블에서 왕복이 너무 많다. */
        internal const val FETCH_SIZE = 500
    }
}
