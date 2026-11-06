package com.crosscert.fidoadmin.log.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** FIDO_LOGS — FIDO 처리 로그(append-only). 컬럼 6개. */
@Entity
@Table(name = "FIDO_LOGS")
class FidoLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fidoLogsSeq")
    @SequenceGenerator(name = "fidoLogsSeq", sequenceName = "FIDO_LOGS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "SERIALCODE", length = 128) var serialcode: String? = null
    @Column(name = "SERVICENAME", length = 512) var servicename: String? = null
    @Lob @Column(name = "JSONDATA") var jsondata: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
}
