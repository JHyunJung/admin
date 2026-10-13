package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter @Setter
public class DashboardSearchForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate = LocalDate.now().minusDays(29);
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate = LocalDate.now();
    // companyIdx 는 두지 않는다 — 테넌트는 세션이 정하며(StatisticsQueryService 가 TenantContext 를 쓴다) 검색 조건이 아니다.
    private String serviceName;
    private String groupby;
}
