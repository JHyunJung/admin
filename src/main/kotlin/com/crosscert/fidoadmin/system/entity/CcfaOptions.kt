package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/** CCFA_OPTIONS — 코드 상세(CCFA_OPTION 의 하위 항목). 컬럼 5개. */
@Entity
@Table(name = "CCFA_OPTIONS")
class CcfaOptions {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaOptionsSeq")
    @SequenceGenerator(name = "ccfaOptionsSeq", sequenceName = "CCFA_OPTIONS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "OPTION_IDX") var optionIdx: Long? = null
    @Column(name = "OPTION_VALUE", length = 128) var optionValue: String? = null
    @Column(name = "OPTION_TITLE", length = 128) var optionTitle: String? = null
    @Column(name = "OPTION_NOTE", length = 128) var optionNote: String? = null
}
