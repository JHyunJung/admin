package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_SYSTEM_INFO — key-value. 컬럼 3개. */
@Entity
@Table(name = "CCFA_SYSTEM_INFO")
class CcfaSystemInfo {
    @Id @Column(name = "PROP_KEY", length = 128) var propKey: String? = null
    @Column(name = "PROP_VALUE", length = 1024) var propValue: String? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
