package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import jakarta.validation.constraints.NotBlank

/** FIDO2_METADATA 입력 폼. 길이는 DB 컬럼 길이(바이트). CLOB 두 개는 길이 제한 없음. */
class Fido2MetadataForm {
    @field:NotBlank @field:ByteSize(max = 256) var description: String? = null
    @field:NotBlank @field:ByteSize(max = 256) var aaguid: String? = null
    @field:ByteSize(max = 2048) var alternativedescriptions: String? = null
    @field:ByteSize(max = 128) var protocolfamily: String? = null
    var authenticatorversion: Long? = null
    @field:ByteSize(max = 512) var upv: String? = null
    @field:ByteSize(max = 128) var assertionschme: String? = null
    var authenticationalgorithm: Long? = null
    var publickeyalgandencoding: Long? = null
    @field:ByteSize(max = 2048) var attestationtypes: String? = null
    @field:ByteSize(max = 2048) var userverificationdetails: String? = null
    var keyprotection: Long? = null
    var matcherprotection: Long? = null
    var cryptostrength: Long? = null
    @field:ByteSize(max = 2048) var operatingenv: String? = null
    var attachmenthint: Long? = null
    @field:ByteSize(max = 8) var issecondfactoronly: String? = "false"
    var tcdisplay: Long? = null
    var attestationrootcertificates: String? = null
    var icon: String? = null
    @field:ByteSize(max = 2048) var ski: String? = null

    fun applyTo(m: Fido2Metadata) {
        m.description = description; m.aaguid = aaguid
        m.alternativedescriptions = alternativedescriptions; m.protocolfamily = protocolfamily
        m.authenticatorversion = authenticatorversion; m.upv = upv; m.assertionschme = assertionschme
        m.authenticationalgorithm = authenticationalgorithm; m.publickeyalgandencoding = publickeyalgandencoding
        m.attestationtypes = attestationtypes; m.userverificationdetails = userverificationdetails
        m.keyprotection = keyprotection; m.matcherprotection = matcherprotection
        m.cryptostrength = cryptostrength; m.operatingenv = operatingenv
        m.attachmenthint = attachmenthint; m.issecondfactoronly = issecondfactoronly
        m.tcdisplay = tcdisplay; m.attestationrootcertificates = attestationrootcertificates
        m.icon = icon; m.ski = ski
    }

    companion object {
        @JvmStatic
        fun from(m: Fido2Metadata): Fido2MetadataForm = Fido2MetadataForm().apply {
            description = m.description; aaguid = m.aaguid
            alternativedescriptions = m.alternativedescriptions; protocolfamily = m.protocolfamily
            authenticatorversion = m.authenticatorversion; upv = m.upv; assertionschme = m.assertionschme
            authenticationalgorithm = m.authenticationalgorithm; publickeyalgandencoding = m.publickeyalgandencoding
            attestationtypes = m.attestationtypes; userverificationdetails = m.userverificationdetails
            keyprotection = m.keyprotection; matcherprotection = m.matcherprotection
            cryptostrength = m.cryptostrength; operatingenv = m.operatingenv
            attachmenthint = m.attachmenthint; issecondfactoronly = m.issecondfactoronly
            tcdisplay = m.tcdisplay; attestationrootcertificates = m.attestationrootcertificates
            icon = m.icon; ski = m.ski
        }
    }
}
