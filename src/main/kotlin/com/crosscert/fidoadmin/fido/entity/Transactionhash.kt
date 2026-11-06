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

/** TRANSACTIONHASH — 거래내용 해시. 컬럼 6개. */
@Entity
@Table(name = "TRANSACTIONHASH")
class Transactionhash {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactionhashSeq")
    @SequenceGenerator(name = "transactionhashSeq", sequenceName = "TRANSACTIONHASH_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "USERID", length = 512) var userid: String? = null
    @Lob @Column(name = "CONTENT") var content: String? = null
    @Column(name = "CONTENTHASH", length = 64) var contenthash: String? = null
    @Column(name = "CREATETIME") var createtime: LocalDateTime? = null
}
