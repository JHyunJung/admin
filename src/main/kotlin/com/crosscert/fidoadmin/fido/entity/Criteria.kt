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

/** CRITERIA — 인증기기 정책 기준(UAF). 컬럼 17개. */
@Entity
@Table(name = "CRITERIA")
class Criteria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "criteriaSeq")
    @SequenceGenerator(name = "criteriaSeq", sequenceName = "CRITERIA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "AAID", length = 64) var aaid: String? = null
    @Column(name = "VENDORIDS", length = 32) var vendorids: String? = null
    @Column(name = "USERVERIFICATION") var userverification: Long? = null
    @Column(name = "KEYPROTECTION") var keyprotection: Long? = null
    @Column(name = "MATCHERPROTECTION") var matcherprotection: Long? = null
    @Column(name = "ATTACHMENTHNUMBER") var attachmenthnumber: Long? = null
    @Column(name = "TCDISPLAY") var tcdisplay: Long? = null
    @Column(name = "TCDISPLAYCONTENTTYPE", length = 128) var tcdisplaycontenttype: String? = null
    @Column(name = "AUTHENTICATIONALGORITHMS", length = 64) var authenticationalgorithms: String? = null
    @Column(name = "ASSERTIONSCHEMES", length = 64) var assertionschemes: String? = null
    @Column(name = "ATTESTATIONTYPES", length = 64) var attestationtypes: String? = null
    @Column(name = "AUTHENTICATORVERSION") var authenticatorversion: Long? = null
    @Column(name = "METAHASH", length = 512) var metahash: String? = null
    @Lob @Column(name = "JSONDATA") var jsondata: String? = null
    @Column(name = "CREATETIME") var createtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
