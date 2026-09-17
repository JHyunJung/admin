package com.crosscert.fidoadmin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatTotalsTest {
    @Test void sumsEveryColumn() {
        StatTotals t = StatTotals.of(List.of(
            new DailyStat(LocalDate.of(2026, 9, 1), 10, 1, 2, 0, 3, 0, 1, 0),
            new DailyStat(LocalDate.of(2026, 9, 2), 5, 2, 1, 1, 0, 1, 0, 1)));
        assertThat(t).isEqualTo(new StatTotals(15, 3, 3, 1, 3, 1, 1, 1));
        assertThat(t.authTotal()).isEqualTo(18);
        assertThat(t.authSuccessRate()).isEqualTo(83.3);
    }
    @Test void emptyIsZeroAndRateIsZero() {
        assertThat(StatTotals.of(List.of()).authSuccessRate()).isZero();
    }
}
