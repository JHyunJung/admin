package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** CCFA_ERROR_TABLE — 에러코드 사전. PK 없음(논리 식별자 ERROR_CODE). 컬럼 4개. */
@Entity
@Table(name = "CCFA_ERROR_TABLE")
class CcfaErrorTable {
    @Id @Column(name = "ERROR_CODE", length = 20) var errorCode: String? = null
    @Column(name = "ERROR_MESSAGE", length = 1024) var errorMessage: String? = null
    @Column(name = "ERROR_COMMENT", length = 4000) var errorComment: String? = null
    @Column(name = "ERROR_TYPE", length = 20) var errorType: String? = null
}
