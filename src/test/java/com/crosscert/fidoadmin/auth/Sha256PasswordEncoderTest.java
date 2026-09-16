package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256PasswordEncoderTest {

    private final Sha256PasswordEncoder encoder = new Sha256PasswordEncoder();

    @Test
    void encodeProducesLowercaseHex64() {
        // printf 'Admin1234!' | shasum -a 256
        assertThat(encoder.encode("Admin1234!"))
            .isEqualTo("5ce41ada64f1e8ffb0acfaafa622b141438f3a5777785e7f0b830fb73e40d3d6");
    }

    @Test
    void matchesIgnoresCaseOfStoredHash() {
        String upper = "5CE41ADA64F1E8FFB0ACFAAFA622B141438F3A5777785E7F0B830FB73E40D3D6";
        assertThat(encoder.matches("Admin1234!", upper)).isTrue();
        assertThat(encoder.matches("wrong", upper)).isFalse();
    }

    @Test
    void matchesReturnsFalseForNullOrBlankStored() {
        assertThat(encoder.matches("Admin1234!", null)).isFalse();
        assertThat(encoder.matches("Admin1234!", "")).isFalse();
    }

    @Test
    void sha256HexHandlesKorean() {
        // printf '%s' '한글' | shasum -a 256 — UTF-8 인코딩을 고정한다
        assertThat(Sha256PasswordEncoder.sha256Hex("한글"))
            .isEqualTo("bd87f9bb68b67d2fa1cb82b6751820e946d5b1316d25d5fd96512fb4be44a2a8");
    }
}
