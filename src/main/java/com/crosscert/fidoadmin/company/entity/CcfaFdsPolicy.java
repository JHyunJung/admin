package com.crosscert.fidoadmin.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_FDS_POLICY — COMPANY_IDX 가 PK(고객사당 1건). ERD 컬럼 10개. */
@Entity
@Table(name = "CCFA_FDS_POLICY")
@Getter @Setter @NoArgsConstructor
public class CcfaFdsPolicy {
    @Id @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "AND_IP", length = 4000) private String andIp;
    @Column(name = "AND_TERM", length = 32) private String andTerm;
    @Column(name = "AND_DEVICE", length = 16) private String andDevice;
    @Column(name = "AND_COUNTRY", length = 16) private String andCountry;
    @Column(name = "OR_IP", length = 4000) private String orIp;
    @Column(name = "OR_TERM", length = 32) private String orTerm;
    @Column(name = "OR_COUNTRY", length = 16) private String orCountry;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
