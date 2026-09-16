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

/** CCFA_MENU — 어드민 메뉴 트리. ERD 컬럼 13개. */
@Entity
@Table(name = "CCFA_MENU")
@Getter @Setter @NoArgsConstructor
public class CcfaMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaMenuSeq")
    @SequenceGenerator(name = "ccfaMenuSeq", sequenceName = "CCFA_MENU_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "MENU_NAME", length = 32) private String menuName;
    @Column(name = "MENU_CODE", length = 32) private String menuCode;
    @Column(name = "MENU_PARENT_IDX") private Long menuParentIdx;
    @Column(name = "MENU_ICON", length = 64) private String menuIcon;
    @Column(name = "MENU_URL", length = 512) private String menuUrl;
    @Column(name = "MENU_SEQ") private Long menuSeq;
    @Column(name = "TBL_NAME", length = 128) private String tblName;
    @Column(name = "PK", length = 128) private String pk;
    @Column(name = "VISIBLE", length = 20) private String visible;
    @Column(name = "OPEN_TYPE", length = 20) private String openType;
    @Column(name = "STATISTICS", length = 20) private String statistics;
    @Column(name = "READONLY", length = 20) private String readonly;
}
