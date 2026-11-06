package com.crosscert.fidoadmin.company.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_LICENSE — 라이선스. 컬럼 13개. */
@Entity
@Table(name = "CCFA_LICENSE")
class CcfaLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaLicenseSeq")
    @SequenceGenerator(name = "ccfaLicenseSeq", sequenceName = "CCFA_LICENSE_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "COMPANY_NAME", length = 256) var companyName: String? = null
    @Column(name = "CONTACT_NAME", length = 128) var contactName: String? = null
    @Column(name = "CONTACT_PHONE", length = 128) var contactPhone: String? = null
    @Column(name = "CONTACT_EMAIL", length = 256) var contactEmail: String? = null
    @Column(name = "SERVICE_NAME", length = 256) var serviceName: String? = null
    @Column(name = "ETC", length = 1024) var etc: String? = null
    @Column(name = "LICENSE", length = 2048) var license: String? = null
    @Column(name = "FILE_PATH", length = 256) var filePath: String? = null
    @Column(name = "HASHVALUE", length = 256) var hashvalue: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
