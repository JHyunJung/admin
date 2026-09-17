package com.crosscert.fidoadmin.fido2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO2_DEMO_ACCESS_CODE — 데모 접근 코드. PK 없음(논리 식별자 ACCESSCODE). ERD 컬럼 7개.
 *  STARTTIME/ENDTIME 은 epoch 값(NUMBER) 이므로 Long 으로 매핑한다. */
@Entity
@Table(name = "FIDO2_DEMO_ACCESS_CODE")
@Getter @Setter @NoArgsConstructor
public class Fido2DemoAccessCode {
    @Id @Column(name = "ACCESSCODE", length = 128) private String accesscode;
    @Column(name = "VENDORNAME", length = 128) private String vendorname;
    @Column(name = "STARTTIME") private Long starttime;
    @Column(name = "ENDTIME") private Long endtime;
    @Column(name = "STATUS", length = 1) private String status;
    @Column(name = "NOTE", length = 300) private String note;
    @Column(name = "ETC", length = 1024) private String etc;
}
