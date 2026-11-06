package com.crosscert.fidoadmin.fido.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** APPID — RP 앱 등록 정보. 컬럼 10개. */
@Entity
@Table(name = "APPID")
class Appid {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appidSeq")
    @SequenceGenerator(name = "appidSeq", sequenceName = "SEQ_APPID", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "APPID", length = 128) var appid: String? = null
    @Column(name = "MEMO", length = 512) var memo: String? = null
    @Column(name = "STATUS", length = 12) var status: String? = null
    @Column(name = "DEVICE", length = 128) var device: String? = null
    @Column(name = "DEVICE_DEFAULT", length = 1) var deviceDefault: String? = null
    @Column(name = "SERVICENAME", length = 512) var servicename: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
