package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter @Setter
public class DashboardSearchForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate = LocalDate.now().minusDays(29);
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate = LocalDate.now();
    private Long companyIdx;
    private String serviceName;
    private String groupby;
}
