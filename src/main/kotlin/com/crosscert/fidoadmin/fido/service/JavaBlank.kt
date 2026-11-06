package com.crosscert.fidoadmin.fido.service

/**
 * Java `s == null || s.isBlank()` 과 같은 판정. Kotlin `isBlank()` 는 `Char.isWhitespace()` 기준이라
 * U+00A0(줄바꿈 없는 공백) 등도 공백으로 보지만, Java `String.isBlank()` 는 `Character.isWhitespace` 만 본다.
 * Java 에서 옮긴 기본값·검증 판정이 바뀌지 않도록 이 기준을 쓴다.
 */
internal fun String?.isNullOrJavaBlank(): Boolean = this == null || all { Character.isWhitespace(it) }
