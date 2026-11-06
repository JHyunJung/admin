package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** CCFA_STATISTICS_ORDER — 통계 정렬 정의. PK 없음. 컬럼 3개. */
@Entity
@Table(name = "CCFA_STATISTICS_ORDER")
class CcfaStatisticsOrder {
    @EmbeddedId var id: CcfaStatisticsOrderId? = null
    @Column(name = "TYPE", length = 20) var type: String? = null
}
