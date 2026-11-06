package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaFields
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** CCFA_FIELDS 입력 폼. PK/FK/EDITABLE 은 0/1 플래그를 NUMBER 로 저장한다. */
class FieldForm {
    @field:NotBlank @field:ByteSize(max = 128) var fieldTable: String? = null
    @field:NotBlank @field:ByteSize(max = 256) var fieldName: String? = null
    @field:NotBlank @field:ByteSize(max = 128) var fieldType: String? = null
    @field:ByteSize(max = 128) var fieldTitle: String? = null
    @field:NotNull @field:Min(0) var pk: Long? = 0L
    @field:NotNull @field:Min(0) var fk: Long? = 0L
    @field:NotNull @field:Min(0) var editable: Long? = 0L
    /** 코드 그룹(CCFA_OPTION.IDX). 비우면 null. */
    var optionIdx: Long? = null

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    fun toNewEntity(): CcfaFields {
        val e = CcfaFields()
        applyTo(e)
        return e
    }

    fun applyTo(e: CcfaFields) {
        e.fieldTable = fieldTable; e.fieldName = fieldName; e.fieldType = fieldType; e.fieldTitle = fieldTitle
        e.pk = pk; e.fk = fk; e.editable = editable; e.optionIdx = optionIdx
    }

    companion object {
        @JvmStatic
        fun from(e: CcfaFields): FieldForm = FieldForm().apply {
            fieldTable = e.fieldTable; fieldName = e.fieldName; fieldType = e.fieldType
            fieldTitle = e.fieldTitle; pk = e.pk; fk = e.fk; editable = e.editable
            optionIdx = e.optionIdx
        }
    }
}
