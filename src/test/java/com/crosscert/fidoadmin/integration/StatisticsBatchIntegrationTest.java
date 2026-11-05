package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.statistics.batch.HourlyAggregator;
import com.crosscert.fidoadmin.statistics.batch.LogClassifier;
import com.crosscert.fidoadmin.statistics.batch.StatCounts;
import com.crosscert.fidoadmin.statistics.batch.StatHour;
import com.crosscert.fidoadmin.statistics.batch.StatKey;
import com.crosscert.fidoadmin.statistics.batch.StatisticsWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통계 배치의 DB 쪽: 날짜 테이블을 읽어 세는 것과, 같은 시간을 두 번 써도 한 번만 쌓이는 것.
 * 분할 테이블 FIDO_LOGS_20210101 을 빌려 쓰고, 2021/01/01 통계 행과 함께 끝나면 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatisticsBatchIntegrationTest extends OracleContainerSupport {

    static final LocalDate DAY = LocalDate.of(2021, 1, 1);
    static final StatHour H14 = StatHour.parse("2021/01/01/14");
    static final StatHour H15 = StatHour.parse("2021/01/01/15");

    @Autowired HourlyAggregator aggregator;
    @Autowired StatisticsWriter writer;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear() {
        jdbc.update("DELETE FROM FIDO_LOGS_20210101 WHERE IDX BETWEEN 9100 AND 9199");
        jdbc.update("DELETE FROM FIDO_STATISTICS WHERE GROUPBY LIKE '2021/01/01/%'");
    }

    private static String enc(String op, String status) {
        String json = "{\"transaction\":{\"op\":\"" + op + "\",\"status\":\"" + status + "\"}}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void log(int idx, long company, String service, String jsondata, String time) {
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) "
            + "VALUES (?, ?, 'S', ?, ?, TIMESTAMP '" + time + "')", idx, company, service, jsondata);
    }

    @Test void 날짜_테이블을_시간_회사_서비스별로_센다() {
        log(9101, 1, "kbstar", enc("Auth", "Success"), "2021-01-01 14:00:00");
        log(9102, 1, "kbstar", enc("Auth", "Error"), "2021-01-01 14:59:59");
        log(9103, 1, "kbpay", enc("Reg", "Success"), "2021-01-01 14:30:00");
        log(9104, 2, "testsvc", enc("Unknown", "Success"), "2021-01-01 14:10:00"); // 셀 수 없어도 행은 생긴다
        log(9105, 1, "kbstar", "@@broken@@", "2021-01-01 14:20:00");                // 깨진 로그도 행은 생긴다
        log(9106, 999, "ghost", enc("Auth", "Success"), "2021-01-01 14:10:00");    // CCFA_COMPANY 에 없는 회사
        log(9107, 1, "kbstar", enc("TC", "Success"), "2021-01-01 15:00:00");
        log(9108, 1, "kbstar", enc("DeReg", "Success"), "2021-01-01 16:00:00");     // 요청하지 않은 시간

        Map<StatHour, Map<StatKey, StatCounts>> r = aggregator.aggregate(DAY, List.of(H14, H15));

        assertThat(r).containsOnlyKeys(H14, H15);
        Map<StatKey, StatCounts> h14 = r.get(H14);
        assertThat(h14).containsOnlyKeys(new StatKey(1, "kbstar"), new StatKey(1, "kbpay"), new StatKey(2, "testsvc"));
        StatCounts kbstar = h14.get(new StatKey(1, "kbstar"));
        assertThat(kbstar.getAuthS()).isEqualTo(1);
        assertThat(kbstar.getAuthF()).isEqualTo(1);
        assertThat(h14.get(new StatKey(1, "kbpay")).getRegS()).isEqualTo(1);
        StatCounts empty = h14.get(new StatKey(2, "testsvc"));
        assertThat(empty.getAuthS() + empty.getAuthF() + empty.getRegS() + empty.getTcS()).isZero();
        assertThat(r.get(H15).get(new StatKey(1, "kbstar")).getTcS()).isEqualTo(1);
    }

    @Test void 없는_날짜_테이블은_빈_결과다() {
        assertThat(aggregator.aggregate(LocalDate.of(1999, 1, 1), List.of(StatHour.parse("1999/01/01/00")))).isEmpty();
    }

    @Test void 같은_시간을_두번_써도_한번만_쌓인다() {
        Map<StatKey, StatCounts> counts = Map.of(
            new StatKey(1, "kbstar"), new StatCounts().add(LogClassifier.Kind.AUTH, true),
            new StatKey(1, "kbpay"), new StatCounts().add(LogClassifier.Kind.REG, false));

        assertThat(writer.write(H14, counts)).isEqualTo(2);
        assertThat(writer.write(H14, counts)).isZero();

        assertThat(rows("2021/01/01/14")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT AUTH_S FROM FIDO_STATISTICS WHERE GROUPBY = '2021/01/01/14' AND SERVICE_NAME = 'kbstar'",
            Long.class)).isEqualTo(1L);
        assertThat(writer.loadedGroupbys(StatHour.parse("2021/01/01/00"), StatHour.parse("2021/01/01/23")))
            .containsExactly("2021/01/01/14");
    }

    @Test void 다른_회사만_있으면_그_회사만_추가한다() {
        writer.write(H14, Map.of(new StatKey(1, "kbstar"), new StatCounts().add(LogClassifier.Kind.AUTH, true)));

        int added = writer.write(H14, Map.of(
            new StatKey(1, "kbstar"), new StatCounts().add(LogClassifier.Kind.AUTH, true),
            new StatKey(2, "testsvc"), new StatCounts().add(LogClassifier.Kind.REG, true)));

        assertThat(added).isEqualTo(1);
        assertThat(rows("2021/01/01/14")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FIDO_STATISTICS WHERE GROUPBY = '2021/01/01/14' AND COMPANY_IDX = 1",
            Integer.class)).isEqualTo(1);
    }

    private int rows(String groupby) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM FIDO_STATISTICS WHERE GROUPBY = ?", Integer.class, groupby);
    }
}
