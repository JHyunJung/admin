package com.crosscert.fidoadmin.manager.entity;

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

/** CCFA_MANAGER_PW_POLICY — 운영자 비밀번호 정책/잠금 상태. ERD 컬럼 6개. */
@Entity
@Table(name = "CCFA_MANAGER_PW_POLICY")
@Getter @Setter @NoArgsConstructor
public class CcfaManagerPwPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaManagerPwPolicySeq")
    @SequenceGenerator(name = "ccfaManagerPwPolicySeq", sequenceName = "CCFA_MANAGER_PW_POLICY_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "USER_ID", length = 64) private String userId;
    @Column(name = "ACCOUNT_LOCK", length = 1) private String accountLock;
    @Column(name = "PW_FAIL_CNT") private Long pwFailCnt;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
