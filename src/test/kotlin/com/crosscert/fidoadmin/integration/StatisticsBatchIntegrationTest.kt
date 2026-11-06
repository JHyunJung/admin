package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.statistics.batch.HourlyAggregator
import com.crosscert.fidoadmin.statistics.batch.LogClassifier
import com.crosscert.fidoadmin.statistics.batch.StatCounts
import com.crosscert.fidoadmin.statistics.batch.StatHour
import com.crosscert.fidoadmin.statistics.batch.StatKey
import com.crosscert.fidoadmin.statistics.batch.StatisticsWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * 통계 배치의 DB 쪽: 날짜 테이블을 읽어 세는 것과, 같은 시간을 두 번 써도 한 번만 쌓이는 것.
 * 분할 테이블 FIDO_LOGS_20210101 을 빌려 쓰고, 2021/01/01 통계 행과 함께 끝나면 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatisticsBatchIntegrationTest : OracleContainerSupport() {

    companion object {
        val DAY: LocalDate = LocalDate.of(2021, 1, 1)
        val H14: StatHour = StatHour.parse("2021/01/01/14")
        val H15: StatHour = StatHour.parse("2021/01/01/15")

        private fun enc(op: String, status: String): String {
            val json = "{\"transaction\":{\"op\":\"" + op + "\",\"status\":\"" + status + "\"}}"
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Autowired lateinit var aggregator: HourlyAggregator
    @Autowired lateinit var writer: StatisticsWriter
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun clear() {
        jdbc.update("DELETE FROM FIDO_LOGS_20210101 WHERE IDX BETWEEN 9100 AND 9199")
        jdbc.update("DELETE FROM FIDO_STATISTICS WHERE GROUPBY LIKE '2021/01/01/%'")
    }

    private fun log(idx: Int, company: Long, service: String, jsondata: String, time: String) {
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) " +
            "VALUES (?, ?, 'S', ?, ?, TIMESTAMP '" + time + "')", idx, company, service, jsondata)
    }

    @Test
    fun 날짜_테이블을_시간_회사_서비스별로_센다() {
        log(9101, 1, "kbstar", enc("Auth", "Success"), "2021-01-01 14:00:00")
        log(9102, 1, "kbstar", enc("Auth", "Error"), "2021-01-01 14:59:59")
        log(9103, 1, "kbpay", enc("Reg", "Success"), "2021-01-01 14:30:00")
        log(9104, 2, "testsvc", enc("Unknown", "Success"), "2021-01-01 14:10:00") // 셀 수 없어도 행은 생긴다
        log(9105, 1, "kbstar", "@@broken@@", "2021-01-01 14:20:00")                // 깨진 로그도 행은 생긴다
        log(9106, 999, "ghost", enc("Auth", "Success"), "2021-01-01 14:10:00")    // CCFA_COMPANY 에 없는 회사
        log(9107, 1, "kbstar", enc("TC", "Success"), "2021-01-01 15:00:00")
        log(9108, 1, "kbstar", enc("DeReg", "Success"), "2021-01-01 16:00:00")     // 요청하지 않은 시간

        val r: Map<StatHour, Map<StatKey, StatCounts>> = aggregator.aggregate(DAY, listOf(H14, H15))

        assertThat(r).containsOnlyKeys(H14, H15)
        val h14: Map<StatKey, StatCounts> = r[H14]!!
        assertThat(h14).containsOnlyKeys(StatKey(1, "kbstar"), StatKey(1, "kbpay"), StatKey(2, "testsvc"))
        val kbstar = h14[StatKey(1, "kbstar")]!!
        assertThat(kbstar.authS).isEqualTo(1)
        assertThat(kbstar.authF).isEqualTo(1)
        assertThat(h14[StatKey(1, "kbpay")]!!.regS).isEqualTo(1)
        val empty = h14[StatKey(2, "testsvc")]!!
        assertThat(empty.authS + empty.authF + empty.regS + empty.tcS).isZero()
        assertThat(r[H15]!![StatKey(1, "kbstar")]!!.tcS).isEqualTo(1)
    }

    @Test
    fun 없는_날짜_테이블은_빈_결과다() {
        assertThat(aggregator.aggregate(LocalDate.of(1999, 1, 1), listOf(StatHour.parse("1999/01/01/00")))).isEmpty()
    }

    @Test
    fun 같은_시간을_두번_써도_한번만_쌓인다() {
        val counts: Map<StatKey, StatCounts> = mapOf(
            StatKey(1, "kbstar") to StatCounts().add(LogClassifier.Kind.AUTH, true),
            StatKey(1, "kbpay") to StatCounts().add(LogClassifier.Kind.REG, false))

        assertThat(writer.write(H14, counts)).isEqualTo(2)
        assertThat(writer.write(H14, counts)).isZero()

        assertThat(rows("2021/01/01/14")).isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT AUTH_S FROM FIDO_STATISTICS WHERE GROUPBY = '2021/01/01/14' AND SERVICE_NAME = 'kbstar'",
            Long::class.javaObjectType)).isEqualTo(1L)
        assertThat(writer.loadedGroupbys(StatHour.parse("2021/01/01/00"), StatHour.parse("2021/01/01/23")))
            .containsExactly("2021/01/01/14")
    }

    @Test
    fun 다른_회사만_있으면_그_회사만_추가한다() {
        writer.write(H14, mapOf(StatKey(1, "kbstar") to StatCounts().add(LogClassifier.Kind.AUTH, true)))

        val added = writer.write(H14, mapOf(
            StatKey(1, "kbstar") to StatCounts().add(LogClassifier.Kind.AUTH, true),
            StatKey(2, "testsvc") to StatCounts().add(LogClassifier.Kind.REG, true)))

        assertThat(added).isEqualTo(1)
        assertThat(rows("2021/01/01/14")).isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FIDO_STATISTICS WHERE GROUPBY = '2021/01/01/14' AND COMPANY_IDX = 1",
            Int::class.javaObjectType)).isEqualTo(1)
    }

    private fun rows(groupby: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM FIDO_STATISTICS WHERE GROUPBY = ?", Int::class.javaObjectType, groupby)!!
}
