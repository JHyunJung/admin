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
    /** 컬럼마다 다른 값이라 두 컬럼이 뒤바뀌면 바로 드러난다. */
    @Test fun eachColumnSumsIntoItsOwnField() {
        val t = StatTotals.of(listOf(
            DailyStat(LocalDate.of(2026, 9, 1), 1, 2, 3, 4, 5, 6, 7, 8),
            DailyStat(LocalDate.of(2026, 9, 2), 10, 20, 30, 40, 50, 60, 70, 80)))
        assertThat(t.authS).isEqualTo(11L)
        assertThat(t.authF).isEqualTo(22L)
        assertThat(t.tcS).isEqualTo(33L)
        assertThat(t.tcF).isEqualTo(44L)
        assertThat(t.regS).isEqualTo(55L)
        assertThat(t.regF).isEqualTo(66L)
        assertThat(t.deregS).isEqualTo(77L)
        assertThat(t.deregF).isEqualTo(88L)
    }
    @Test fun emptyIsZeroAndRateIsZero() {
        assertThat(StatTotals.of(listOf()).authSuccessRate()).isZero()
    }
}
