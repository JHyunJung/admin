package com.crosscert.fidoadmin.manager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** CCFA_MANAGER — 운영자 계정. ERD 컬럼 16개. USER_PW 는 SHA-256 hex, 화면 DTO 에 절대 싣지 않는다. */
@Entity
@Table(name = "CCFA_MANAGER")
@Getter @Setter @NoArgsConstructor
@ToString(exclude = "userPw")
public class CcfaManager {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaManagerSeq")
    @SequenceGenerator(name = "ccfaManagerSeq", sequenceName = "CCFA_MANAGER_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "USER_ID", length = 64) private String userId;
    @Column(name = "USER_PW", length = 128) private String userPw;
    @Column(name = "USER_NM", length = 50) private String userNm;
    @Column(name = "USER_EMAIL", length = 256) private String userEmail;
    @Column(name = "USER_PHONE", length = 20) private String userPhone;
    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "STATUS", length = 20) private String status;
    @Column(name = "LOGIN", length = 20) private String login;
    @Column(name = "BLOCK_TIME") private LocalDateTime blockTime;
    @Column(name = "LAST_ACCESS") private LocalDateTime lastAccess;
    @Column(name = "ETC", length = 2048) private String etc;
    @Column(name = "ALRAM_TYPE", length = 32) private String alramType;
    @Column(name = "ALRAM_LEVEL", length = 20) private String alramLevel;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
