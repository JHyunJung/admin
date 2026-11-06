package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * CCFA_FIDOCLIENT 입력 폼. servercode 는 등록 시에만 쓰인다.
 * SERVERCODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL·리다이렉트에 안전한 문자만 허용한다.
 */
class FidoClientForm {
    @field:NotBlank @field:ByteSize(max = 64)
    @field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "코드는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    var servercode: String? = null
    @field:NotBlank @field:ByteSize(max = 1024) var servername: String? = null
    @field:NotBlank @field:ByteSize(max = 2048) var serverurl: String? = null
    @field:NotBlank @field:ByteSize(max = 4) var status: String? = "ON"

    fun toNewEntity(): CcfaFidoclient {
        val c = CcfaFidoclient()
        c.servercode = servercode?.javaTrim()
        applyTo(c)
        return c
    }

    /** 식별자는 바꾸지 않는다. */
    fun applyTo(c: CcfaFidoclient) {
        c.servername = servername; c.serverurl = serverurl; c.status = status
    }

    companion object {
        @JvmStatic
        fun from(c: CcfaFidoclient): FidoClientForm = FidoClientForm().apply {
            servercode = c.servercode; servername = c.servername
            serverurl = c.serverurl; status = c.status
        }
    }
}
