package com.crosscert.fidoadmin.company.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * CCFA_FDS_POLICY — COMPANY_IDX 가 PK(고객사당 1건). 컬럼 10개.
 *
 * 어드민에서 쓰지 않는다. 이상 징후 탐지 화면은 2026-10-05 에 지웠지만 테이블은 스키마에
 * 남아 있어 스키마 대조(`SchemaConformanceTest`)를 위해 엔티티만 둔다.
 */
@Entity
@Table(name = "CCFA_FDS_POLICY")
class CcfaFdsPolicy {
    @Id @Column(name = "COMPANY_IDX") var companyIdx: Long? = null
    @Column(name = "AND_IP", length = 4000) var andIp: String? = null
    @Column(name = "AND_TERM", length = 32) var andTerm: String? = null
    @Column(name = "AND_DEVICE", length = 16) var andDevice: String? = null
    @Column(name = "AND_COUNTRY", length = 16) var andCountry: String? = null
    @Column(name = "OR_IP", length = 4000) var orIp: String? = null
    @Column(name = "OR_TERM", length = 32) var orTerm: String? = null
    @Column(name = "OR_COUNTRY", length = 16) var orCountry: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
