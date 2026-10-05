package com.crosscert.fidoadmin.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CCFA_FDS_POLICY — COMPANY_IDX 가 PK(고객사당 1건). ERD 컬럼 10개.
 *
 * <p>어드민에서 쓰지 않는다. 이상 징후 탐지 화면은 2026-10-05 에 지웠지만 테이블은 운영 DB 에
 * 남아 있어 ERD 대조({@code ErdConformanceTest})를 위해 엔티티만 둔다.
 */
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
