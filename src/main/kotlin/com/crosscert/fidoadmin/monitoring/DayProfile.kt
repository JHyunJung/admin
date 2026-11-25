package com.crosscert.fidoadmin.monitoring

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 하루치 로그 건수를 고객사×분(0..1439) 으로 담는 값 객체. 날짜 테이블 한 장을 한 번 읽어 만들고,
 * 그 위에서 임의 구간의 합을 DB 재조회 없이 구한다.
 *
 * 분 단위로 자른다: 구간의 `from`·`to` 는 분으로 내림하고, 이 날짜 밖의 부분은 세지 않는다.
 * 자정을 넘는 구간은 호출하는 쪽이 두 날짜의 [count] 를 더한다.
 */
class DayProfile(
    val date: LocalDate,
    private val counts: Map<Long, LongArray>,
    private val last: Map<Long, LocalDateTime>,
) {

    fun total(companyIdx: Long): Long = counts[companyIdx]?.sum() ?: 0L

    fun lastLog(companyIdx: Long): LocalDateTime? = last[companyIdx]

    /** [from, to) 중 이 날짜에 속하는 부분만 센다. 범위가 이 날짜와 겹치지 않으면 0. */
    fun count(companyIdx: Long, from: LocalDateTime, to: LocalDateTime): Long {
        val minutes = counts[companyIdx] ?: return 0L
        return sum(minutes, minuteIndex(from), minuteIndex(to))
    }

    /** 어느 고객사든 [from, to) 에 로그가 있는가. */
    fun anyBetween(from: LocalDateTime, to: LocalDateTime): Boolean {
        val s = minuteIndex(from)
        val e = minuteIndex(to)
        return counts.values.any { sum(it, s, e) > 0 }
    }

    /** 이 날짜 0시부터의 분(내림)을 0..1440 으로 자른다. */
    private fun minuteIndex(t: LocalDateTime): Int =
        Duration.between(date.atStartOfDay(), t.truncatedTo(ChronoUnit.MINUTES)).toMinutes()
            .coerceIn(0, MINUTES_PER_DAY.toLong()).toInt()

    private fun sum(minutes: LongArray, from: Int, to: Int): Long {
        var s = 0L
        for (i in from until to) s += minutes[i]
        return s
    }

    class Builder(val date: LocalDate) {
        private val counts = HashMap<Long, LongArray>()
        private val lasts = HashMap<Long, LocalDateTime>()

        fun add(companyIdx: Long, minuteOfDay: Int, n: Long, last: LocalDateTime): Builder {
            require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay 는 0..1439 여야 한다: $minuteOfDay" }
            counts.getOrPut(companyIdx) { LongArray(MINUTES_PER_DAY) }[minuteOfDay] += n
            lasts.merge(companyIdx, last) { a, b -> if (b.isAfter(a)) b else a }
            return this
        }

        fun build(): DayProfile = DayProfile(date, counts.toMap(), lasts.toMap())
    }

    companion object {
        private const val MINUTES_PER_DAY = 1440

        fun empty(date: LocalDate): DayProfile = DayProfile(date, emptyMap(), emptyMap())
    }
}
