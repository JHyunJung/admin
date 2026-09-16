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

/** SIGN — 전자서명 원본/서명값. ERD 컬럼 11개. */
@Entity
@Table(name = "SIGN")
@Getter @Setter @NoArgsConstructor
public class Sign {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "signSeq")
    @SequenceGenerator(name = "signSeq", sequenceName = "SIGN_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "USERID", length = 512) private String userid;
    @Column(name = "ASSERTION", length = 4000) private String assertion;
    @Column(name = "DN", length = 200) private String dn;
    @Lob @Column(name = "PLAINTEXT") private String plaintext;
    @Column(name = "DATA", length = 2000) private String data;
    @Column(name = "SIGNATURE", length = 1000) private String signature;
    @Column(name = "MEMO", length = 64) private String memo;
    @Column(name = "DEL", length = 1) private String del;
    @Column(name = "CREATETIME") private LocalDateTime createtime;
}
