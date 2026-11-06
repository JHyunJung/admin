package com.crosscert.fidoadmin.statistics.batch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 집계 단위인 한 시간 구간 `[start, start+1h)`. 시각은 KST 벽시계 값이다.
 *
 * [groupby] 는 FIDO_STATISTICS.GROUPBY 표기(`yyyy/MM/dd/HH`, 24시간제)다.
 * 구 배치가 SQL `TO_CHAR(CREATEDTIME,'yyyy/MM/dd/hh24')` 로 만든 값과 같다.
 *
 * Java record 였다. 생성자가 시각을 정각으로 자르므로 data class 대신 일반 클래스로 두고
 * record 와 같은 접근자(`start()`)·equals·hashCode·toString 을 손으로 맞춘다.
 */
class StatHour(start: LocalDateTime?) : Comparable<StatHour> {

    private val start: LocalDateTime =
        (start ?: throw IllegalArgumentException("start")).truncatedTo(ChronoUnit.HOURS)

    fun start(): LocalDateTime = start

    fun groupby(): String = start.format(GROUPBY)

    fun end(): LocalDateTime = start.plusHours(1)

    /** 이 시간의 로그가 들어 있는 날짜 테이블의 날짜. */
    fun date(): LocalDate = start.toLocalDate()

    fun plusHours(hours: Long): StatHour = StatHour(start.plusHours(hours))

    override fun compareTo(other: StatHour): Int = start.compareTo(other.start)

    override fun equals(other: Any?): Boolean = other is StatHour && start == other.start

    override fun hashCode(): Int = start.hashCode()

    override fun toString(): String = "StatHour[start=$start]"

    companion object {
        @JvmField
        internal val GROUPBY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")

        @JvmStatic
        fun of(time: LocalDateTime?): StatHour = StatHour(time)

        @JvmStatic
        fun parse(groupby: String): StatHour = StatHour(
            LocalDateTime.parse("$groupby/00/00", DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")))
    }
}
