package com.crosscert.fidoadmin.common

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * 문자 수가 아닌 UTF-8 바이트 수로 길이를 검증한다.
 *
 * DB 의 VARCHAR2 는 BYTE 의미(NLS_LENGTH_SEMANTICS=BYTE, 문자셋 AL32UTF8)라
 * 한글 1자가 3바이트를 차지한다. `@Size` 는 문자 수를 세므로
 * 예를 들어 한글 86자(258바이트)는 통과하지만 VARCHAR2(256) 저장 시 ORA-12899 가 난다.
 * DB 컬럼 길이는 바이트 값이므로 폼 검증도 바이트로 맞춘다.
 */
@MustBeDocumented
@Constraint(validatedBy = [ByteSizeValidator::class])
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ByteSize(
    val message: String = "{max}바이트를 넘을 수 없습니다(한글은 1자가 3바이트).",
    val max: Int,
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
