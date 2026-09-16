package com.crosscert.fidoadmin.aws.entity;

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
import lombok.ToString;

/** AWS_INFO — AWS 마켓플레이스 연동 정보. ERD 컬럼 10개.
 *  EXPIRATIONDATE 는 다른 테이블과 달리 VARCHAR2(100) 라 String 으로 둔다. */
@Entity
@Table(name = "AWS_INFO")
@Getter @Setter @NoArgsConstructor
@ToString(exclude = "amzToken")
public class AwsInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "awsInfoSeq")
    @SequenceGenerator(name = "awsInfoSeq", sequenceName = "AWS_INFO_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "AMZ_TOKEN", length = 512) private String amzToken;
    @Column(name = "CUSTOMER_ID", length = 512) private String customerId;
    @Column(name = "PRODUCT_CODE", length = 512) private String productCode;
    @Column(name = "CUSTOMER_AWS_ACCOUNT_ID", length = 512) private String customerAwsAccountId;
    @Column(name = "DIMENSION", length = 100) private String dimension;
    @Column(name = "EXPIRATIONDATE", length = 100) private String expirationdate;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
