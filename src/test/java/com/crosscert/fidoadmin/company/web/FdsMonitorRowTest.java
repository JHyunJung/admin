package com.crosscert.fidoadmin.company.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FdsMonitorRowTest {

    LocalDateTime t = LocalDateTime.of(2026, 9, 27, 10, 0, 0);

    /** 간격은 소수점을 유지한다. SQL 에서 초로 바꾸면 밀리초가 날아간다. */
    @Test void gapsKeepFractionalSeconds() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, t.minusNanos(1_500_000_000L), t.plusSeconds(3), 4);
        assertThat(r.gapBeforeSec()).isEqualTo(1.5);
        assertThat(r.gapAfterSec()).isEqualTo(3.0);
        assertThat(r.repeats()).isEqualTo(4);
    }

    /** 같은 시각의 두 요청은 간격 0 — 빈칸이나 null 이 아니라 0 으로 보여야 한다. */
    @Test void equalTimestampsGiveZeroGap() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, t, null, 2);
        assertThat(r.gapBeforeSec()).isEqualTo(0.0);
        assertThat(r.gapAfterSec()).isNull();
    }

    @Test void missingNeighboursGiveNull() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, null, null, 1);
        assertThat(r.gapBeforeSec()).isNull();
        assertThat(r.gapAfterSec()).isNull();
    }
}
