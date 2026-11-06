package com.crosscert.fidoadmin.fido.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** USERINFO — FIDO 등록 사용자 인증정보(크리덴셜). 컬럼 15개. PUBKEY/CERTIFICATE 는 민감정보. */
@Entity
@Table(name = "USERINFO")
class Userinfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "userinfoSeq")
    @SequenceGenerator(name = "userinfoSeq", sequenceName = "USERINFO_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "BIO_TYPE") var bioType: Long? = null
    @Column(name = "SERVICENAME", length = 512) var servicename: String? = null
    @Column(name = "USERID", length = 512) var userid: String? = null
    @Column(name = "AAID", length = 128) var aaid: String? = null
    @Column(name = "AUTHENTICATORVERSION") var authenticatorversion: Long? = null
    @Column(name = "KEYID", length = 1024) var keyid: String? = null
    @Column(name = "PUBKEY", length = 2048) var pubkey: String? = null
    @Column(name = "CERTIFICATE", length = 2048) var certificate: String? = null
    @Column(name = "SIGNCOUNTER") var signcounter: Long? = null
    @Column(name = "UVS", length = 64) var uvs: String? = null
    @Column(name = "STATUS", length = 20) var status: String? = null
    @Column(name = "REGTIME") var regtime: LocalDateTime? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null

    /** Lombok `@ToString(exclude = {"pubkey", "certificate"})` 과 같은 형식. 민감 컬럼은 싣지 않는다. */
    override fun toString(): String =
        "Userinfo(idx=$idx, companyIdx=$companyIdx, bioType=$bioType, servicename=$servicename, userid=$userid, " +
            "aaid=$aaid, authenticatorversion=$authenticatorversion, keyid=$keyid, signcounter=$signcounter, " +
            "uvs=$uvs, status=$status, regtime=$regtime, createdtime=$createdtime)"
}
