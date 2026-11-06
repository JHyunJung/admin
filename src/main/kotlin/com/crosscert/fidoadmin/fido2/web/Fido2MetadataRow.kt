package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import java.time.LocalDateTime

/** 목록 행. CLOB(ICON, ATTESTATIONROOTCERTIFICATES)은 제외한다. */
@JvmRecord
data class Fido2MetadataRow(
    val idx: Long?, val description: String?, val aaguid: String?, val protocolfamily: String?,
    val authenticatorversion: Long?, val assertionschme: String?, val issecondfactoronly: String?,
    val createdtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(m: Fido2Metadata): Fido2MetadataRow = Fido2MetadataRow(
            m.idx, m.description, m.aaguid, m.protocolfamily,
            m.authenticatorversion, m.assertionschme, m.issecondfactoronly, m.createdtime,
        )
    }
}
