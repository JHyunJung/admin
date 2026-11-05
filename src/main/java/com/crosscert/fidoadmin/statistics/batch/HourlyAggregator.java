package com.crosscert.fidoadmin.statistics.batch;

import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 날짜 테이블 {@code FIDO_LOGS_yyyyMMdd} 를 <b>한 번</b> 읽어 그 날짜의 여러 시간을 (시간, 회사, 서비스)별로 센다.
 *
 * <p>일자 테이블에는 CREATEDTIME 인덱스가 없어(PK 만 있다) 시간마다 읽으면 하루 24번 전체를 훑는다.
 * 그래서 빠진 시간들을 한 쿼리로 읽는다. 트랜잭션·잠금 없이 읽기만 한다.
 */
@Slf4j
@Component
public class HourlyAggregator {

    /** 한 번에 받아 오는 행 수. Oracle 드라이버 기본(10행)으로는 큰 테이블에서 왕복이 너무 많다. */
    static final int FETCH_SIZE = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final FidoLogTable tables;

    public HourlyAggregator(DataSource dataSource, FidoLogTable tables) {
        JdbcTemplate t = new JdbcTemplate(dataSource);
        t.setFetchSize(FETCH_SIZE);
        this.jdbc = new NamedParameterJdbcTemplate(t);
        this.tables = tables;
    }

    /**
     * @param date  날짜 테이블의 날짜
     * @param hours 그 날짜에서 셀 시간들(모두 {@code date} 에 속해야 한다)
     * @return 시간 → (회사, 서비스) → 누적값. 로그가 없는 시간은 키가 없다. 테이블이 없으면 빈 맵
     */
    public Map<StatHour, Map<StatKey, StatCounts>> aggregate(LocalDate date, Collection<StatHour> hours) {
        if (hours.isEmpty()) return Map.of();
        for (StatHour h : hours) {
            if (!h.date().equals(date)) throw new IllegalArgumentException(h.groupby() + " 은(는) " + date + " 이 아닙니다");
        }
        String table = tables.nameFor(date);
        if (!tables.exists(table)) {
            log.info("statistics: {} 테이블이 없어 건너뜁니다", table);
            return Map.of();
        }
        TreeSet<StatHour> sorted = new TreeSet<>(hours);
        Map<String, StatHour> wanted = new HashMap<>();
        for (StatHour h : sorted) wanted.put(h.groupby(), h);

        // 테이블 이름은 FidoLogTable.nameFor 가 LocalDate 로만 만든다(신뢰 경계). 나머지는 모두 바인드 값이다.
        String sql = "SELECT TO_CHAR(CREATEDTIME,'YYYY/MM/DD/HH24') AS GROUPBY, COMPANY_IDX, SERVICENAME, JSONDATA"
            + " FROM " + table
            + " WHERE CREATEDTIME >= :start AND CREATEDTIME < :end"
            + " AND COMPANY_IDX IN (SELECT IDX FROM CCFA_COMPANY)";
        // JdbcTemplate 은 Hibernate jdbc.time_zone 을 쓰지 않는다. Timestamp.valueOf 와 드라이버가 같은 JVM 시간대를 써서
        // 서로 상쇄되므로 벽시계 값 그대로 비교된다.
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("start", Timestamp.valueOf(sorted.first().start()))
            .addValue("end", Timestamp.valueOf(sorted.last().end()));

        Map<StatHour, Map<StatKey, StatCounts>> result = new TreeMap<>();
        Map<StatHour, int[]> broken = new TreeMap<>();
        jdbc.query(sql, p, rs -> {
            StatHour h = wanted.get(rs.getString("GROUPBY"));
            if (h == null) return; // 범위 안이지만 이미 적재된 시간
            StatKey key = new StatKey(rs.getLong("COMPANY_IDX"), rs.getString("SERVICENAME"));
            // 로그가 하나라도 있으면 (회사, 서비스) 행이 생긴다 — 셀 수 없는 op 만 있어도 모두 0 인 행(구 배치와 같다).
            StatCounts counts = result.computeIfAbsent(h, k -> new LinkedHashMap<>()).computeIfAbsent(key, k -> new StatCounts());
            LogClassifier.classify(rs.getString("JSONDATA")).ifPresentOrElse(
                r -> counts.add(r.kind(), r.success()),
                () -> broken.computeIfAbsent(h, k -> new int[1])[0]++);
        });
        broken.forEach((h, n) -> log.warn("statistics: {} 디코드 실패 {}건(세지 않음)", h.groupby(), n[0]));
        return result;
    }
}
