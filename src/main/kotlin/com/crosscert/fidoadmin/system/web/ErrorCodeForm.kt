package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable
import com.crosscert.fidoadmin.system.trimJava
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * CCFA_ERROR_TABLE 입력 폼. errorCode 는 등록 시에만 쓰인다.
 * ERROR_CODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL·리다이렉트에 안전한 문자만 허용한다.
 */
class ErrorCodeForm {
    @field:NotBlank @field:ByteSize(max = 20)
    @field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "코드는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    var errorCode: String? = null
    @field:ByteSize(max = 1024) var errorMessage: String? = null
    @field:ByteSize(max = 4000) var errorComment: String? = null
    @field:ByteSize(max = 20) var errorType: String? = null

    fun toNewEntity(): CcfaErrorTable {
        val e = CcfaErrorTable()
        e.errorCode = errorCode?.trimJava()
        applyTo(e)
        return e
    }

    /** 식별자는 바꾸지 않는다. */
    fun applyTo(e: CcfaErrorTable) {
        e.errorMessage = errorMessage; e.errorComment = errorComment; e.errorType = errorType
    }

    companion object {
        @JvmStatic
        fun from(e: CcfaErrorTable): ErrorCodeForm = ErrorCodeForm().apply {
            errorCode = e.errorCode; errorMessage = e.errorMessage
            errorComment = e.errorComment; errorType = e.errorType
        }
    }
}
