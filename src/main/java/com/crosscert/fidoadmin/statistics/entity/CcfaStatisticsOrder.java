package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_STATISTICS_ORDER — 통계 정렬 정의. PK 없음. ERD 컬럼 3개. */
@Entity
@Table(name = "CCFA_STATISTICS_ORDER")
@Getter @Setter @NoArgsConstructor
public class CcfaStatisticsOrder {
    @EmbeddedId private CcfaStatisticsOrderId id;
    @Column(name = "TYPE", length = 20) private String type;
}
