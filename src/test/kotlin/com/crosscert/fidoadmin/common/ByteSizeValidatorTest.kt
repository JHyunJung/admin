package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.company.web.CompanyForm
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * KBFIDO 의 VARCHAR2 는 BYTE 의미다. 문자 수로 검증하면 한글 입력이 검증을 통과한 뒤
 * ORA-12899 로 저장에 실패한다(실 DB 로 재현 확인: 한글 86자 = 258바이트 > COMPANY_NAME 256).
 */
class ByteSizeValidatorTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().validator

    @Test
    fun koreanOverflowingByteLimitIsRejected() {
        val form = CompanyForm()
        form.companyName = "가".repeat(86) // 258 bytes > 256
        form.enableType = "Y"

        val violations = validator.validateProperty(form, "companyName")

        assertThat(violations).isNotEmpty()
    }

    @Test
    fun koreanWithinByteLimitIsAccepted() {
        val form = CompanyForm()
        form.companyName = "가".repeat(85) // 255 bytes <= 256
        form.enableType = "Y"

        assertThat(validator.validateProperty(form, "companyName")).isEmpty()
    }

    @Test
    fun asciiUpToLimitIsAccepted() {
        val form = CompanyForm()
        form.companyName = "a".repeat(256)

        assertThat(validator.validateProperty(form, "companyName")).isEmpty()
    }
}
