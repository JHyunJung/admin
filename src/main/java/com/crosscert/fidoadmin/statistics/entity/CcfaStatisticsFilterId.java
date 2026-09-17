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
public class CcfaStatisticsFilterId implements Serializable {
    @Column(name = "STATISTICS_IDX") private Long statisticsIdx;
    @Column(name = "COLUMN_NAME", length = 256) private String columnName;
    @Column(name = "OP", length = 20) private String op;
    @Column(name = "\"VALUE\"", length = 1024) private String value;
}
