package com.crosscert.fidoadmin.fido.entity;

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

/** TRANSACTIONHASH — 거래내용 해시. ERD 컬럼 6개. */
@Entity
@Table(name = "TRANSACTIONHASH")
@Getter @Setter @NoArgsConstructor
public class Transactionhash {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactionhashSeq")
    @SequenceGenerator(name = "transactionhashSeq", sequenceName = "TRANSACTIONHASH_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "USERID", length = 512) private String userid;
    @Lob @Column(name = "CONTENT") private String content;
    @Column(name = "CONTENTHASH", length = 64) private String contenthash;
    @Column(name = "CREATETIME") private LocalDateTime createtime;
}
