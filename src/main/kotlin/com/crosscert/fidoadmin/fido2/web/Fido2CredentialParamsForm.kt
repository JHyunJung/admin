package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** FIDO2_CREDENTIAL_PARAMS 입력 폼. CRED_ALG 는 COSE 알고리즘 번호(-7 ES256, -257 RS256, -8 EdDSA). */
class Fido2CredentialParamsForm {
    @field:NotBlank @field:ByteSize(max = 32) var credType: String? = "public-key"
    @field:NotNull var credAlg: Long? = null
    @field:NotBlank @field:ByteSize(max = 1) var status: String? = "T"

    fun applyTo(p: Fido2CredentialParams) {
        p.credType = credType; p.credAlg = credAlg; p.status = status
    }

    companion object {
        @JvmStatic
        fun from(p: Fido2CredentialParams): Fido2CredentialParamsForm = Fido2CredentialParamsForm().apply {
            credType = p.credType; credAlg = p.credAlg; status = p.status
        }
    }
}
