package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_STATISTICS_FILTER — PK 없음. 논리 식별자 4개 컬럼을 복합키로 매핑. ERD 컬럼 5개. */
@Entity
@Table(name = "CCFA_STATISTICS_FILTER")
@Getter @Setter @NoArgsConstructor
public class CcfaStatisticsFilter {
    @EmbeddedId private CcfaStatisticsFilterId id;
    @Column(name = "TYPE", length = 20) private String type;
}
