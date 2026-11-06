package com.crosscert.fidoadmin.manager.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_MANAGER_PW_POLICY — 운영자 비밀번호 정책/잠금 상태. 컬럼 6개. */
@Entity
@Table(name = "CCFA_MANAGER_PW_POLICY")
class CcfaManagerPwPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaManagerPwPolicySeq")
    @SequenceGenerator(name = "ccfaManagerPwPolicySeq", sequenceName = "CCFA_MANAGER_PW_POLICY_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "USER_ID", length = 64) var userId: String? = null
    @Column(name = "ACCOUNT_LOCK", length = 1) var accountLock: String? = null
    @Column(name = "PW_FAIL_CNT") var pwFailCnt: Long? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
