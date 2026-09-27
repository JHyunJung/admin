package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import java.sql.Timestamp;
import java.time.LocalDate;
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
 * GROUPBY 값은 운영 데이터에 따라 다르므로 화면에서 고르게 하고, 없으면 첫 값을 쓴다.
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

    /**
     * 집계 단위 목록. COMPANY 역할에게는 자기 고객사에 존재하는 값만 보여준다.
     * 전역 조회로 두면 다른 고객사의 집계 단위가 노출되고, 첫 값을 기본으로 고르는 탓에
     * 자기 데이터가 있는데도 0 으로 보이는 문제가 생긴다.
     */
    @Transactional(readOnly = true)
    public List<String> groupbys() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", tenant.companyIdx());
        return jdbc.queryForList(
            "SELECT DISTINCT GROUPBY FROM FIDO_STATISTICS"
                + " WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY GROUPBY",
            p, String.class);
    }

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

    /**
     * 같은 조건(집계 단위·서비스)에서 가장 최근 집계일. 기간과 무관하다.
     * 그래프가 비었을 때 "데이터가 언제까지 있는지", 배치가 멈췄는지를 알려 주는 데 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastStatDate(DashboardSearchForm f) {
        Map<String, Object> p = new HashMap<>();
        p.put("groupby", f.getGroupby());
        p.put("companyIdx", tenant.companyIdx());
        p.put("serviceName", f.getServiceName() == null || f.getServiceName().isBlank() ? null : f.getServiceName());
        Timestamp last = jdbc.queryForObject("""
            SELECT MAX(CREATEDTIME) FROM FIDO_STATISTICS
             WHERE UPPER(GROUPBY) = UPPER(:groupby)
               AND (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)
               AND (:serviceName IS NULL OR SERVICE_NAME = :serviceName)
            """, p, Timestamp.class);
        return Optional.ofNullable(last).map(t -> t.toLocalDateTime().toLocalDate());
    }

    @Transactional(readOnly = true)
    public List<DailyStat> daily(DashboardSearchForm f) {
        Long companyIdx = tenant.companyIdx();
        Range range = rangeOf(f);
        LocalDate from = range.from();
        LocalDate to = range.to();
        Map<String, Object> p = new HashMap<>();
        p.put("groupby", f.getGroupby());
        p.put("from", Timestamp.valueOf(from.atStartOfDay()));
        p.put("to", Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        p.put("companyIdx", companyIdx);
        p.put("serviceName", f.getServiceName() == null || f.getServiceName().isBlank() ? null : f.getServiceName());
        String sql = """
            SELECT TRUNC(CREATEDTIME) AS D,
                   NVL(SUM(AUTH_S),0) AUTH_S, NVL(SUM(AUTH_F),0) AUTH_F, NVL(SUM(TC_S),0) TC_S, NVL(SUM(TC_F),0) TC_F,
                   NVL(SUM(REG_S),0) REG_S, NVL(SUM(REG_F),0) REG_F, NVL(SUM(DEREG_S),0) DEREG_S, NVL(SUM(DEREG_F),0) DEREG_F
              FROM FIDO_STATISTICS
             -- 운영 데이터의 GROUPBY 표기가 섞여 있어도(day/DAY) 같은 단위로 본다
             WHERE UPPER(GROUPBY) = UPPER(:groupby)
               AND CREATEDTIME >= :from AND CREATEDTIME < :to
               AND (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)
               AND (:serviceName IS NULL OR SERVICE_NAME = :serviceName)
             GROUP BY TRUNC(CREATEDTIME)
             ORDER BY 1
            """;
        return jdbc.query(sql, p, (rs, i) -> new DailyStat(
            rs.getTimestamp("D").toLocalDateTime().toLocalDate(),
            rs.getLong("AUTH_S"), rs.getLong("AUTH_F"), rs.getLong("TC_S"), rs.getLong("TC_F"),
            rs.getLong("REG_S"), rs.getLong("REG_F"), rs.getLong("DEREG_S"), rs.getLong("DEREG_F")));
    }
}
