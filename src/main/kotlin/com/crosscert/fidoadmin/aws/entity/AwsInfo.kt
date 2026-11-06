package com.crosscert.fidoadmin.aws.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** AWS_INFO — AWS 마켓플레이스 연동 정보. 컬럼 10개.
 *  EXPIRATIONDATE 는 다른 테이블과 달리 VARCHAR2(100) 라 String 으로 둔다. */
@Entity
@Table(name = "AWS_INFO")
class AwsInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "awsInfoSeq")
    @SequenceGenerator(name = "awsInfoSeq", sequenceName = "AWS_INFO_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "AMZ_TOKEN", length = 512) var amzToken: String? = null
    @Column(name = "CUSTOMER_ID", length = 512) var customerId: String? = null
    @Column(name = "PRODUCT_CODE", length = 512) var productCode: String? = null
    @Column(name = "CUSTOMER_AWS_ACCOUNT_ID", length = 512) var customerAwsAccountId: String? = null
    @Column(name = "DIMENSION", length = 100) var dimension: String? = null
    @Column(name = "EXPIRATIONDATE", length = 100) var expirationdate: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null

    /** Lombok {@code @ToString(exclude = "amzToken")} 와 같은 형식. 토큰은 로그에 남기지 않는다. */
    override fun toString(): String =
        "AwsInfo(idx=$idx, companyIdx=$companyIdx, customerId=$customerId, productCode=$productCode, " +
            "customerAwsAccountId=$customerAwsAccountId, dimension=$dimension, expirationdate=$expirationdate, " +
            "createdtime=$createdtime, updatedtime=$updatedtime)"
}
