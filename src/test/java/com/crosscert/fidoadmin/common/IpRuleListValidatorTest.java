package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpRuleListValidatorTest {

    @Test void accepts() {
        for (String v : new String[] {"10.0.0.1", "0.0.0.0", "255.255.255.255", "10.0.0.0/8", "10.0.0.0/0", "10.0.0.0/32",
            "10.0.0.1~10.0.0.9", "10.0.0.1~10.0.0.1", "10.0.0.1,192.168.0.0/16,1.1.1.1~1.1.1.5",
            " 10.0.0.1 , 10.0.0.0/8 "}) {
            assertThat(IpRuleListValidator.firstInvalid(v)).as(v).isEmpty();
        }
    }

    @Test void blankIsValid() {
        assertThat(IpRuleListValidator.firstInvalid(null)).isEmpty();
        assertThat(IpRuleListValidator.firstInvalid("")).isEmpty();
        assertThat(IpRuleListValidator.firstInvalid("   ")).isEmpty();
    }

    @Test void rejectsOctetOver255() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.256")).contains("10.0.0.256"); }
    @Test void rejectsPrefixOver32() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.0/33")).contains("10.0.0.0/33"); }
    @Test void rejectsReversedRange() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.9~10.0.0.1")).contains("10.0.0.9~10.0.0.1"); }
    @Test void rejectsEmptyItem() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,,10.0.0.2")).contains(""); }
    @Test void rejectsTrailingComma() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,")).contains(""); }
    @Test void rejectsIpv6() { assertThat(IpRuleListValidator.firstInvalid("::1")).contains("::1"); }
    @Test void rejectsGarbage() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,abc")).contains("abc"); }
    @Test void rejectsThreeOctets() { assertThat(IpRuleListValidator.firstInvalid("10.0.0")).contains("10.0.0"); }
    @Test void reportsFirstInvalidOnly() { assertThat(IpRuleListValidator.firstInvalid("x,y")).contains("x"); }
}
