package com.crosscert.fidoadmin.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ByteSizeValidator implements ConstraintValidator<ByteSize, String> {

    private int max;

    @Override
    public void initialize(ByteSize constraint) {
        this.max = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return true; // 필수 여부는 @NotBlank 가 본다
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
