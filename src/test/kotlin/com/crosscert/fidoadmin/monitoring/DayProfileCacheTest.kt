package com.crosscert.fidoadmin.monitoring

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.QueryTimeoutException

class DayProfileCacheTest {

    /** 시각을 앞으로 돌려 가며 쓰는 테스트용 시계. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) {
            now = now.plus(d)
        }
    }

    private val today = LocalDate.of(2026, 11, 25)
    private val clock = MutableClock(Instant.parse("2026-11-25T12:00:00Z"))
    private val reader = mock<DayProfileReader>()
    private val props = MonitoringProperties(true, true, 20, 0.5, 10, 5.0, 100, 3, 60, 10)
    private val cache = DayProfileCache(reader, props, clock)

    private fun profile(date: LocalDate) = DayProfile.empty(date)

    @Test
    fun 당일은_보관_시간_안에_다시_읽지_않는다() {
        whenever(reader.read(today)).doReturn(profile(today))

        cache.get(today)
        clock.advance(Duration.ofSeconds(59))
        cache.get(today)

        verify(reader, times(1)).read(today)
    }

    @Test
    fun 당일은_보관_시간이_지나면_다시_읽는다() {
        whenever(reader.read(today)).doReturn(profile(today))

        cache.get(today)
        clock.advance(Duration.ofSeconds(61))
        cache.get(today)

        verify(reader, times(2)).read(today)
    }

    @Test
    fun 지난_날짜는_계속_보관한다() {
        val yesterday = today.minusDays(1)
        whenever(reader.read(yesterday)).doReturn(profile(yesterday))

        cache.get(yesterday)
        clock.advance(Duration.ofDays(1))
        cache.get(yesterday)

        verify(reader, times(1)).read(yesterday)
    }

    @Test
    fun 테이블이_없으면_빈_프로파일이고_보관한다() {
        val yesterday = today.minusDays(1)
        whenever(reader.read(yesterday)).doReturn(null)

        val p = cache.get(yesterday)
        cache.get(yesterday)

        assertThat(p).isNotNull
        assertThat(p!!.total(1)).isEqualTo(0)
        verify(reader, times(1)).read(yesterday)
    }

    @Test
    fun 실패는_null이고_보관하지_않는다() {
        whenever(reader.read(today)).doThrow(QueryTimeoutException("timeout"))

        assertThat(cache.get(today)).isNull()
        assertThat(cache.get(today)).isNull()

        verify(reader, times(2)).read(today)
    }

    @Test
    fun 날짜가_바뀌면_어제는_지난_날짜로_남는다() {
        whenever(reader.read(any())).thenAnswer { profile(it.getArgument(0)) }

        cache.get(today)                        // 당일에 읽은 것: 그날의 끝이 빠져 있을 수 있다
        clock.advance(Duration.ofDays(1))       // 날짜가 바뀐다
        cache.get(today)                        // 이제 지난 날짜지만 덜 찬 보관본이라 한 번 더 읽는다
        cache.get(today)                        // 그 뒤로는 다시 읽지 않는다
        clock.advance(Duration.ofDays(1))
        cache.get(today)

        verify(reader, times(2)).read(today)
    }

    @Test
    fun 여드레_넘은_항목은_버린다() {
        val d = today.minusDays(1)
        whenever(reader.read(d)).doReturn(profile(d))
        cache.get(d)

        clock.advance(Duration.ofDays(7))       // d 는 오늘보다 8일 전: 아직 보관
        cache.get(d)
        verify(reader, times(1)).read(d)

        clock.advance(Duration.ofDays(1))       // 9일 전: 버려지고 다시 읽는다
        cache.get(d)
        verify(reader, times(2)).read(d)
    }
}
