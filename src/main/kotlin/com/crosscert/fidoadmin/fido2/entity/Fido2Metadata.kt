package com.crosscert.fidoadmin.fido2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** FIDO2_METADATA — FIDO2 인증기기 메타데이터(MDS3). 컬럼 23개.
 *  ASSERTIONSCHME 는 원본 컬럼명 오타(ASSERTIONSCHEME 아님) 그대로 유지한다. */
@Entity
@Table(name = "FIDO2_METADATA")
class Fido2Metadata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fido2MetadataSeq")
    @SequenceGenerator(name = "fido2MetadataSeq", sequenceName = "FIDO2_METADATA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "DESCRIPTION", length = 256) var description: String? = null
    @Column(name = "AAGUID", length = 256) var aaguid: String? = null
    @Column(name = "ALTERNATIVEDESCRIPTIONS", length = 2048) var alternativedescriptions: String? = null
    @Column(name = "PROTOCOLFAMILY", length = 128) var protocolfamily: String? = null
    @Column(name = "AUTHENTICATORVERSION") var authenticatorversion: Long? = null
    @Column(name = "UPV", length = 512) var upv: String? = null
    @Column(name = "ASSERTIONSCHME", length = 128) var assertionschme: String? = null
    @Column(name = "AUTHENTICATIONALGORITHM") var authenticationalgorithm: Long? = null
    @Column(name = "PUBLICKEYALGANDENCODING") var publickeyalgandencoding: Long? = null
    @Column(name = "ATTESTATIONTYPES", length = 2048) var attestationtypes: String? = null
    @Column(name = "USERVERIFICATIONDETAILS", length = 2048) var userverificationdetails: String? = null
    @Column(name = "KEYPROTECTION") var keyprotection: Long? = null
    @Column(name = "MATCHERPROTECTION") var matcherprotection: Long? = null
    @Column(name = "CRYPTOSTRENGTH") var cryptostrength: Long? = null
    @Column(name = "OPERATINGENV", length = 2048) var operatingenv: String? = null
    @Column(name = "ATTACHMENTHINT") var attachmenthint: Long? = null
    @Column(name = "ISSECONDFACTORONLY", length = 8) var issecondfactoronly: String? = null
    @Column(name = "TCDISPLAY") var tcdisplay: Long? = null
    @Lob @Column(name = "ATTESTATIONROOTCERTIFICATES") var attestationrootcertificates: String? = null
    @Lob @Column(name = "ICON") var icon: String? = null
    @Column(name = "SKI", length = 2048) var ski: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
}
