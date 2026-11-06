package com.crosscert.fidoadmin.manager.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_MANAGER — 운영자 계정. 컬럼 16개. USER_PW 는 SHA-256 hex, 화면 DTO 에 절대 싣지 않는다. */
@Entity
@Table(name = "CCFA_MANAGER")
class CcfaManager {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaManagerSeq")
    @SequenceGenerator(name = "ccfaManagerSeq", sequenceName = "CCFA_MANAGER_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "USER_ID", length = 64) var userId: String? = null
    @Column(name = "USER_PW", length = 128) var userPw: String? = null
    @Column(name = "USER_NM", length = 50) var userNm: String? = null
    @Column(name = "USER_EMAIL", length = 256) var userEmail: String? = null
    @Column(name = "USER_PHONE", length = 20) var userPhone: String? = null
    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "STATUS", length = 20) var status: String? = null
    @Column(name = "LOGIN", length = 20) var login: String? = null
    @Column(name = "BLOCK_TIME") var blockTime: LocalDateTime? = null
    @Column(name = "LAST_ACCESS") var lastAccess: LocalDateTime? = null
    @Column(name = "ETC", length = 2048) var etc: String? = null
    @Column(name = "ALRAM_TYPE", length = 32) var alramType: String? = null
    @Column(name = "ALRAM_LEVEL", length = 20) var alramLevel: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null

    /** Lombok `@ToString(exclude = "userPw")` 과 같은 형식. USER_PW 는 싣지 않는다. */
    override fun toString(): String =
        "CcfaManager(idx=$idx, userId=$userId, userNm=$userNm, userEmail=$userEmail, userPhone=$userPhone, " +
            "companyIdx=$companyIdx, status=$status, login=$login, blockTime=$blockTime, lastAccess=$lastAccess, " +
            "etc=$etc, alramType=$alramType, alramLevel=$alramLevel, createdtime=$createdtime, updatedtime=$updatedtime)"
}
