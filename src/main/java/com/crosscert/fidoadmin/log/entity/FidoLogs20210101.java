package com.crosscert.fidoadmin.log.entity;

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

/** FIDO_LOGS_20210101 — 일자별 로그 아카이브(append-only). ERD 컬럼 6개. */
@Entity
@Table(name = "FIDO_LOGS_20210101")
@Getter @Setter @NoArgsConstructor
public class FidoLogs20210101 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fidoLogs20210101Seq")
    @SequenceGenerator(name = "fidoLogs20210101Seq", sequenceName = "FIDO_LOGS_20210101_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERIALCODE", length = 128) private String serialcode;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
