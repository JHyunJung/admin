package com.crosscert.fidoadmin.fido.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** USERINFO — FIDO 등록 사용자 인증정보(크리덴셜). ERD 컬럼 15개. PUBKEY/CERTIFICATE 는 민감정보. */
@Entity
@Table(name = "USERINFO")
@Getter @Setter @NoArgsConstructor
@ToString(exclude = {"pubkey", "certificate"})
public class Userinfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "userinfoSeq")
    @SequenceGenerator(name = "userinfoSeq", sequenceName = "USERINFO_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "BIO_TYPE") private Long bioType;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Column(name = "USERID", length = 512) private String userid;
    @Column(name = "AAID", length = 128) private String aaid;
    @Column(name = "AUTHENTICATORVERSION") private Long authenticatorversion;
    @Column(name = "KEYID", length = 1024) private String keyid;
    @Column(name = "PUBKEY", length = 2048) private String pubkey;
    @Column(name = "CERTIFICATE", length = 2048) private String certificate;
    @Column(name = "SIGNCOUNTER") private Long signcounter;
    @Column(name = "UVS", length = 64) private String uvs;
    @Column(name = "STATUS", length = 20) private String status;
    @Column(name = "REGTIME") private LocalDateTime regtime;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
