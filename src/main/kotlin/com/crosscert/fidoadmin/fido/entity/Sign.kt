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

/** SIGN — 전자서명 원본/서명값. 컬럼 11개. */
@Entity
@Table(name = "SIGN")
class Sign {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "signSeq")
    @SequenceGenerator(name = "signSeq", sequenceName = "SIGN_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "USERID", length = 512) var userid: String? = null
    @Column(name = "ASSERTION", length = 4000) var assertion: String? = null
    @Column(name = "DN", length = 200) var dn: String? = null
    @Lob @Column(name = "PLAINTEXT") var plaintext: String? = null
    @Column(name = "DATA", length = 2000) var data: String? = null
    @Column(name = "SIGNATURE", length = 1000) var signature: String? = null
    @Column(name = "MEMO", length = 64) var memo: String? = null
    @Column(name = "DEL", length = 1) var del: String? = null
    @Column(name = "CREATETIME") var createtime: LocalDateTime? = null
}
