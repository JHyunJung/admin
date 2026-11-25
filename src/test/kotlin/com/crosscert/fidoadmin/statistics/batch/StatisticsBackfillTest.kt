package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StatisticsBackfillTest {

    private val aggregator: HourlyAggregator = mock()
    private val writer: StatisticsWriter = mock()
    private val backfill = StatisticsBackfill(aggregator, writer)

    private val now = LocalDateTime.of(2026, 11, 25, 10, 15)

    private fun one(): Map<StatKey, StatCounts> =
        java.util.Map.of(StatKey(1L, "svc"), StatCounts().add(LogClassifier.Kind.AUTH, true))

    @Test fun 시작일_0시부터_끝일_23시까지를_본다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())

        val r = backfill.run(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), now)

        verify(writer).loadedGroupbys(StatHour.parse("2026/10/20/00"), StatHour.parse("2026/10/21/23"))
        val dates = argumentCaptor<LocalDate>()
        val hours = argumentCaptor<Collection<StatHour>>()
        verify(aggregator, times(2)).aggregate(dates.capture(), hours.capture())
        assertThat(dates.allValues).containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21))
        assertThat(hours.allValues[0]).hasSize(24)
        assertThat(r.from).isEqualTo(StatHour.parse("2026/10/20/00"))
        assertThat(r.to).isEqualTo(StatHour.parse("2026/10/21/23"))
        assertThat(r.missingHours).isEqualTo(48)
    }

    /** 정시 배치와 같은 규칙이다: 24시간이 지나지 않은 시간은 세지 않는다. */
    @Test fun 끝은_24시간_전_정각을_넘지_않는다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())

        val r = backfill.run(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25), now)

        verify(writer).loadedGroupbys(StatHour.parse("2026/11/24/00"), StatHour.parse("2026/11/24/10"))
        assertThat(r.to).isEqualTo(StatHour.parse("2026/11/24/10"))
        verify(aggregator, never()).aggregate(eq(LocalDate.of(2026, 11, 25)), any())
    }

    @Test fun 이미_있는_시간은_건너뛰고_넣은_행_수를_돌려준다() {
        val done = (0..23).map { "2026/10/20/%02d".format(it) }.filter { it != "2026/10/20/09" }.toSet()
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(done)
        val h9 = StatHour.parse("2026/10/20/09")
        whenever(aggregator.aggregate(any(), any())).thenReturn(java.util.Map.of(h9, one()))
        whenever(writer.write(eq(h9), anyMap())).thenReturn(3)

        val r = backfill.run(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), now)

        verify(aggregator).aggregate(LocalDate.of(2026, 10, 20), listOf(h9))
        assertThat(r.missingHours).isEqualTo(1)
        assertThat(r.rows).isEqualTo(3)
        assertThat(r.failures).isZero()
    }

    @Test fun 실패한_날짜와_시간을_센다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        val h0 = StatHour.parse("2026/10/21/00")
        val h1 = StatHour.parse("2026/10/21/01")
        whenever(aggregator.aggregate(eq(LocalDate.of(2026, 10, 20)), any())).thenThrow(RuntimeException("ORA-xxx"))
        whenever(aggregator.aggregate(eq(LocalDate.of(2026, 10, 21)), any())).thenReturn(java.util.Map.of(h0, one(), h1, one()))
        whenever(writer.write(eq(h0), anyMap())).thenThrow(RuntimeException("boom"))
        whenever(writer.write(eq(h1), anyMap())).thenReturn(1)

        val r = backfill.run(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), now)

        assertThat(r.rows).isEqualTo(1)
        assertThat(r.failures).isEqualTo(2) // 읽지 못한 날짜 하나, 쓰지 못한 시간 하나
    }

    @Test fun 시작일이_끝일보다_늦으면_거절한다() {
        assertThatThrownBy { backfill.run(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20), now) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("시작일")
        verify(writer, never()).loadedGroupbys(any(), any())
    }

    @Test fun 아직_집계할_수_없는_기간이면_거절한다() {
        assertThatThrownBy { backfill.run(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25), now) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("2026-11-24 10시")
    }

    /** 연도를 잘못 넣은 실수(2006-…)로 수천 일을 훑지 않게 한다. */
    @Test fun 기간이_366일을_넘으면_거절한다() {
        assertThatThrownBy { backfill.run(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 20), now) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("366일")
    }
}
