package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_ERROR_TABLE — 에러코드 사전. PK 없음(논리 식별자 ERROR_CODE). ERD 컬럼 4개. */
@Entity
@Table(name = "CCFA_ERROR_TABLE")
@Getter @Setter @NoArgsConstructor
public class CcfaErrorTable {
    @Id @Column(name = "ERROR_CODE", length = 20) private String errorCode;
    @Column(name = "ERROR_MESSAGE", length = 1024) private String errorMessage;
    @Column(name = "ERROR_COMMENT", length = 4000) private String errorComment;
    @Column(name = "ERROR_TYPE", length = 20) private String errorType;
}
