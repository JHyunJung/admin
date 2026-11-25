package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.log.service.FidoLogTable
import java.time.LocalDate
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component

/**
 * 날짜 테이블 `FIDO_LOGS_yyyyMMdd` 한 장을 한 번 읽어 고객사×분 건수([DayProfile])로 만든다.
 *
 * 일자 테이블에는 CREATEDTIME 인덱스가 없어 구간마다 읽으면 그때마다 전체를 훑는다. 그래서 하루치를
 * (고객사, 분)으로 묶은 결과만 가져온다. JSONDATA(CLOB)는 읽지 않는다. 트랜잭션·잠금 없이 읽기만 한다.
 * `HourlyAggregator` 처럼 자기 `JdbcTemplate` 을 쓰며, 여기서는 조회 제한 시간도 건다.
 */
@Component
class DayProfileReader(dataSource: DataSource, private val tables: FidoLogTable, props: MonitoringProperties) {

    private val jdbc: JdbcTemplate = JdbcTemplate(dataSource).also {
        it.fetchSize = FETCH_SIZE
        it.queryTimeout = props.queryTimeoutSeconds
    }

    /** 테이블이 없으면 null. 조회 실패·타임아웃은 예외(DataAccessException)를 그대로 던진다. */
    fun read(date: LocalDate): DayProfile? {
        val table = tables.nameFor(date)
        if (!tables.exists(table)) return null

        // 테이블 이름은 FidoLogTable.nameFor 가 LocalDate 로만 만든다(신뢰 경계). 나머지는 상수다.
        val sql = "SELECT COMPANY_IDX, TO_CHAR(CREATEDTIME,'HH24:MI') AS HM, COUNT(*) AS N, MAX(CREATEDTIME) AS LAST_AT" +
            " FROM " + table +
            " WHERE COMPANY_IDX IN (SELECT IDX FROM CCFA_COMPANY)" +
            " GROUP BY COMPANY_IDX, TO_CHAR(CREATEDTIME,'HH24:MI')"
        val builder = DayProfile.Builder(date)
        jdbc.query(sql, RowCallbackHandler { rs ->
            val hm = rs.getString("HM")
            val minuteOfDay = hm.substring(0, 2).toInt() * 60 + hm.substring(3, 5).toInt()
            // 드라이버와 Timestamp 가 같은 JVM 시간대를 써서 벽시계 값 그대로 돌아온다(HourlyAggregator 와 같다).
            builder.add(rs.getLong("COMPANY_IDX"), minuteOfDay, rs.getLong("N"), rs.getTimestamp("LAST_AT").toLocalDateTime())
        })
        return builder.build()
    }

    companion object {
        /** 한 번에 받아 오는 행 수. Oracle 드라이버 기본(10행)으로는 왕복이 너무 많다. */
        internal const val FETCH_SIZE = 500
    }
}
