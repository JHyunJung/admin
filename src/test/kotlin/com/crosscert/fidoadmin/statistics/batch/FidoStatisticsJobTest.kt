package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FidoStatisticsJobTest {

    private val aggregator: HourlyAggregator = mock()
    private val writer: StatisticsWriter = mock()

    private fun job(catchUpHours: Int): FidoStatisticsJob =
        FidoStatisticsJob(StatisticsBatchProperties(true, "0 15 * * * *", catchUpHours), aggregator, writer)

    companion object {
        private fun one(): Map<StatKey, StatCounts> =
            java.util.Map.of(StatKey(1L, "svc"), StatCounts().add(LogClassifier.Kind.AUTH, true))
    }

    /** 2026-11-05 10:15 실행 → 가장 늦은 대상은 24시간 전 정각(11/04 10시), 3시간이면 08~10시. */
    @Test fun 대상은_24시간_전_정각까지다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15))

        verify(writer).loadedGroupbys(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/10"))
        verify(aggregator).aggregate(LocalDate.of(2026, 11, 4),
            listOf(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/09"), StatHour.parse("2026/11/04/10")))
    }

    @Test fun 이미_있는_시간은_건너뛴다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf("2026/11/04/09"))
        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15))

        verify(aggregator).aggregate(LocalDate.of(2026, 11, 4),
            listOf(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/10")))
    }

    @Test fun 모두_있으면_아무것도_읽지_않는다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf("2026/11/04/10"))
        job(1).run(LocalDateTime.of(2026, 11, 5, 10, 15))
        verify(aggregator, never()).aggregate(any(), any())
    }

    /** 자정을 걸치면 날짜 테이블마다 한 번씩, 오래된 날짜부터 읽는다. */
    @Test fun 날짜별로_한번씩_오래된_날짜부터_읽는다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        job(3).run(LocalDateTime.of(2026, 11, 5, 1, 15)) // 대상 11/03 23시, 11/04 00·01시

        val dates = argumentCaptor<LocalDate>()
        val hours = argumentCaptor<Collection<StatHour>>()
        verify(aggregator, times(2)).aggregate(dates.capture(), hours.capture())
        assertThat(dates.allValues).containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4))
        assertThat(hours.allValues[0]).containsExactly(StatHour.parse("2026/11/03/23"))
        assertThat(hours.allValues[1]).containsExactly(StatHour.parse("2026/11/04/00"), StatHour.parse("2026/11/04/01"))
    }

    @Test fun 로그가_없는_시간은_쓰지_않는다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        val h9 = StatHour.parse("2026/11/04/09")
        whenever(aggregator.aggregate(any(), any())).thenReturn(java.util.Map.of(h9, one()))

        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15))

        verify(writer).write(eq(h9), anyMap())
        verify(writer, times(1)).write(any(), anyMap())
    }

    @Test fun 한_시간이_실패해도_다음_시간은_계속한다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        val h8 = StatHour.parse("2026/11/04/08")
        val h9 = StatHour.parse("2026/11/04/09")
        whenever(aggregator.aggregate(any(), any())).thenReturn(java.util.Map.of(h8, one(), h9, one()))
        whenever(writer.write(eq(h8), anyMap())).thenThrow(RuntimeException("boom"))
        whenever(writer.write(eq(h9), anyMap())).thenReturn(1)

        assertThat(job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15))).isEqualTo(1)
        verify(writer).write(eq(h9), anyMap())
    }

    @Test fun 한_날짜를_읽지_못해도_다음_날짜는_계속한다() {
        whenever(writer.loadedGroupbys(any(), any())).thenReturn(setOf())
        val h0 = StatHour.parse("2026/11/04/00")
        whenever(aggregator.aggregate(eq(LocalDate.of(2026, 11, 3)), any())).thenThrow(RuntimeException("ORA-xxx"))
        whenever(aggregator.aggregate(eq(LocalDate.of(2026, 11, 4)), any())).thenReturn(java.util.Map.of(h0, one()))
        whenever(writer.write(eq(h0), anyMap())).thenReturn(1)

        assertThat(job(2).run(LocalDateTime.of(2026, 11, 5, 0, 15))).isEqualTo(1)
    }

    @Test fun 이전_실행이_돌고_있으면_건너뛴다() {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        whenever(writer.loadedGroupbys(any(), any())).thenAnswer {
            calls.incrementAndGet()
            inside.countDown()
            release.await(5, TimeUnit.SECONDS)
            setOf("2026/11/04/10")
        }
        val job = job(1)
        val first = Thread { job.run(LocalDateTime.of(2026, 11, 5, 10, 15)) }
        first.start()
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue()

        assertThat(job.run(LocalDateTime.of(2026, 11, 5, 10, 15))).isEqualTo(-1)

        release.countDown()
        first.join(5000)
        assertThat(calls.get()).isEqualTo(1)
        assertThat(job.run(LocalDateTime.of(2026, 11, 5, 10, 15))).isZero() // 끝난 뒤에는 다시 돈다
    }
}
