package com.crosscert.fidoadmin.log.entity;

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

/** CCFA_AUDIT_LOG — 어드민 감사로그(append-only). ERD 컬럼 11개. INTERGRITY_HASH 는 원본 컬럼명 오타 그대로. */
@Entity
@Table(name = "CCFA_AUDIT_LOG")
@Getter @Setter @NoArgsConstructor
public class CcfaAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaAuditLogSeq")
    @SequenceGenerator(name = "ccfaAuditLogSeq", sequenceName = "CCFA_AUDIT_LOG_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "COMPANY_NAME", length = 512) private String companyName;
    @Column(name = "TYPE", length = 32) private String type;
    @Column(name = "USER_ID", length = 64) private String userId;
    @Column(name = "USER_NAME", length = 32) private String userName;
    @Column(name = "MESSAGE", length = 4000) private String message;
    @Column(name = "IP", length = 15) private String ip;
    @Column(name = "UA", length = 2048) private String ua;
    @Column(name = "INTERGRITY_HASH", length = 512) private String intergrityHash;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
