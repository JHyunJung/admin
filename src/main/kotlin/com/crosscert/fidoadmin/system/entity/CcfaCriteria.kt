package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_CRITERIA — 어드민 인증기기 정책. 컬럼 6개. */
@Entity
@Table(name = "CCFA_CRITERIA")
class CcfaCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaCriteriaSeq")
    @SequenceGenerator(name = "ccfaCriteriaSeq", sequenceName = "CCFA_CRITERIA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "AAID", length = 64) var aaid: String? = null
    @Column(name = "METAHASH", length = 512) var metahash: String? = null
    @Lob @Column(name = "JSONDATA") var jsondata: String? = null
    @Column(name = "CREATETIME") var createtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
