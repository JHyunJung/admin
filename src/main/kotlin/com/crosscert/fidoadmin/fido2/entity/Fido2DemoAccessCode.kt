package com.crosscert.fidoadmin.fido2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** FIDO2_DEMO_ACCESS_CODE — 데모 접근 코드. PK 없음(논리 식별자 ACCESSCODE). 컬럼 7개.
 *  STARTTIME/ENDTIME 은 epoch 값(NUMBER) 이므로 Long 으로 매핑한다. */
@Entity
@Table(name = "FIDO2_DEMO_ACCESS_CODE")
class Fido2DemoAccessCode {
    @Id @Column(name = "ACCESSCODE", length = 128) var accesscode: String? = null
    @Column(name = "VENDORNAME", length = 128) var vendorname: String? = null
    @Column(name = "STARTTIME") var starttime: Long? = null
    @Column(name = "ENDTIME") var endtime: Long? = null
    @Column(name = "STATUS", length = 1) var status: String? = null
    @Column(name = "NOTE", length = 300) var note: String? = null
    @Column(name = "ETC", length = 1024) var etc: String? = null
}
