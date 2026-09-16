package com.crosscert.fidoadmin.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자 수가 아닌 UTF-8 바이트 수로 길이를 검증한다.
 *
 * KBFIDO 스키마의 VARCHAR2 는 BYTE 의미(NLS_LENGTH_SEMANTICS=BYTE, 문자셋 AL32UTF8)라
 * 한글 1자가 3바이트를 차지한다. {@code @Size} 는 문자 수를 세므로
 * 예를 들어 한글 86자(258바이트)는 통과하지만 VARCHAR2(256) 저장 시 ORA-12899 가 난다.
 * ERD 의 컬럼 길이는 바이트 값이므로 폼 검증도 바이트로 맞춘다.
 */
@Documented
@Constraint(validatedBy = ByteSizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ByteSize {

    String message() default "{max}바이트를 넘을 수 없습니다(한글은 1자가 3바이트).";

    int max();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
