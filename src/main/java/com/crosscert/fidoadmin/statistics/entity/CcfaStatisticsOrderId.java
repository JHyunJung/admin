package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class CcfaStatisticsOrderId implements Serializable {
    @Column(name = "STATISTICS_IDX") private Long statisticsIdx;
    @Column(name = "COLUMN_NAME", length = 128) private String columnName;
}
