package com.crosscert.fidoadmin.company.entity;

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

/** CCFA_COMPANY — 고객사(테넌트). ERD 컬럼 19개. IDX 0 은 전역(시스템) 레코드. */
@Entity
@Table(name = "CCFA_COMPANY")
@Getter @Setter @NoArgsConstructor
public class CcfaCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaCompanySeq")
    @SequenceGenerator(name = "ccfaCompanySeq", sequenceName = "CCFA_COMPANY_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_NAME", length = 256) private String companyName;
    @Column(name = "COMPANY_TYPE", length = 20) private String companyType;
    @Column(name = "VENDOR_CODE", length = 20) private String vendorCode;
    @Column(name = "CONTACT", length = 512) private String contact;
    @Column(name = "CONTACT_PHONE", length = 50) private String contactPhone;
    @Column(name = "CONTACT_PHONE2", length = 50) private String contactPhone2;
    @Column(name = "CONTACT_ADDR", length = 2048) private String contactAddr;
    @Column(name = "ENABLE_TYPE", length = 20) private String enableType;
    @Column(name = "STARTTIME") private LocalDateTime starttime;
    @Column(name = "ENDTIME") private LocalDateTime endtime;
    @Column(name = "MAX_APPID") private Long maxAppid;
    @Column(name = "MAX_APPSERVER") private Long maxAppserver;
    @Column(name = "MAX_USER") private Long maxUser;
    @Column(name = "ETC", length = 4000) private String etc;
    @Column(name = "CREATOR") private Long creator;
    @Column(name = "UPDATOR") private Long updator;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
