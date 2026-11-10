package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * CCFA_SYSTEM_INFO 입력 폼. propKey 는 등록 시에만 쓰인다.
 * PROP_KEY 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL·리다이렉트에 안전한 문자만 허용한다.
 */
class SystemInfoForm {
    @field:NotBlank @field:ByteSize(max = 128)
    @field:Pattern(regexp = PATH_ID_PATTERN, message = "키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    var propKey: String? = null
    @field:ByteSize(max = 1024) var propValue: String? = null

    fun toNewEntity(): CcfaSystemInfo {
        val i = CcfaSystemInfo()
        i.propKey = propKey?.javaTrim()
        applyTo(i)
        return i
    }

    /** 식별자는 바꾸지 않는다. */
    fun applyTo(i: CcfaSystemInfo) { i.propValue = propValue }

    companion object {
        @JvmStatic
        fun from(i: CcfaSystemInfo): SystemInfoForm = SystemInfoForm().apply {
            propKey = i.propKey; propValue = i.propValue
        }
    }
}
