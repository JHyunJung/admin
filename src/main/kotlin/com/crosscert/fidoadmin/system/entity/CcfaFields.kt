package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/** CCFA_FIELDS — 화면 필드 메타 정의. 컬럼 9개. */
@Entity
@Table(name = "CCFA_FIELDS")
class CcfaFields {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaFieldsSeq")
    @SequenceGenerator(name = "ccfaFieldsSeq", sequenceName = "CCFA_FIELDS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "FIELD_TABLE", length = 128) var fieldTable: String? = null
    @Column(name = "FIELD_NAME", length = 256) var fieldName: String? = null
    @Column(name = "FIELD_TYPE", length = 128) var fieldType: String? = null
    @Column(name = "FIELD_TITLE", length = 128) var fieldTitle: String? = null
    @Column(name = "PK") var pk: Long? = null
    @Column(name = "FK") var fk: Long? = null
    @Column(name = "OPTION_IDX") var optionIdx: Long? = null
    @Column(name = "EDITABLE") var editable: Long? = null
}
