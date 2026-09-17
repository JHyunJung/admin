package com.crosscert.fidoadmin.fido2.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EpochSecondsTest {

    /** 시드 값 1767225600 = 2026-01-01T00:00:00Z = 서울 2026-01-01 09:00. */
    @Test void seedEpochIsSeoulNineOclock() {
        assertThat(EpochSeconds.fromEpoch(1767225600L)).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        assertThat(EpochSeconds.toEpoch(LocalDateTime.of(2026, 1, 1, 9, 0))).isEqualTo(1767225600L);
    }

    @Test void roundTrip() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 17, 13, 45);
        assertThat(EpochSeconds.fromEpoch(EpochSeconds.toEpoch(t))).isEqualTo(t);
    }

    @Test void nullPassesThrough() {
        assertThat(EpochSeconds.toEpoch(null)).isNull();
        assertThat(EpochSeconds.fromEpoch(null)).isNull();
    }
}
