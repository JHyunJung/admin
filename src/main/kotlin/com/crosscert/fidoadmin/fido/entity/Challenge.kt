package com.crosscert.fidoadmin.fido.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CHALLENGE — 인증 챌린지(일회성). 컬럼 8개. */
@Entity
@Table(name = "CHALLENGE")
class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "challengeSeq")
    @SequenceGenerator(name = "challengeSeq", sequenceName = "CHALLENGE_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "USERID", length = 512) var userid: String? = null
    @Column(name = "SERVICENAME", length = 512) var servicename: String? = null
    @Column(name = "CHALLENGECODE", length = 140) var challengecode: String? = null
    @Column(name = "CREATETIME") var createtime: LocalDateTime? = null
    @Column(name = "LOGIDX") var logidx: Long? = null
    @Column(name = "UV", length = 20) var uv: String? = null
}
