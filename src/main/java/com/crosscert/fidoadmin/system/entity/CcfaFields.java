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

/** CCFA_FIELDS — 화면 필드 메타 정의. ERD 컬럼 9개. */
@Entity
@Table(name = "CCFA_FIELDS")
@Getter @Setter @NoArgsConstructor
public class CcfaFields {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaFieldsSeq")
    @SequenceGenerator(name = "ccfaFieldsSeq", sequenceName = "CCFA_FIELDS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "FIELD_TABLE", length = 128) private String fieldTable;
    @Column(name = "FIELD_NAME", length = 256) private String fieldName;
    @Column(name = "FIELD_TYPE", length = 128) private String fieldType;
    @Column(name = "FIELD_TITLE", length = 128) private String fieldTitle;
    @Column(name = "PK") private Long pk;
    @Column(name = "FK") private Long fk;
    @Column(name = "OPTION_IDX") private Long optionIdx;
    @Column(name = "EDITABLE") private Long editable;
}
