package com.crosscert.fidoadmin.auth

/*
 * Java 에서 옮긴 코드가 String.trim()/isBlank() 의 Java 의미를 그대로 유지하도록 두는 확장 함수.
 *
 * Kotlin 표준 trim()/isBlank() 는 Char.isWhitespace() 를 써서 NBSP(U+00A0)·U+2007·U+202F 같은
 * 유니코드 공백까지 떼거나 공백으로 본다. Java 의 trim() 은 코드값 U+0020 이하만 떼고,
 * isBlank() 는 Character.isWhitespace() 만 본다. 로그인 ID 정규화(Spring Security 필터의 trim 과
 * 일치해야 한다)와 비밀번호 입력 여부 판정이 이 차이로 달라지지 않게 한다.
 */

/** Java `String.trim()` 과 같다 — 앞뒤의 코드값 U+0020 이하 문자만 뗀다. */
internal fun String.javaTrim(): String = trim { it <= ' ' }

/** Java `String.isBlank()` 와 같다 — 모든 문자가 `Character.isWhitespace()` 이면 참. */
internal fun String.isJavaBlank(): Boolean = all { Character.isWhitespace(it) }
