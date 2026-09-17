package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_FIDOCLIENT — FIDO 서버 연동 정보. PK 없음(논리 식별자 SERVERCODE). ERD 컬럼 6개. */
@Entity
@Table(name = "CCFA_FIDOCLIENT")
@Getter @Setter @NoArgsConstructor
public class CcfaFidoclient {
    @Id @Column(name = "SERVERCODE", length = 64) private String servercode;
    @Column(name = "SERVERNAME", length = 1024) private String servername;
    @Column(name = "SERVERURL", length = 2048) private String serverurl;
    @Column(name = "STATUS", length = 4) private String status;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
