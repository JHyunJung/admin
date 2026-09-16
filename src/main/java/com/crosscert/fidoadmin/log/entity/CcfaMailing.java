package com.crosscert.fidoadmin.log.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_MAILING — 메일/SMS 발송 큐. ERD 컬럼 10개. TO 는 예약어라 따옴표 필수. */
@Entity
@Table(name = "CCFA_MAILING")
@Getter @Setter @NoArgsConstructor
public class CcfaMailing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaMailingSeq")
    @SequenceGenerator(name = "ccfaMailingSeq", sequenceName = "CCFA_MAILING_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "\"TO\"", length = 4000) private String to;
    @Column(name = "SUBJECT", length = 256) private String subject;
    @Column(name = "CONTENT", length = 4000) private String content;
    @Column(name = "STATUS", length = 256) private String status;
    @Column(name = "SMS_TO", length = 4000) private String smsTo;
    @Column(name = "SMS_CONTENT", length = 300) private String smsContent;
    @Column(name = "SMS_STATUS", length = 512) private String smsStatus;
    /** ERD 상 VARCHAR2(64). 문자열 그대로 둔다. */
    @Column(name = "SENDTIME", length = 64) private String sendtime;
}
