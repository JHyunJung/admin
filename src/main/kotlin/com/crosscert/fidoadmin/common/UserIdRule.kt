package com.crosscert.fidoadmin.common

/**
 * USER_ID 입력 규칙(가입 신청·운영자 등록 공통). 아이디는 로그·감사 메시지에 그대로 들어가므로
 * 안쪽의 개행·제어문자(Cc)와 줄·문단 구분자(U+2028·U+2029)를 막는다 — 막지 않으면 가짜 로그 줄을 만들 수 있다.
 *
 * 앞뒤의 공백·제어문자(U+0020 이하)는 지금처럼 받는다. 두 폼 모두 저장 전에 Java trim 으로 떼어 내므로
 * 남지 않는다. 한글·이메일형 등 그 밖의 문자는 그대로 받는다.
 */
object UserIdRule {
    /** Bean Validation `@Pattern` 용(전체 일치). 앞뒤 U+0020 이하 + 제어문자 없는 본문 + 앞뒤 U+0020 이하. */
    const val PATTERN = "[\\x00-\\x20]*[^\\p{Cc}\\p{Zl}\\p{Zp}]*[\\x00-\\x20]*"
    const val MESSAGE = "아이디에는 줄바꿈·탭 같은 제어문자를 쓸 수 없습니다."
}
