package com.crosscert.fidoadmin.system

/*
 * Java String 메서드와 똑같이 동작하는 trim·isBlank. Java→Kotlin 전환에서 동작을 바꾸지 않으려고 둔다.
 *
 * Kotlin 의 trim()/isBlank() 는 Char.isWhitespace(유니코드 공백, NBSP 포함)를 기준으로 하지만
 * Java String.trim() 은 코드값 <= ' ' 인 문자를, String.isBlank() 는 Character.isWhitespace 를 기준으로 한다.
 * 저장값(SERVERCODE·PROP_KEY 등)과 기본값 판단이 이 차이로 달라지지 않게 한다.
 */

/** Java `String.trim()` 과 같다: 앞뒤의 코드값 <= U+0020 문자를 지운다. */
internal fun String.trimJava(): String = trim { it <= ' ' }

/** Java `String.isBlank()` 와 같다: 비었거나 모든 문자가 Character.isWhitespace. */
internal fun String.isBlankJava(): Boolean = all { Character.isWhitespace(it) }
