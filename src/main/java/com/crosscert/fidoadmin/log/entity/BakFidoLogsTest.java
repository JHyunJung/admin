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

/** BAK_FIDO_LOGS_TEST — 로그 테스트 테이블(append-only). ERD 컬럼 6개. */
@Entity
@Table(name = "BAK_FIDO_LOGS_TEST")
@Getter @Setter @NoArgsConstructor
public class BakFidoLogsTest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bakFidoLogsTestSeq")
    @SequenceGenerator(name = "bakFidoLogsTestSeq", sequenceName = "BAK_FIDO_LOGS_TEST_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERIALCODE", length = 128) private String serialcode;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
