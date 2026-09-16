package com.crosscert.fidoadmin.log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_EXCEPTIONS — 예외 로그(append-only). ERD 컬럼 8개.
 *  CREATEDTIME 은 다른 테이블과 달리 VARCHAR2(64) 라 String 으로 둔다. */
@Entity
@Table(name = "CCFA_EXCEPTIONS")
@Getter @Setter @NoArgsConstructor
public class CcfaExceptions {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaExceptionsSeq")
    @SequenceGenerator(name = "ccfaExceptionsSeq", sequenceName = "CCFA_EXCEPTIONS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "E_TYPE", length = 32) private String eType;
    @Column(name = "E_LEVEL", length = 32) private String eLevel;
    @Column(name = "EXCEPTION_MESSAGE", length = 4000) private String exceptionMessage;
    @Column(name = "EXCEPTION_DETAIL_MESSAGE", length = 4000) private String exceptionDetailMessage;
    @Lob @Column(name = "EXCEPTION_DATA") private String exceptionData;
    @Column(name = "CREATEDTIME", length = 64) private String createdtime;
}
