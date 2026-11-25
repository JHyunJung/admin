package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.monitoring.DayProfileReader
import com.crosscert.fidoadmin.monitoring.ErrorCounts
import com.crosscert.fidoadmin.monitoring.ExceptionCountReader
import com.crosscert.fidoadmin.monitoring.StatBoardReader
import com.crosscert.fidoadmin.monitoring.WeekTotals
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * 관제 화면의 DB 쪽 읽기. 분할 테이블 FIDO_LOGS_20210101 을 빌려 쓰고(IDX 9300~9399) 끝나면 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MonitoringReadersIntegrationTest : OracleContainerSupport() {

    @Autowired internal lateinit var reader: DayProfileReader
    @Autowired internal lateinit var exceptions: ExceptionCountReader
    @Autowired internal lateinit var stats: StatBoardReader
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun clear() {
        jdbc.update("DELETE FROM FIDO_LOGS_20210101 WHERE IDX BETWEEN 9300 AND 9399")
        jdbc.update("DELETE FROM CCFA_EXCEPTIONS WHERE IDX BETWEEN 9300 AND 9399")
        jdbc.update("DELETE FROM FIDO_STATISTICS WHERE SERVICE_NAME LIKE 'montest%'")
    }

    private fun logWithoutTime(idx: Int, company: Long) {
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) " +
            "VALUES (?, ?, 'S', 'svc', 'x', NULL)", idx, company)
    }

    private fun log(idx: Int, company: Long, time: String) {
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) " +
            "VALUES (?, ?, 'S', 'svc', 'x', TIMESTAMP '" + time + "')", idx, company)
    }

    private fun at(h: Int, m: Int): LocalDateTime = LocalDateTime.of(2021, 1, 1, h, m)

    @Test
    fun 날짜_테이블을_고객사_분별로_센다() {
        log(9301, 1, "2021-01-01 14:05:10")
        log(9302, 1, "2021-01-01 14:05:50")
        log(9303, 1, "2021-01-01 15:00:00")
        log(9304, 2, "2021-01-01 00:00:30")
        log(9305, 999999, "2021-01-01 14:05:00") // CCFA_COMPANY 에 없는 고객사는 세지 않는다

        val p = reader.read(LocalDate.of(2021, 1, 1))!!

        assertThat(p.total(1)).isEqualTo(3)
        assertThat(p.total(999999)).isEqualTo(0)
        assertThat(p.count(1, at(14, 5), at(14, 6))).isEqualTo(2)
        assertThat(p.lastLog(1)).isEqualTo(LocalDateTime.of(2021, 1, 1, 15, 0, 0))
    }

    @Test
    fun 테이블이_없는_날짜는_null() {
        assertThat(reader.read(LocalDate.of(2019, 1, 1))).isNull()
    }

    @Test
    fun 생성시각이_없는_행은_세지_않고_조회도_깨지_않는다() {
        log(9311, 1, "2021-01-01 10:00:00")
        logWithoutTime(9312, 1) // CREATEDTIME 은 NULL 이 허용된다. 어느 분에도 넣을 수 없다

        val p = reader.read(LocalDate.of(2021, 1, 1))!!

        assertThat(p.total(1)).isEqualTo(1)
    }

    private fun ex(idx: Int, company: Long?, level: String, time: String) {
        jdbc.update("INSERT INTO CCFA_EXCEPTIONS (IDX, COMPANY_IDX, E_TYPE, E_LEVEL, CREATEDTIME) VALUES (?, ?, 'T', ?, ?)",
            idx, company, level, time)
    }

    /** 서비스 이름에는 항상 montest- 를 붙인다. 정리와 다른 테스트 행 보호를 위해서다. */
    private fun stat(company: Long, service: String, groupby: String,
                     authS: Long = 0, authF: Long = 0, regS: Long = 0, regF: Long = 0, deregS: Long = 0) {
        jdbc.update("INSERT INTO FIDO_STATISTICS (COMPANY_IDX, SERVICE_NAME, GROUPBY, AUTH_S, AUTH_F, REG_S, REG_F, DEREG_S) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", company, "montest-$service", groupby, authS, authF, regS, regF, deregS)
    }

    @Test
    fun 예외는_ERROR만_최근과_오늘로_센다() {
        val now = LocalDateTime.of(2021, 1, 2, 14, 30)
        ex(9301, 1, "ERROR", "2021-01-02 13:45:00"); ex(9302, 1, "ERROR", "2021-01-02 09:00:00")
        ex(9303, 1, "WARN", "2021-01-02 14:00:00"); ex(9304, 1, "ERROR", "2021-01-01 23:00:00")
        ex(9305, 0, "ERROR", "2021-01-02 14:29:00"); ex(9306, null, "ERROR", "2021-01-02 14:10:00")

        val m = exceptions.read(now)

        assertThat(m[1L]).isEqualTo(ErrorCounts(recent = 1, today = 2))
        assertThat(m[0L]).isEqualTo(ErrorCounts(recent = 2, today = 2))
    }

    @Test
    fun 최근은_로그와_같은_60분이고_오늘은_지금_분까지_센다() {
        // now 14:30 → 최근 [13:30, 14:30), 오늘 [00:00, 14:31)
        ex(9311, 1, "ERROR", "2021-01-02 13:30:00") // 최근 구간의 첫 분: 최근에 든다
        ex(9312, 1, "ERROR", "2021-01-02 13:29:59") // 그 직전: 최근이 아니고 오늘에만 든다
        ex(9313, 1, "ERROR", "2021-01-02 14:30:20") // 지금 분: 최근이 아니고 오늘에는 든다
        ex(9314, 1, "ERROR", "2021-01-02 14:31:00") // 다음 분: 어디에도 들지 않는다

        val m = exceptions.read(LocalDateTime.of(2021, 1, 2, 14, 30, 40))

        assertThat(m[1L]).isEqualTo(ErrorCounts(recent = 1, today = 3))
    }

    @Test
    fun 자정_직후에는_전날_꼬리가_최근에_들어간다() {
        ex(9307, 1, "ERROR", "2021-01-01 23:40:00"); ex(9308, 1, "ERROR", "2021-01-02 00:05:00")

        val m = exceptions.read(LocalDateTime.of(2021, 1, 2, 0, 20))

        assertThat(m[1L]).isEqualTo(ErrorCounts(recent = 2, today = 1))
    }

    @Test
    fun 생성시각이_없는_예외는_세지_않는다() {
        ex(9309, 1, "ERROR", "2021-01-02 10:00:00")
        jdbc.update("INSERT INTO CCFA_EXCEPTIONS (IDX, COMPANY_IDX, E_TYPE, E_LEVEL, CREATEDTIME) VALUES (9310, 1, 'T', 'ERROR', NULL)")

        val m = exceptions.read(LocalDateTime.of(2021, 1, 2, 14, 30))

        assertThat(m[1L]).isEqualTo(ErrorCounts(recent = 0, today = 1))
    }

    @Test
    fun 최신_GROUPBY는_시간_형식만_본다() {
        stat(1, "kbstar", "2021/01/05/13", authS = 1); stat(1, "kbstar", "day", authS = 1)

        val latest = stats.latestGroupby()

        // 공용 DB 에 더 새로운 통계가 있을 수 있어 하한과 형식만 본다
        assertThat(latest).isNotEqualTo("day")
        assertThat(latest).matches("\\d{4}/\\d{2}/\\d{2}/\\d{2}")
        assertThat(latest).isGreaterThanOrEqualTo("2021/01/05/13")
    }

    @Test
    fun 기간_합계는_양끝_날짜를_포함하고_서비스를_합친다() {
        stat(1, "kbstar", "2021/01/03/00", authS = 10, authF = 1); stat(1, "kbpay", "2021/01/04/23", authS = 5, regS = 2, deregS = 1)
        stat(1, "kbstar", "2021/01/05/00", authS = 100) // 범위 밖
        stat(999999, "x", "2021/01/03/10", authS = 7) // 없는 고객사

        val m = stats.totals(LocalDate.of(2021, 1, 3), LocalDate.of(2021, 1, 4))

        assertThat(m[1L]).isEqualTo(WeekTotals(15, 1, 2, 0, 1))
        assertThat(m).doesNotContainKey(999999L)
    }
}
