package com.crosscert.fidoadmin.common

/**
 * 텍스트 로그에 넣는 외부·사용자 값 정제. 개행이 남으면 값 하나로 가짜 로그 줄을 만들 수 있다.
 *
 * 제어문자(Cc — CR·LF·탭·NEL 포함)와 줄·문단 구분자(U+2028·U+2029)를 글자마다 `_` 로 바꾼다
 * ([UserIdRule] 이 입력에서 막는 문자와 같은 범위). 저장 데이터(감사 메시지 등)에는 쓰지 않는다 — 로그 전용이다.
 */
object LogText {
    private val UNSAFE = Regex("[\\p{Cc}\\u2028\\u2029]")

    @JvmStatic
    fun oneLine(value: String?): String? = value?.replace(UNSAFE, "_")
}
