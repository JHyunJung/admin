package com.crosscert.fidoadmin.common

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.nio.charset.StandardCharsets

class ByteSizeValidator : ConstraintValidator<ByteSize, String> {

    private var max: Int = 0

    override fun initialize(constraint: ByteSize) {
        this.max = constraint.max
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value.isNullOrEmpty()) return true // 필수 여부는 @NotBlank 가 본다
        return value.toByteArray(StandardCharsets.UTF_8).size <= max
    }
}
