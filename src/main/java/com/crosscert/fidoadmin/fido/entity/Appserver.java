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

/** APPSERVER — 앱 서버(연동 주체) 정보. ERD 컬럼 8개. */
@Entity
@Table(name = "APPSERVER")
@Getter @Setter @NoArgsConstructor
public class Appserver {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appserverSeq")
    @SequenceGenerator(name = "appserverSeq", sequenceName = "APPSERVER_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "MEMBER_CODE", length = 32) private String memberCode;
    @Column(name = "MEMBER_ID", length = 32) private String memberId;
    @Column(name = "TYPE", length = 10) private String type;
    @Column(name = "NOTE", length = 128) private String note;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
