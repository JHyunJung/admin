package com.crosscert.fidoadmin.dashboard

import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat

class DashboardSearchForm {
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) var fromDate: LocalDate? = LocalDate.now().minusDays(29)
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) var toDate: LocalDate? = LocalDate.now()
    // companyIdx 는 두지 않는다 — 테넌트는 세션이 정하며(StatisticsQueryService 가 TenantContext 를 쓴다) 검색 조건이 아니다.
    var serviceName: String? = null
}
