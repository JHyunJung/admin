package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class FidoStatisticsId implements Serializable {
    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERVICE_NAME", length = 512) private String serviceName;
    @Column(name = "GROUPBY", length = 32) private String groupby;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
