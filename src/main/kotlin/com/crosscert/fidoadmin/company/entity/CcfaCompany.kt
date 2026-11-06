package com.crosscert.fidoadmin.company.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_COMPANY — 고객사(테넌트). 컬럼 19개. IDX 0 은 전역(시스템) 레코드. */
@Entity
@Table(name = "CCFA_COMPANY")
class CcfaCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaCompanySeq")
    @SequenceGenerator(name = "ccfaCompanySeq", sequenceName = "CCFA_COMPANY_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_NAME", length = 256) var companyName: String? = null
    @Column(name = "COMPANY_TYPE", length = 20) var companyType: String? = null
    @Column(name = "VENDOR_CODE", length = 20) var vendorCode: String? = null
    @Column(name = "CONTACT", length = 512) var contact: String? = null
    @Column(name = "CONTACT_PHONE", length = 50) var contactPhone: String? = null
    @Column(name = "CONTACT_PHONE2", length = 50) var contactPhone2: String? = null
    @Column(name = "CONTACT_ADDR", length = 2048) var contactAddr: String? = null
    @Column(name = "ENABLE_TYPE", length = 20) var enableType: String? = null
    @Column(name = "STARTTIME") var starttime: LocalDateTime? = null
    @Column(name = "ENDTIME") var endtime: LocalDateTime? = null
    @Column(name = "MAX_APPID") var maxAppid: Long? = null
    @Column(name = "MAX_APPSERVER") var maxAppserver: Long? = null
    @Column(name = "MAX_USER") var maxUser: Long? = null
    @Column(name = "ETC", length = 4000) var etc: String? = null
    @Column(name = "CREATOR") var creator: Long? = null
    @Column(name = "UPDATOR") var updator: Long? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
