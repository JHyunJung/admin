package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.monitoring.DayProfileReader
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

    @Autowired lateinit var reader: DayProfileReader
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun clear() {
        jdbc.update("DELETE FROM FIDO_LOGS_20210101 WHERE IDX BETWEEN 9300 AND 9399")
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
}
