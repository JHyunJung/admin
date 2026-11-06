package com.crosscert.fidoadmin.common

/*
 * Java String 과 같은 규칙의 공백 처리. Kotlin 의 trim()/isBlank() 는 유니코드 공백(NBSP 등)까지 공백으로 보아
 * Java 의 String.trim()(코드 ≤ U+0020)·String.isBlank()(Character.isWhitespace) 와 결과가 다르다.
 * Java 에서 옮긴 코드가 같은 입력에 같은 결과를 내도록 이 함수들을 쓴다.
 */

/** Java `String.trim()` 과 같다: 앞뒤의 코드 ≤ U+0020 문자를 지운다. */
internal fun String.javaTrim(): String = trim { it <= ' ' }

/** Java `String.isBlank()` 와 같다: 비었거나 모두 [Character.isWhitespace] 인 문자. */
internal fun CharSequence.javaIsBlank(): Boolean = all { Character.isWhitespace(it) }

/** `null` 이거나 Java `String.isBlank()` 기준으로 빈 문자열. */
internal fun CharSequence?.isNullOrJavaBlank(): Boolean = this == null || javaIsBlank()
