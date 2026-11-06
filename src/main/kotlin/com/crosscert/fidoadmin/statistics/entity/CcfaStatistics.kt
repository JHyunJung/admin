package com.crosscert.fidoadmin.statistics.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** CCFA_STATISTICS — 어드민 통계 위젯 정의. 컬럼 16개. LIMIT 은 예약어라 따옴표 필수. */
@Entity
@Table(name = "CCFA_STATISTICS")
class CcfaStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaStatisticsSeq")
    @SequenceGenerator(name = "ccfaStatisticsSeq", sequenceName = "CCFA_STATISTICS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "OWNER_IDX") var ownerIdx: Long? = null
    @Column(name = "TYPE", length = 16) var type: String? = null
    @Column(name = "OPEN_TYPE", length = 20) var openType: String? = null
    @Column(name = "GROUP1_IDX") var group1Idx: Long? = null
    @Column(name = "GROUP2_IDX") var group2Idx: Long? = null
    @Column(name = "TARGET_IDX") var targetIdx: Long? = null
    @Column(name = "GRAPH_TYPE", length = 32) var graphType: String? = null
    @Column(name = "TITLE", length = 1024) var title: String? = null
    @Column(name = "CONTENT", length = 4000) var content: String? = null
    @Column(name = "PRIME_FIELD_IDX") var primeFieldIdx: Long? = null
    @Column(name = "\"LIMIT\"") var limit: Long? = null
    @Column(name = "REALTIME", length = 20) var realtime: String? = null
    @Column(name = "ETC", length = 4000) var etc: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
    @Column(name = "UPDATEDTIME") var updatedtime: LocalDateTime? = null
}
