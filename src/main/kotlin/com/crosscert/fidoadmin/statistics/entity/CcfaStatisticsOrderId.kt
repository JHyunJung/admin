package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.Objects

@Embeddable
class CcfaStatisticsOrderId : Serializable {
    @Column(name = "STATISTICS_IDX") var statisticsIdx: Long? = null
    @Column(name = "COLUMN_NAME", length = 128) var columnName: String? = null

    /** Lombok @EqualsAndHashCode 와 같은 의미: 같은 타입이고 두 필드가 모두 같으면 같다. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CcfaStatisticsOrderId) return false
        return statisticsIdx == other.statisticsIdx && columnName == other.columnName
    }

    override fun hashCode(): Int = Objects.hash(statisticsIdx, columnName)
}
