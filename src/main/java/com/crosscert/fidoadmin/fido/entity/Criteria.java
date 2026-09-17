package com.crosscert.fidoadmin.fido.entity;

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

/** CRITERIA — 인증기기 정책 기준(UAF). ERD 컬럼 17개. */
@Entity
@Table(name = "CRITERIA")
@Getter @Setter @NoArgsConstructor
public class Criteria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "criteriaSeq")
    @SequenceGenerator(name = "criteriaSeq", sequenceName = "CRITERIA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "AAID", length = 64) private String aaid;
    @Column(name = "VENDORIDS", length = 32) private String vendorids;
    @Column(name = "USERVERIFICATION") private Long userverification;
    @Column(name = "KEYPROTECTION") private Long keyprotection;
    @Column(name = "MATCHERPROTECTION") private Long matcherprotection;
    @Column(name = "ATTACHMENTHNUMBER") private Long attachmenthnumber;
    @Column(name = "TCDISPLAY") private Long tcdisplay;
    @Column(name = "TCDISPLAYCONTENTTYPE", length = 128) private String tcdisplaycontenttype;
    @Column(name = "AUTHENTICATIONALGORITHMS", length = 64) private String authenticationalgorithms;
    @Column(name = "ASSERTIONSCHEMES", length = 64) private String assertionschemes;
    @Column(name = "ATTESTATIONTYPES", length = 64) private String attestationtypes;
    @Column(name = "AUTHENTICATORVERSION") private Long authenticatorversion;
    @Column(name = "METAHASH", length = 512) private String metahash;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATETIME") private LocalDateTime createtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
