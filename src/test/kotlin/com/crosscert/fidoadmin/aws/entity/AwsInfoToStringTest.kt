package com.crosscert.fidoadmin.aws.entity

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 손으로 쓴 toString() 의 형식과 AMZ_TOKEN 제외를 고정하는 특성화 테스트. */
class AwsInfoToStringTest {

    @Test fun formatMatchesAndExcludesAmzToken() {
        val e = AwsInfo().apply {
            idx = 1L
            companyIdx = 2L
            amzToken = "SECRET-AMZ-TOKEN-MARKER"
            customerId = "cust"
            productCode = "prod"
            customerAwsAccountId = "acct"
            dimension = "dim"
            expirationdate = "2026-12-31"
            createdtime = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
            updatedtime = LocalDateTime.of(2026, 6, 7, 8, 9, 10)
        }

        val s = e.toString()

        assertThat(s).isEqualTo(
            "AwsInfo(idx=1, companyIdx=2, customerId=cust, productCode=prod, " +
                "customerAwsAccountId=acct, dimension=dim, expirationdate=2026-12-31, " +
                "createdtime=2026-01-02T03:04:05, updatedtime=2026-06-07T08:09:10)")
        assertThat(s).doesNotContain("SECRET-AMZ-TOKEN-MARKER").doesNotContain("amzToken")
    }

    @Test fun nullFieldsPrintAsNull() {
        assertThat(AwsInfo().toString()).isEqualTo(
            "AwsInfo(idx=null, companyIdx=null, customerId=null, productCode=null, " +
                "customerAwsAccountId=null, dimension=null, expirationdate=null, " +
                "createdtime=null, updatedtime=null)")
    }
}
