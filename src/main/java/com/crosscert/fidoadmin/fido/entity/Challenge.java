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

/** CHALLENGE — 인증 챌린지(일회성). ERD 컬럼 8개. */
@Entity
@Table(name = "CHALLENGE")
@Getter @Setter @NoArgsConstructor
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "challengeSeq")
    @SequenceGenerator(name = "challengeSeq", sequenceName = "CHALLENGE_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "USERID", length = 512) private String userid;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Column(name = "CHALLENGECODE", length = 140) private String challengecode;
    @Column(name = "CREATETIME") private LocalDateTime createtime;
    @Column(name = "LOGIDX") private Long logidx;
    @Column(name = "UV", length = 20) private String uv;
}
