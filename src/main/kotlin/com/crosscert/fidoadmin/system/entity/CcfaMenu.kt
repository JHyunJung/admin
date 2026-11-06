package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

/** CCFA_MENU — 어드민 메뉴 트리. 컬럼 13개. */
@Entity
@Table(name = "CCFA_MENU")
class CcfaMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaMenuSeq")
    @SequenceGenerator(name = "ccfaMenuSeq", sequenceName = "CCFA_MENU_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "MENU_NAME", length = 32) var menuName: String? = null
    @Column(name = "MENU_CODE", length = 32) var menuCode: String? = null
    @Column(name = "MENU_PARENT_IDX") var menuParentIdx: Long? = null
    @Column(name = "MENU_ICON", length = 64) var menuIcon: String? = null
    @Column(name = "MENU_URL", length = 512) var menuUrl: String? = null
    @Column(name = "MENU_SEQ") var menuSeq: Long? = null
    @Column(name = "TBL_NAME", length = 128) var tblName: String? = null
    @Column(name = "PK", length = 128) var pk: String? = null
    @Column(name = "VISIBLE", length = 20) var visible: String? = null
    @Column(name = "OPEN_TYPE", length = 20) var openType: String? = null
    @Column(name = "STATISTICS", length = 20) var statistics: String? = null
    @Column(name = "READONLY", length = 20) var readonly: String? = null
}
