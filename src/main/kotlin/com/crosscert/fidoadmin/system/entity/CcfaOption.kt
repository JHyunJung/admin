package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/** CCFA_OPTION — 코드 그룹. 컬럼 4개. */
@Entity
@Table(name = "CCFA_OPTION")
class CcfaOption {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaOptionSeq")
    @SequenceGenerator(name = "ccfaOptionSeq", sequenceName = "CCFA_OPTION_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "OPTION_NAME", length = 64) var optionName: String? = null
    @Column(name = "OPTION_NOTE", length = 64) var optionNote: String? = null
    @Column(name = "OPTION_TITLE", length = 64) var optionTitle: String? = null
}
