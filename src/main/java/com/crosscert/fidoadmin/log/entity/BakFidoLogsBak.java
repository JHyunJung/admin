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

/** BAK_FIDO_LOGS_BAK — 로그 백업본(복호화 컬럼 포함, append-only). ERD 컬럼 7개. */
@Entity
@Table(name = "BAK_FIDO_LOGS_BAK")
@Getter @Setter @NoArgsConstructor
public class BakFidoLogsBak {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bakFidoLogsBakSeq")
    @SequenceGenerator(name = "bakFidoLogsBakSeq", sequenceName = "BAK_FIDO_LOGS_BAK_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERIALCODE", length = 128) private String serialcode;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Lob @Column(name = "DEC_JSONDATA") private String decJsondata;
}
