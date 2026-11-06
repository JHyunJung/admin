package com.crosscert.fidoadmin.log.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/** CCFA_MAILING — 메일/SMS 발송 큐. 컬럼 10개. TO 는 예약어라 따옴표 필수. */
@Entity
@Table(name = "CCFA_MAILING")
class CcfaMailing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaMailingSeq")
    @SequenceGenerator(name = "ccfaMailingSeq", sequenceName = "CCFA_MAILING_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "\"TO\"", length = 4000) var to: String? = null
    @Column(name = "SUBJECT", length = 256) var subject: String? = null
    @Column(name = "CONTENT", length = 4000) var content: String? = null
    @Column(name = "STATUS", length = 256) var status: String? = null
    @Column(name = "SMS_TO", length = 4000) var smsTo: String? = null
    @Column(name = "SMS_CONTENT", length = 300) var smsContent: String? = null
    @Column(name = "SMS_STATUS", length = 512) var smsStatus: String? = null

    /** DB 상 VARCHAR2(64). 문자열 그대로 둔다. */
    @Column(name = "SENDTIME", length = 64) var sendtime: String? = null
}
