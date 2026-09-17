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

/** TRANSACTION_CONFIRMATION — 거래확인(TC) 내용. ERD 컬럼 7개. */
@Entity
@Table(name = "TRANSACTION_CONFIRMATION")
@Getter @Setter @NoArgsConstructor
public class TransactionConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactionConfirmationSeq")
    @SequenceGenerator(name = "transactionConfirmationSeq", sequenceName = "TRANSACTION_CONFIRMATION_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "USERID", length = 512) private String userid;
    @Column(name = "AAID", length = 64) private String aaid;
    @Column(name = "CONTENTTYPE", length = 128) private String contenttype;
    @Lob @Column(name = "CONTENT") private String content;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
