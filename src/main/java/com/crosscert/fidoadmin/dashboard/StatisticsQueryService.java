package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIDO_STATISTICS 를 일자별로 합산한다. 읽기 전용 SQL 만 쓴다.
 *
 * <p>GROUPBY 는 집계한 로그의 한 시간 구간({@code yyyy/MM/dd/HH}, 24시간제)이다. 날짜는 GROUPBY 앞 10자리로 정한다 —
 * CREATEDTIME 은 적재 시각(로그 시각 + 약 하루)이라 날짜 기준으로 쓸 수 없다. 고정 길이 문자열이라
 * 사전순 비교가 시간순과 같으므로 범위 조건을 문자열 그대로 건다.
 *
 * <p>각 쿼리의 {@code (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)} 절 중
 * {@code IS NULL} 쪽은 이제 도달할 수 없다. {@link TenantContext#companyIdx()} 는
 * 유효 테넌트가 없으면 null 을 돌려주지 않고 예외를 던지므로, 이 서비스로 들어오는
 * {@code companyIdx} 파라미터는 항상 값이 있다. 즉 전역(테넌트 미지정) 집계 경로는
 * 이미 막혀 있다. SQL 자체는 그대로 둔다 — 다시 쓰면 회귀 위험만 지고 얻는 것이 없다.
 */
@Service
@RequiredArgsConstructor
public class StatisticsQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    /** 최대 조회 기간. 지나치게 넓은 범위로 전 기간을 훑지 않도록 제한한다. */
    static final int MAX_RANGE_DAYS = 366;

    /** 유효 테넌트가 유일한 출처이므로 파라미터로 받지 않는다. */
    @Transactional(readOnly = true)
    public List<String> serviceNames() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", tenant.companyIdx());
        return jdbc.queryForList(
            "SELECT DISTINCT SERVICE_NAME FROM FIDO_STATISTICS WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY SERVICE_NAME",
            p, String.class);
    }

    /** 실제 조회 기간. 화면 안내 문구도 이 값을 쓴다(폼 값과 다를 수 있다). */
    public record Range(LocalDate from, LocalDate to) {}

    /**
     * 빈 값(?fromDate=)은 필드 기본값을 덮어써 null 이 되므로 여기서 다시 채운다.
     * 뒤집힌 범위는 빈 결과 대신 정상 범위로 바로잡고, 과도하게 넓은 범위는 제한한다.
     */
    public static Range rangeOf(DashboardSearchForm f) {
        LocalDate to = f.getToDate() == null ? LocalDate.now() : f.getToDate();
        LocalDate from = f.getFromDate() == null ? to.minusDays(29) : f.getFromDate();
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }
        if (from.isBefore(to.minusDays(MAX_RANGE_DAYS))) {
            from = to.minusDays(MAX_RANGE_DAYS);
        }
        return new Range(from, to);
    }

    /** GROUPBY 날짜 키. {@code 2026/10/15/00} 처럼 그날 0시. */
    static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy/MM/dd/'00'");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 같은 조건(서비스)에서 가장 최근 집계일(로그 날짜). 기간과 무관하다.
     * 그래프가 비었을 때 "데이터가 언제까지 있는지", 배치가 멈췄는지를 알려 주는 데 쓴다.
     * 시간 형식이 아닌 옛 GROUPBY(예: day)는 뺀다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastStatDate(DashboardSearchForm f) {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", tenant.companyIdx());
        p.put("serviceName", serviceName(f));
        String last = jdbc.queryForObject("""
            SELECT MAX(GROUPBY) FROM FIDO_STATISTICS
             WHERE REGEXP_LIKE(GROUPBY, '^[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9]{2}$')
               AND (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)
               AND (:serviceName IS NULL OR SERVICE_NAME = :serviceName)
            """, p, String.class);
        return Optional.ofNullable(last).map(g -> LocalDate.parse(g.substring(0, 10), DAY));
    }

    @Transactional(readOnly = true)
    public List<DailyStat> daily(DashboardSearchForm f) {
        Long companyIdx = tenant.companyIdx();
        Range range = rangeOf(f);
        LocalDate from = range.from();
        LocalDate to = range.to();
        Map<String, Object> p = new HashMap<>();
        p.put("fromKey", from.format(DAY_KEY));
        p.put("toKey", to.plusDays(1).format(DAY_KEY));
        p.put("companyIdx", companyIdx);
        p.put("serviceName", serviceName(f));
        String sql = """
            SELECT TO_DATE(SUBSTR(GROUPBY,1,10),'YYYY/MM/DD') AS D,
                   NVL(SUM(AUTH_S),0) AUTH_S, NVL(SUM(AUTH_F),0) AUTH_F, NVL(SUM(TC_S),0) TC_S, NVL(SUM(TC_F),0) TC_F,
                   NVL(SUM(REG_S),0) REG_S, NVL(SUM(REG_F),0) REG_F, NVL(SUM(DEREG_S),0) DEREG_S, NVL(SUM(DEREG_F),0) DEREG_F
              FROM FIDO_STATISTICS
             -- 'yyyy/MM/dd/HH' 의 사전순 = 시간순. day 같은 옛 값은 이 범위에 들어오지 않는다('d' > '2').
             WHERE GROUPBY >= :fromKey AND GROUPBY < :toKey
               AND (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)
               AND (:serviceName IS NULL OR SERVICE_NAME = :serviceName)
             GROUP BY TO_DATE(SUBSTR(GROUPBY,1,10),'YYYY/MM/DD')
             ORDER BY 1
            """;
        return jdbc.query(sql, p, (rs, i) -> new DailyStat(
            rs.getTimestamp("D").toLocalDateTime().toLocalDate(),
            rs.getLong("AUTH_S"), rs.getLong("AUTH_F"), rs.getLong("TC_S"), rs.getLong("TC_F"),
            rs.getLong("REG_S"), rs.getLong("REG_F"), rs.getLong("DEREG_S"), rs.getLong("DEREG_F")));
    }

    private static String serviceName(DashboardSearchForm f) {
        return f.getServiceName() == null || f.getServiceName().isBlank() ? null : f.getServiceName();
    }
}
