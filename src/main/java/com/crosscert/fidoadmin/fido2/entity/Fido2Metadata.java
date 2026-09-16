package com.crosscert.fidoadmin.fido2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO2_METADATA — FIDO2 인증기기 메타데이터(MDS3). ERD 컬럼 23개.
 *  ASSERTIONSCHME 는 원본 컬럼명 오타(ASSERTIONSCHEME 아님) 그대로 유지한다. */
@Entity
@Table(name = "FIDO2_METADATA")
@Getter @Setter @NoArgsConstructor
public class Fido2Metadata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fido2MetadataSeq")
    @SequenceGenerator(name = "fido2MetadataSeq", sequenceName = "FIDO2_METADATA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "DESCRIPTION", length = 256) private String description;
    @Column(name = "AAGUID", length = 256) private String aaguid;
    @Column(name = "ALTERNATIVEDESCRIPTIONS", length = 2048) private String alternativedescriptions;
    @Column(name = "PROTOCOLFAMILY", length = 128) private String protocolfamily;
    @Column(name = "AUTHENTICATORVERSION") private Long authenticatorversion;
    @Column(name = "UPV", length = 512) private String upv;
    @Column(name = "ASSERTIONSCHME", length = 128) private String assertionschme;
    @Column(name = "AUTHENTICATIONALGORITHM") private Long authenticationalgorithm;
    @Column(name = "PUBLICKEYALGANDENCODING") private Long publickeyalgandencoding;
    @Column(name = "ATTESTATIONTYPES", length = 2048) private String attestationtypes;
    @Column(name = "USERVERIFICATIONDETAILS", length = 2048) private String userverificationdetails;
    @Column(name = "KEYPROTECTION") private Long keyprotection;
    @Column(name = "MATCHERPROTECTION") private Long matcherprotection;
    @Column(name = "CRYPTOSTRENGTH") private Long cryptostrength;
    @Column(name = "OPERATINGENV", length = 2048) private String operatingenv;
    @Column(name = "ATTACHMENTHINT") private Long attachmenthint;
    @Column(name = "ISSECONDFACTORONLY", length = 8) private String issecondfactoronly;
    @Column(name = "TCDISPLAY") private Long tcdisplay;
    @Lob @Column(name = "ATTESTATIONROOTCERTIFICATES") private String attestationrootcertificates;
    @Lob @Column(name = "ICON") private String icon;
    @Column(name = "SKI", length = 2048) private String ski;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
