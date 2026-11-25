package com.crosscert.fidoadmin.monitoring

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 관제 화면의 숫자·문구 서식. 템플릿에 긴 식을 쓰지 않으려고 순수 함수로 뺐다(모델 속성 `fmt`).
 *
 * 값이 null 인 칸의 문구는 그 값이 속한 열의 [ColumnState] 가 정한다 — 읽지 못했으면 "확인 불가",
 * 꺼 두었으면 "사용 안 함", 둘 다 아니면(기준일 없음·오늘 로그 없음) "-". 0 과 섞이지 않게 하려는 구분이다.
 * 로케일은 서버 설정에 흔들리지 않도록 고정한다.
 */
object MonitoringView {

    private const val NONE = "-"
    private const val NOT_JUDGED = "끊김·감소를 판정하지 않습니다."
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun count(v: Long?, state: ColumnState): String = if (v == null) missing(state) else comma(v)

    fun time(v: LocalDateTime?, state: ColumnState): String = if (v == null) missing(state) else v.format(CLOCK)

    /** 항상 값이 있는 건수(통계 합계). */
    fun number(v: Long): String = comma(v)

    /** 증감: 부호와 `%`. 0 은 부호 없이. */
    fun pct(v: Double?): String = if (v == null) NONE else sign(v) + "%"

    /** 성공률 변화: 부호와 `%p`. */
    fun points(v: Double?): String = if (v == null) NONE else sign(v) + "%p"

    fun rate(v: Double?): String = if (v == null) NONE else String.format(Locale.US, "%,.1f%%", v)

    /** 순증: 부호를 붙인 천 단위 숫자. */
    fun signed(v: Long): String = if (v > 0) "+" + comma(v) else comma(v)

    /** `2026-11-24 (화)` */
    fun day(d: LocalDate): String = "$d (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    /**
     * 상태 칸을 배지 없이 글자로만 쓸 때의 글자. null 이면 상태 배지를 쓴다.
     * 로그가 없는 줄(NONE)은 배지가 아니라 "-" 다. 로그 열을 꺼서 모르는 상태는 고장이 아니므로
     * "확인 불가" 배지 대신 "사용 안 함" 이다 — "확인 불가" 배지는 실제로 읽지 못했을 때만 쓴다.
     */
    fun plainStatus(status: LiveStatus, logs: ColumnState): String? = when {
        status == LiveStatus.NONE -> NONE
        status == LiveStatus.UNKNOWN && logs == ColumnState.DISABLED -> missing(ColumnState.DISABLED)
        else -> null
    }

    /** 표 아래 기준일 문구. 기준일이 없을 때는 찾아보고 없었는지, 찾을 수 없었는지를 가른다. */
    fun baselineNote(logs: ColumnState, baseline: ColumnState, date: LocalDate?): String = when {
        logs == ColumnState.DISABLED -> "로그 조회 사용 안 함 — $NOT_JUDGED"
        logs == ColumnState.FAILED -> "로그 확인 불가 — $NOT_JUDGED"
        date != null -> "기준일: ${day(date)}"
        baseline == ColumnState.FAILED -> "기준일 확인 불가 — $NOT_JUDGED"
        else -> "기준일 없음 — $NOT_JUDGED"
    }

    private fun comma(v: Long): String = String.format(Locale.US, "%,d", v)

    private fun sign(v: Double): String = (if (v > 0) "+" else "") + String.format(Locale.US, "%,.1f", v)

    private fun missing(state: ColumnState): String = when (state) {
        ColumnState.FAILED -> "확인 불가"
        ColumnState.DISABLED -> "사용 안 함"
        ColumnState.OK -> NONE
    }
}
