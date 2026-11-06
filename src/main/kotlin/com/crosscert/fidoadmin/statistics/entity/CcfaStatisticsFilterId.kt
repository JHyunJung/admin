package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.Objects

@Embeddable
class CcfaStatisticsFilterId : Serializable {
    @Column(name = "STATISTICS_IDX") var statisticsIdx: Long? = null
    @Column(name = "COLUMN_NAME", length = 256) var columnName: String? = null
    @Column(name = "OP", length = 20) var op: String? = null
    @Column(name = "\"VALUE\"", length = 1024) var value: String? = null

    /** Lombok @EqualsAndHashCode 와 같은 의미: 같은 타입이고 네 필드가 모두 같으면 같다. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CcfaStatisticsFilterId) return false
        return statisticsIdx == other.statisticsIdx && columnName == other.columnName &&
            op == other.op && value == other.value
    }

    override fun hashCode(): Int = Objects.hash(statisticsIdx, columnName, op, value)
}
