package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaOption
import jakarta.validation.constraints.NotBlank

/** CCFA_OPTION(코드 그룹) 입력 폼. */
class OptionForm {
    @field:NotBlank @field:ByteSize(max = 64) var optionName: String? = null
    @field:ByteSize(max = 64) var optionNote: String? = null
    @field:ByteSize(max = 64) var optionTitle: String? = null

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    fun toNewEntity(): CcfaOption {
        val o = CcfaOption()
        applyTo(o)
        return o
    }

    fun applyTo(o: CcfaOption) {
        o.optionName = optionName; o.optionNote = optionNote; o.optionTitle = optionTitle
    }

    companion object {
        @JvmStatic
        fun from(o: CcfaOption): OptionForm = OptionForm().apply {
            optionName = o.optionName; optionNote = o.optionNote; optionTitle = o.optionTitle
        }
    }
}
