package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaOptions
import jakarta.validation.constraints.NotBlank

/** CCFA_OPTIONS(코드) 인라인 추가 폼. OPTION_IDX 는 경로에서 오므로 폼에 없다. */
class OptionItemForm {
    @field:NotBlank @field:ByteSize(max = 128) var optionValue: String? = null
    @field:ByteSize(max = 128) var optionTitle: String? = null
    @field:ByteSize(max = 128) var optionNote: String? = null

    fun toNewEntity(): CcfaOptions {
        val i = CcfaOptions()
        i.optionValue = optionValue; i.optionTitle = optionTitle; i.optionNote = optionNote
        return i
    }
}
