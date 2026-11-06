package com.crosscert.fidoadmin.fido2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** FIDO2_CREDENTIAL_PARAMS — FIDO2 지원 알고리즘 목록. 컬럼 5개. */
@Entity
@Table(name = "FIDO2_CREDENTIAL_PARAMS")
class Fido2CredentialParams {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fido2CredentialParamsSeq")
    @SequenceGenerator(name = "fido2CredentialParamsSeq", sequenceName = "FIDO2_CREDENTIAL_PARAMS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    var idx: Long? = null

    @Column(name = "CRED_TYPE", length = 32) var credType: String? = null
    @Column(name = "CRED_ALG") var credAlg: Long? = null
    @Column(name = "STATUS", length = 1) var status: String? = null
    @Column(name = "CREATEDTIME") var createdtime: LocalDateTime? = null
}
