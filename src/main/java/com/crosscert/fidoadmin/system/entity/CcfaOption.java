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

/** CCFA_OPTION — 코드 그룹. ERD 컬럼 4개. */
@Entity
@Table(name = "CCFA_OPTION")
@Getter @Setter @NoArgsConstructor
public class CcfaOption {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaOptionSeq")
    @SequenceGenerator(name = "ccfaOptionSeq", sequenceName = "CCFA_OPTION_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "OPTION_NAME", length = 64) private String optionName;
    @Column(name = "OPTION_NOTE", length = 64) private String optionNote;
    @Column(name = "OPTION_TITLE", length = 64) private String optionTitle;
}
