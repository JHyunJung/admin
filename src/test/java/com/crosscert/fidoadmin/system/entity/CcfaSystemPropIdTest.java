package com.crosscert.fidoadmin.system.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CcfaSystemPropIdTest {

    @Test void toPathValueJoinsKeyAndCompany() {
        assertThat(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toPathValue()).isEqualTo("PW_FAIL_LIMIT@0");
        assertThat(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toString()).isEqualTo("PW_FAIL_LIMIT@0");
    }

    @Test void parseRoundTrips() {
        CcfaSystemPropId id = CcfaSystemPropId.parse("PW_FAIL_LIMIT@0");
        assertThat(id.getPropKey()).isEqualTo("PW_FAIL_LIMIT");
        assertThat(id.getCompanyIdx()).isEqualTo(0L);
        assertThat(id).isEqualTo(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L));
    }

    /** 키 자체에 '@' 가 있어도 마지막 '@' 뒤가 COMPANY_IDX 다. */
    @Test void parseSplitsAtLastAt() {
        CcfaSystemPropId id = CcfaSystemPropId.parse("a@b@12");
        assertThat(id.getPropKey()).isEqualTo("a@b");
        assertThat(id.getCompanyIdx()).isEqualTo(12L);
        assertThat(id.toPathValue()).isEqualTo("a@b@12");
    }

    @Test void parseRejectsMalformed() {
        assertThatThrownBy(() -> CcfaSystemPropId.parse("NO_AT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("@0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("KEY@x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("KEY@")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
