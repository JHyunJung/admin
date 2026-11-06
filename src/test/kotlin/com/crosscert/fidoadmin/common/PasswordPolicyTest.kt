package com.crosscert.fidoadmin.common

import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult

class PasswordPolicyTest {

    @JvmRecord
    data class Form(val password: String?, val passwordConfirm: String?)

    private fun bind(pw: String?, confirm: String?): BindingResult {
        val b: BindingResult = BeanPropertyBindingResult(Form(pw, confirm), "form")
        PasswordPolicy.validatePassword(pw, confirm, b, "password", "passwordConfirm")
        return b
    }

    @Test fun acceptsPolicyCompliantPassword() {
        assertThat(bind("Company1234!", "Company1234!").hasErrors()).isFalse()
    }

    @Test fun rejectsMissingUppercase() {
        val b = bind("company1234!", "company1234!")
        assertThat(b.getFieldError("password")!!.defaultMessage).isEqualTo(PasswordPolicy.MESSAGE)
    }

    @Test fun rejectsMissingLowercase() {
        val b = bind("COMPANY1234!", "COMPANY1234!")
        assertThat(b.getFieldError("password")!!.defaultMessage).isEqualTo(PasswordPolicy.MESSAGE)
    }

    @Test fun messageNamesAllFourKinds() {
        assertThat(PasswordPolicy.MESSAGE).isEqualTo("영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.")
    }

    @Test fun rejectsTooShort() {
        val b = bind("Ab1!", "Ab1!")
        assertThat(b.getFieldError("password")!!.defaultMessage).contains("8자 이상")
    }

    @Test fun rejectsTooLong() {
        val pw = "A1!" + "a".repeat(62)   // 65자
        val b = bind(pw, pw)
        assertThat(b.getFieldError("password")!!.defaultMessage).contains("64자 이하")
    }

    @Test fun rejectsMissingSpecialCharacter() {
        val b = bind("Company1234", "Company1234")
        assertThat(b.getFieldError("password")!!.defaultMessage).contains("특수문자")
    }

    @Test fun rejectsMissingDigit() {
        val b = bind("Companyabc!", "Companyabc!")
        assertThat(b.getFieldError("password")!!.defaultMessage).contains("숫자")
    }

    @Test fun rejectsConfirmMismatch() {
        val b = bind("Company1234!", "Company9999!")
        assertThat(b.getFieldError("passwordConfirm")!!.defaultMessage).contains("일치")
    }

    /** 빈 비밀번호는 필수 오류만 내고 형식 검사는 하지 않는다. */
    @Test fun rejectsBlankPasswordAsRequired() {
        val b = bind("  ", "  ")
        assertThat(b.getFieldError("password")!!.defaultMessage).contains("필수")
        assertThat(b.getFieldError("passwordConfirm")).isNull()
    }

    /** 3인자 단축 호출은 password/passwordConfirm 필드에 오류를 붙인다. */
    @Test fun shortFormDefaultsFieldNames() {
        val b: BindingResult = BeanPropertyBindingResult(Form("Company1234!", "Company9999!"), "form")
        PasswordPolicy.validatePassword("Company1234!", "Company9999!", b)
        assertThat(b.getFieldError("passwordConfirm")!!.defaultMessage).contains("일치")
        assertThat(b.getFieldError("password")).isNull()
    }

    /**
     * 공개 상수가 뜻하는 규칙 자체를 고정한다 — 영문·숫자·특수문자를 각각 요구하고,
     * 길이 경계는 8과 64다. 어노테이션으로 이 상수를 참조하는 곳(PasswordChangeForm)의
     * 동작도 결국 이 규칙이다.
     *
     * **이 테스트는 "선언이 한 곳뿐인가" 를 검사하지 않는다.** 자바는 `static final`
     * 상수를 컴파일 시 값으로 박아 넣으므로, 어떤 파일이 상수를 참조하든 리터럴을 다시 적든
     * 값을 비교하는 테스트는 똑같이 통과한다. 단일 선언은 구조로 보장되는 것이지
     * 테스트로 고정할 수 있는 성질이 아니다.
     */
    @Test fun constantsExpressTheIntendedRule() {
        val p = Pattern.compile(PasswordPolicy.PATTERN)
        assertThat(p.matcher("Company1234!").matches()).`as`("세 종류를 모두 포함").isTrue()
        assertThat(p.matcher("Company1234").matches()).`as`("특수문자 없음").isFalse()
        assertThat(p.matcher("Company!!!!").matches()).`as`("숫자 없음").isFalse()
        assertThat(p.matcher("12341234!!!!").matches()).`as`("영문 없음").isFalse()
        assertThat(PasswordPolicy.MIN_LENGTH).isEqualTo(8)
        assertThat(PasswordPolicy.MAX_LENGTH).isEqualTo(64)
    }
}
