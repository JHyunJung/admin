package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** FIDO_STATISTICS_BAK — 통계 백업본. PK 없음. 컬럼 12개. FidoStatisticsId 를 재사용한다. */
@Entity
@Table(name = "FIDO_STATISTICS_BAK")
class FidoStatisticsBak {
    @EmbeddedId var id: FidoStatisticsId? = null
    @Column(name = "AUTH_S") var authS: Long? = null
    @Column(name = "AUTH_F") var authF: Long? = null
    @Column(name = "TC_S") var tcS: Long? = null
    @Column(name = "TC_F") var tcF: Long? = null
    @Column(name = "REG_S") var regS: Long? = null
    @Column(name = "REG_F") var regF: Long? = null
    @Column(name = "DEREG_S") var deregS: Long? = null
    @Column(name = "DEREG_F") var deregF: Long? = null
}
