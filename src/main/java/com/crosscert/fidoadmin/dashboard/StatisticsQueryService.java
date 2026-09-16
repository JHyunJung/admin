package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import java.sql.Timestamp;
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

    @Transactional(readOnly = true)
    public List<String> groupbys() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT GROUPBY FROM FIDO_STATISTICS ORDER BY GROUPBY", String.class);
    }

    @Transactional(readOnly = true)
    public List<String> serviceNames(Long companyIdx) {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", companyIdx);
        return jdbc.queryForList(
            "SELECT DISTINCT SERVICE_NAME FROM FIDO_STATISTICS WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY SERVICE_NAME",
            p, String.class);
    }

    @Transactional(readOnly = true)
    public List<DailyStat> daily(DashboardSearchForm f) {
        Long companyIdx = TenantContext.isSuper() ? f.getCompanyIdx() : TenantContext.companyIdx();
        Map<String, Object> p = new HashMap<>();
        p.put("groupby", f.getGroupby());
        p.put("from", Timestamp.valueOf(f.getFromDate().atStartOfDay()));
        p.put("to", Timestamp.valueOf(f.getToDate().plusDays(1).atStartOfDay()));
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
