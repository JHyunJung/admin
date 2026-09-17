package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_OPTIONS — 코드 상세(CCFA_OPTION 의 하위 항목). ERD 컬럼 5개. */
@Entity
@Table(name = "CCFA_OPTIONS")
@Getter @Setter @NoArgsConstructor
public class CcfaOptions {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaOptionsSeq")
    @SequenceGenerator(name = "ccfaOptionsSeq", sequenceName = "CCFA_OPTIONS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "OPTION_IDX") private Long optionIdx;
    @Column(name = "OPTION_VALUE", length = 128) private String optionValue;
    @Column(name = "OPTION_TITLE", length = 128) private String optionTitle;
    @Column(name = "OPTION_NOTE", length = 128) private String optionNote;
}
