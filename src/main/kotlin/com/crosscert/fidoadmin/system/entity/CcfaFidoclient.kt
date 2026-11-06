package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_FIDOCLIENT — FIDO 서버 연동 정보. PK 없음(논리 식별자 SERVERCODE). 컬럼 6개. */
@Entity
@Table(name = "CCFA_FIDOCLIENT")
class CcfaFidoclient {
    @Id @Column(name = "SERVERCODE", length = 64) var servercode: String? = null
    @Column(name = "SERVERNAME", length = 1024) var servername: String? = null
    @Column(name = "SERVERURL", length = 2048) var serverurl: String? = null
    @Column(name = "STATUS", length = 4) var status: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
