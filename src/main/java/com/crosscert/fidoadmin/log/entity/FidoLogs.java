package com.crosscert.fidoadmin.log.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO_LOGS — FIDO 처리 로그(append-only). ERD 컬럼 6개. */
@Entity
@Table(name = "FIDO_LOGS")
@Getter @Setter @NoArgsConstructor
public class FidoLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fidoLogsSeq")
    @SequenceGenerator(name = "fidoLogsSeq", sequenceName = "FIDO_LOGS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERIALCODE", length = 128) private String serialcode;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
