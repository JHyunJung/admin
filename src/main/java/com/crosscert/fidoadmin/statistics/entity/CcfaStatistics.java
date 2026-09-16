package com.crosscert.fidoadmin.statistics.entity;

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

/** CCFA_STATISTICS — 어드민 통계 위젯 정의. ERD 컬럼 16개. LIMIT 은 예약어라 따옴표 필수. */
@Entity
@Table(name = "CCFA_STATISTICS")
@Getter @Setter @NoArgsConstructor
public class CcfaStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaStatisticsSeq")
    @SequenceGenerator(name = "ccfaStatisticsSeq", sequenceName = "CCFA_STATISTICS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "OWNER_IDX") private Long ownerIdx;
    @Column(name = "TYPE", length = 16) private String type;
    @Column(name = "OPEN_TYPE", length = 20) private String openType;
    @Column(name = "GROUP1_IDX") private Long group1Idx;
    @Column(name = "GROUP2_IDX") private Long group2Idx;
    @Column(name = "TARGET_IDX") private Long targetIdx;
    @Column(name = "GRAPH_TYPE", length = 32) private String graphType;
    @Column(name = "TITLE", length = 1024) private String title;
    @Column(name = "CONTENT", length = 4000) private String content;
    @Column(name = "PRIME_FIELD_IDX") private Long primeFieldIdx;
    @Column(name = "\"LIMIT\"") private Long limit;
    @Column(name = "REALTIME", length = 20) private String realtime;
    @Column(name = "ETC", length = 4000) private String etc;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
