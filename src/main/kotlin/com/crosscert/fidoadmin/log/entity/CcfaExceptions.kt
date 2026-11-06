package com.crosscert.fidoadmin.log.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/**
 * CCFA_EXCEPTIONS — 예외 로그(append-only). 컬럼 8개.
 * CREATEDTIME 은 다른 테이블과 달리 VARCHAR2(64) 라 String 으로 둔다.
 */
@Entity
@Table(name = "CCFA_EXCEPTIONS")
class CcfaExceptions {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaExceptionsSeq")
    @SequenceGenerator(name = "ccfaExceptionsSeq", sequenceName = "CCFA_EXCEPTIONS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "E_TYPE", length = 32) var eType: String? = null
    @Column(name = "E_LEVEL", length = 32) var eLevel: String? = null
    @Column(name = "EXCEPTION_MESSAGE", length = 4000) var exceptionMessage: String? = null
    @Column(name = "EXCEPTION_DETAIL_MESSAGE", length = 4000) var exceptionDetailMessage: String? = null
    @Lob @Column(name = "EXCEPTION_DATA") var exceptionData: String? = null
    @Column(name = "CREATEDTIME", length = 64) var createdtime: String? = null
}
