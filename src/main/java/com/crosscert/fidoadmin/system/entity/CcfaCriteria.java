package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_CRITERIA — 어드민 인증기기 정책. ERD 컬럼 6개. */
@Entity
@Table(name = "CCFA_CRITERIA")
@Getter @Setter @NoArgsConstructor
public class CcfaCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaCriteriaSeq")
    @SequenceGenerator(name = "ccfaCriteriaSeq", sequenceName = "CCFA_CRITERIA_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "AAID", length = 64) private String aaid;
    @Column(name = "METAHASH", length = 512) private String metahash;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATETIME") private LocalDateTime createtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
