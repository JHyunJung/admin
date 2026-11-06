package com.crosscert.fidoadmin.system.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CcfaSystemPropIdTest {

    @Test fun toPathValueJoinsKeyAndCompany() {
        assertThat(CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toPathValue()).isEqualTo("PW_FAIL_LIMIT@0")
        assertThat(CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toString()).isEqualTo("PW_FAIL_LIMIT@0")
    }

    @Test fun parseRoundTrips() {
        val id = CcfaSystemPropId.parse("PW_FAIL_LIMIT@0")
        assertThat(id.propKey).isEqualTo("PW_FAIL_LIMIT")
        assertThat(id.companyIdx).isEqualTo(0L)
        assertThat(id).isEqualTo(CcfaSystemPropId("PW_FAIL_LIMIT", 0L))
    }

    /** 키 자체에 '@' 가 있어도 마지막 '@' 뒤가 COMPANY_IDX 다. */
    @Test fun parseSplitsAtLastAt() {
        val id = CcfaSystemPropId.parse("a@b@12")
        assertThat(id.propKey).isEqualTo("a@b")
        assertThat(id.companyIdx).isEqualTo(12L)
        assertThat(id.toPathValue()).isEqualTo("a@b@12")
    }

    @Test fun parseRejectsMalformed() {
        assertThatThrownBy { CcfaSystemPropId.parse("NO_AT") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CcfaSystemPropId.parse("@0") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CcfaSystemPropId.parse("KEY@x") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CcfaSystemPropId.parse("KEY@") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CcfaSystemPropId.parse(null) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
