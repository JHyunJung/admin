package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_SYSTEM_PROP — 고객사별 key-value. PK 없음. 컬럼 5개. */
@Entity
@Table(name = "CCFA_SYSTEM_PROP")
class CcfaSystemProp {
    @EmbeddedId var id: CcfaSystemPropId? = null
    @Column(name = "PROP_VALUE", length = 4000) var propValue: String? = null
    @Column(name = "SHARE_TYPE", length = 20) var shareType: String? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
