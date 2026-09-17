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

/** CCFA_LICENSE — 라이선스. ERD 컬럼 13개. */
@Entity
@Table(name = "CCFA_LICENSE")
@Getter @Setter @NoArgsConstructor
public class CcfaLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaLicenseSeq")
    @SequenceGenerator(name = "ccfaLicenseSeq", sequenceName = "CCFA_LICENSE_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "COMPANY_NAME", length = 256) private String companyName;
    @Column(name = "CONTACT_NAME", length = 128) private String contactName;
    @Column(name = "CONTACT_PHONE", length = 128) private String contactPhone;
    @Column(name = "CONTACT_EMAIL", length = 256) private String contactEmail;
    @Column(name = "SERVICE_NAME", length = 256) private String serviceName;
    @Column(name = "ETC", length = 1024) private String etc;
    @Column(name = "LICENSE", length = 2048) private String license;
    @Column(name = "FILE_PATH", length = 256) private String filePath;
    @Column(name = "HASHVALUE", length = 256) private String hashvalue;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
