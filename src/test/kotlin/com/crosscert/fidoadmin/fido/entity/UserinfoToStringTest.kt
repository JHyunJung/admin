package com.crosscert.fidoadmin.fido.entity

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 손으로 쓴 toString() 의 형식과 PUBKEY·CERTIFICATE 제외를 고정하는 특성화 테스트. */
class UserinfoToStringTest {

    @Test fun formatMatchesAndExcludesPubkeyAndCertificate() {
        val e = Userinfo().apply {
            idx = 1L
            companyIdx = 2L
            bioType = 3L
            servicename = "svc"
            userid = "user1"
            aaid = "aaid1"
            authenticatorversion = 4L
            keyid = "key1"
            pubkey = "SECRET-PUBKEY-MARKER"
            certificate = "SECRET-CERT-MARKER"
            signcounter = 5L
            uvs = "uvs1"
            status = "ACTIVE"
            regtime = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
            createdtime = LocalDateTime.of(2026, 6, 7, 8, 9, 10)
        }

        val s = e.toString()

        assertThat(s).isEqualTo(
            "Userinfo(idx=1, companyIdx=2, bioType=3, servicename=svc, userid=user1, " +
                "aaid=aaid1, authenticatorversion=4, keyid=key1, signcounter=5, " +
                "uvs=uvs1, status=ACTIVE, regtime=2026-01-02T03:04:05, createdtime=2026-06-07T08:09:10)")
        assertThat(s)
            .doesNotContain("SECRET-PUBKEY-MARKER")
            .doesNotContain("SECRET-CERT-MARKER")
            .doesNotContain("pubkey")
            .doesNotContain("certificate")
    }

    @Test fun nullFieldsPrintAsNull() {
        assertThat(Userinfo().toString()).isEqualTo(
            "Userinfo(idx=null, companyIdx=null, bioType=null, servicename=null, userid=null, " +
                "aaid=null, authenticatorversion=null, keyid=null, signcounter=null, " +
                "uvs=null, status=null, regtime=null, createdtime=null)")
    }
}
