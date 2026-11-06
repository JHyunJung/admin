package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.system.trimJava

/**
 * FIDO 서버 설정 화면의 위젯 범위·선택지(슬라이더, 저장 기간 드롭다운). 다만 **저장된 값이 범위·목록 밖이어도 깎지 않는다** — 화면을 열고 다른 항목만
 * 바꿔 저장했는데 이 값이 조용히 바뀌면 FIDO 서버 동작이 달라진다.
 */
object FidoSettingOptions {

    const val CHALLENGE_MIN = 10
    const val CHALLENGE_MAX = 180
    const val CHALLENGE_STEP = 10

    /** TC원문 저장 기간(일). 9999 는 "영구저장". */
    const val TC_FOREVER = "9999"

    /** 드롭다운 선택지 하나. 템플릿이 `o.value()`·`o.label()` 로 읽으므로 record 로 둔다. */
    @JvmRecord
    data class Option(val value: String, val label: String)

    private val TC_BASE: List<Option> = listOf(
        Option("30", "30일"),
        Option("90", "90일"),
        Option("180", "180일"),
        Option("365", "1년"),
        Option("1095", "3년"),
        Option("1825", "5년"),
        Option(TC_FOREVER, "영구저장"),
    )

    /** 슬라이더 최대값. 저장값이 기본 최대보다 크면 그 값까지 늘린다. */
    @JvmStatic
    fun challengeMax(stored: String?): Int {
        val v = parse(stored)
        return if (v == null) CHALLENGE_MAX else maxOf(CHALLENGE_MAX, v)
    }

    /** 슬라이더 현재값. 숫자가 아니면 기본값(180). */
    @JvmStatic
    fun challengeValue(stored: String?): Int = parse(stored) ?: CHALLENGE_MAX

    /** TC 저장 기간 선택지. 저장값이 목록에 없으면 "N일" 로 끼워 넣는다(값을 잃지 않게). */
    @JvmStatic
    fun tcOptions(stored: String?): List<Option> {
        val v = stored?.trimJava()
        if (v == null || v.isEmpty() || TC_BASE.any { it.value == v }) return TC_BASE
        val list = ArrayList(TC_BASE)
        list.add(0, Option(v, v + "일"))
        return list
    }

    private fun parse(s: String?): Int? {
        if (s == null) return null
        return try {
            s.trimJava().toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
