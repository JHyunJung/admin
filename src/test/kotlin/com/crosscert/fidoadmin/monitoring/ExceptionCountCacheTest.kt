package com.crosscert.fidoadmin.monitoring

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.QueryTimeoutException

class ExceptionCountCacheTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) {
            now = now.plus(d)
        }
    }

    private val clock = MutableClock(Instant.parse("2026-11-25T05:30:00Z"))
    private val reader = mock<ExceptionCountReader>()
    private val props = MonitoringProperties(true, true, 20, 0.5, 10, 5.0, 100, 3, 60, 10)
    private val cache = ExceptionCountCache(reader, props, clock)

    private val t1430 = LocalDateTime.of(2026, 11, 25, 14, 30)
    private val counts = mapOf(1L to ErrorCounts(3, 7))

    @Test
    fun 보관_시간_안에는_다시_읽지_않고_보관한_값을_준다() {
        whenever(reader.read(any())).doReturn(counts)

        assertThat(cache.get(t1430)).isEqualTo(counts)
        clock.advance(Duration.ofSeconds(59))
        assertThat(cache.get(t1430.plusMinutes(1))).isEqualTo(counts)

        verify(reader, times(1)).read(any())
        verify(reader).read(t1430)
    }

    @Test
    fun 보관_시간이_지나면_다시_읽는다() {
        whenever(reader.read(any())).doReturn(counts)

        cache.get(t1430)
        clock.advance(Duration.ofSeconds(61))
        cache.get(t1430.plusMinutes(1))

        verify(reader).read(t1430)
        verify(reader).read(t1430.plusMinutes(1))
    }

    @Test
    fun 실패는_null이고_보관_시간_동안은_다시_읽지_않는다() {
        whenever(reader.read(any())).doThrow(QueryTimeoutException("timeout")).thenReturn(counts)

        assertThat(cache.get(t1430)).isNull()
        clock.advance(Duration.ofSeconds(59))
        assertThat(cache.get(t1430)).isNull()
        verify(reader, times(1)).read(any())

        clock.advance(Duration.ofSeconds(2))
        assertThat(cache.get(t1430)).isEqualTo(counts)
        verify(reader, times(2)).read(any())
    }

    /** 보관 기준 시각은 조회가 끝난 때다. 시작 때로 잡으면 조회가 보관 시간보다 오래 걸릴 때 저장하자마자 만료된다. */
    @Test
    fun 조회가_보관_시간보다_오래_걸려도_끝난_뒤부터_보관한다() {
        val short = MonitoringProperties(true, true, 20, 0.5, 10, 5.0, 100, 3, 5, 10)
        val slow = ExceptionCountCache(reader, short, clock)
        whenever(reader.read(any())).doAnswer {
            clock.advance(Duration.ofSeconds(10))
            throw QueryTimeoutException("timeout")
        }

        assertThat(slow.get(t1430)).isNull()
        clock.advance(Duration.ofSeconds(4))
        assertThat(slow.get(t1430)).isNull()

        verify(reader, times(1)).read(any())
    }

    /** '예외 오늘'은 날짜에 묶인 값이라, 자정을 넘기면 보관 시간이 남아도 전날 누계를 주지 않는다. */
    @Test
    fun 자정을_넘기면_보관_시간_안이어도_다시_읽는다() {
        val night = MutableClock(Instant.parse("2026-11-25T23:59:30Z"))
        val c = ExceptionCountCache(reader, props, night)
        whenever(reader.read(any())).doReturn(counts)

        c.get(LocalDateTime.of(2026, 11, 25, 23, 59))
        night.advance(Duration.ofSeconds(40))
        c.get(LocalDateTime.of(2026, 11, 26, 0, 0))

        verify(reader, times(2)).read(any())
    }
}
