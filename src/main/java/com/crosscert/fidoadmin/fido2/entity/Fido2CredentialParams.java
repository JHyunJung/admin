package com.crosscert.fidoadmin.fido2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO2_CREDENTIAL_PARAMS — FIDO2 지원 알고리즘 목록. ERD 컬럼 5개. */
@Entity
@Table(name = "FIDO2_CREDENTIAL_PARAMS")
@Getter @Setter @NoArgsConstructor
public class Fido2CredentialParams {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fido2CredentialParamsSeq")
    @SequenceGenerator(name = "fido2CredentialParamsSeq", sequenceName = "FIDO2_CREDENTIAL_PARAMS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "CRED_TYPE", length = 32) private String credType;
    @Column(name = "CRED_ALG") private Long credAlg;
    @Column(name = "STATUS", length = 1) private String status;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
