package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDateTime
import java.util.Objects

@Embeddable
class FidoStatisticsId : Serializable {
    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "SERVICE_NAME", length = 512) var serviceName: String? = null
    @Column(name = "GROUPBY", length = 32) var groupby: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null

    /** Lombok @EqualsAndHashCode 와 같은 의미: 같은 타입이고 네 필드가 모두 같으면 같다. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FidoStatisticsId) return false
        return companyIdx == other.companyIdx && serviceName == other.serviceName &&
            groupby == other.groupby && createdtime == other.createdtime
    }

    override fun hashCode(): Int = Objects.hash(companyIdx, serviceName, groupby, createdtime)
}
