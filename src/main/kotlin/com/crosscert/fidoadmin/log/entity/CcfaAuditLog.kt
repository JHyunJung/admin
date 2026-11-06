package com.crosscert.fidoadmin.log.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_AUDIT_LOG — 어드민 감사로그(append-only). 컬럼 11개. INTERGRITY_HASH 는 원본 컬럼명 오타 그대로. */
@Entity
@Table(name = "CCFA_AUDIT_LOG")
class CcfaAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaAuditLogSeq")
    @SequenceGenerator(name = "ccfaAuditLogSeq", sequenceName = "CCFA_AUDIT_LOG_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "COMPANY_NAME", length = 512) var companyName: String? = null
    @Column(name = "TYPE", length = 32) var type: String? = null
    @Column(name = "USER_ID", length = 64) var userId: String? = null
    @Column(name = "USER_NAME", length = 32) var userName: String? = null
    @Column(name = "MESSAGE", length = 4000) var message: String? = null
    @Column(name = "IP", length = 15) var ip: String? = null
    @Column(name = "UA", length = 2048) var ua: String? = null
    @Column(name = "INTERGRITY_HASH", length = 512) var intergrityHash: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
}
