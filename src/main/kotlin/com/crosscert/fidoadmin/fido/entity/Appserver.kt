package com.crosscert.fidoadmin.fido.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** APPSERVER — 앱 서버(연동 주체) 정보. 컬럼 8개. */
@Entity
@Table(name = "APPSERVER")
class Appserver {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appserverSeq")
    @SequenceGenerator(name = "appserverSeq", sequenceName = "APPSERVER_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "MEMBER_CODE", length = 32) var memberCode: String? = null
    @Column(name = "MEMBER_ID", length = 32) var memberId: String? = null
    @Column(name = "TYPE", length = 10) var type: String? = null
    @Column(name = "NOTE", length = 128) var note: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
