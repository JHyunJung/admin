package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIDO_STATISTICS 를 일자별로 합산한다. 읽기 전용 SQL 만 쓴다.
 * GROUPBY 값은 운영 데이터에 따라 다르므로 화면에서 고르게 하고, 없으면 첫 값을 쓴다.
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

    @Transactional(readOnly = true)
    public List<DailyStat> daily(DashboardSearchForm f) {
        Long companyIdx = tenant.companyIdx();
        // 빈 값(?fromDate=)은 필드 기본값을 덮어써 null 이 되므로 여기서 다시 채운다.
        // 뒤집힌 범위는 빈 결과 대신 정상 범위로 바로잡고, 과도하게 넓은 범위는 제한한다.
        LocalDate to = f.getToDate() == null ? LocalDate.now() : f.getToDate();
        LocalDate from = f.getFromDate() == null ? to.minusDays(29) : f.getFromDate();
        if (from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }
        if (from.isBefore(to.minusDays(MAX_RANGE_DAYS))) {
            from = to.minusDays(MAX_RANGE_DAYS);
        }
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
             WHERE GROUPBY = :groupby
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
