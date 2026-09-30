package com.crosscert.fidoadmin.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 쉼표로 구분한 IPv4 규칙 목록. 항목은 단일 IP, CIDR(a.b.c.d/n), 범위(a.b.c.d~e.f.g.h) 중 하나.
 * 이전 어드민 FDSPolicyValidator.checkIp 를 잇는다. 빈 값은 통과.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IpRuleListValidator.class)
public @interface IpRuleList {
    String message() default "올바른 IP 형식이 아닙니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
