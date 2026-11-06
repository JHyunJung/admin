package com.crosscert.fidoadmin.fido.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** TRANSACTION_CONFIRMATION — 거래확인(TC) 내용. 컬럼 7개. */
@Entity
@Table(name = "TRANSACTION_CONFIRMATION")
class TransactionConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactionConfirmationSeq")
    @SequenceGenerator(name = "transactionConfirmationSeq", sequenceName = "TRANSACTION_CONFIRMATION_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "USERID", length = 512) var userid: String? = null
    @Column(name = "AAID", length = 64) var aaid: String? = null
    @Column(name = "CONTENTTYPE", length = 128) var contenttype: String? = null
    @Lob @Column(name = "CONTENT") var content: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
}
