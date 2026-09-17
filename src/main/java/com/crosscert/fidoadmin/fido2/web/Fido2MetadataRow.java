package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import java.time.LocalDateTime;

/** 목록 행. CLOB(ICON, ATTESTATIONROOTCERTIFICATES)은 제외한다. */
public record Fido2MetadataRow(Long idx, String description, String aaguid, String protocolfamily,
                               Long authenticatorversion, String assertionschme, String issecondfactoronly,
                               LocalDateTime createdtime) {
    public static Fido2MetadataRow of(Fido2Metadata m) {
        return new Fido2MetadataRow(m.getIdx(), m.getDescription(), m.getAaguid(), m.getProtocolfamily(),
            m.getAuthenticatorversion(), m.getAssertionschme(), m.getIssecondfactoronly(), m.getCreatedtime());
    }
}
