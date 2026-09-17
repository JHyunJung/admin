package com.crosscert.fidoadmin.fido.entity;

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

/** APPID — RP 앱 등록 정보. ERD 컬럼 10개. */
@Entity
@Table(name = "APPID")
@Getter @Setter @NoArgsConstructor
public class Appid {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appidSeq")
    @SequenceGenerator(name = "appidSeq", sequenceName = "APPID_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "APPID", length = 128) private String appid;
    @Column(name = "MEMO", length = 512) private String memo;
    @Column(name = "STATUS", length = 12) private String status;
    @Column(name = "DEVICE", length = 128) private String device;
    @Column(name = "DEVICE_DEFAULT", length = 1) private String deviceDefault;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
