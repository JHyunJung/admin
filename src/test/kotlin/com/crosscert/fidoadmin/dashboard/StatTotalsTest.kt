package com.crosscert.fidoadmin.dashboard

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatTotalsTest {
    @Test fun sumsEveryColumn() {
        val t = StatTotals.of(listOf(
            DailyStat(LocalDate.of(2026, 9, 1), 10, 1, 2, 0, 3, 0, 1, 0),
            DailyStat(LocalDate.of(2026, 9, 2), 5, 2, 1, 1, 0, 1, 0, 1)))
        assertThat(t).isEqualTo(StatTotals(15, 3, 3, 1, 3, 1, 1, 1))
        assertThat(t.authTotal()).isEqualTo(18L)
        assertThat(t.authSuccessRate()).isEqualTo(83.3)
    }
    @Test fun emptyIsZeroAndRateIsZero() {
        assertThat(StatTotals.of(listOf()).authSuccessRate()).isZero()
    }
}
