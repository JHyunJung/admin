package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

class PasswordPolicyTest {

    record Form(String password, String passwordConfirm) {}

    private BindingResult bind(String pw, String confirm) {
        BindingResult b = new BeanPropertyBindingResult(new Form(pw, confirm), "form");
        PasswordPolicy.validatePassword(pw, confirm, b, "password", "passwordConfirm");
        return b;
    }

    @Test void acceptsPolicyCompliantPassword() {
        assertThat(bind("Company1234!", "Company1234!").hasErrors()).isFalse();
    }

    @Test void rejectsTooShort() {
        BindingResult b = bind("Ab1!", "Ab1!");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("8자 이상");
    }

    @Test void rejectsTooLong() {
        String pw = "A1!" + "a".repeat(62);   // 65자
        BindingResult b = bind(pw, pw);
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("64자 이하");
    }

    @Test void rejectsMissingSpecialCharacter() {
        BindingResult b = bind("Company1234", "Company1234");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("특수문자");
    }

    @Test void rejectsMissingDigit() {
        BindingResult b = bind("Companyabc!", "Companyabc!");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("숫자");
    }

    @Test void rejectsConfirmMismatch() {
        BindingResult b = bind("Company1234!", "Company9999!");
        assertThat(b.getFieldError("passwordConfirm").getDefaultMessage()).contains("일치");
    }

    /** 빈 비밀번호는 필수 오류만 내고 형식 검사는 하지 않는다. */
    @Test void rejectsBlankPasswordAsRequired() {
        BindingResult b = bind("  ", "  ");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("필수");
        assertThat(b.getFieldError("passwordConfirm")).isNull();
    }

    /** 3인자 단축 호출은 password/passwordConfirm 필드에 오류를 붙인다. */
    @Test void shortFormDefaultsFieldNames() {
        BindingResult b = new BeanPropertyBindingResult(new Form("Company1234!", "Company9999!"), "form");
        PasswordPolicy.validatePassword("Company1234!", "Company9999!", b);
        assertThat(b.getFieldError("passwordConfirm").getDefaultMessage()).contains("일치");
        assertThat(b.getFieldError("password")).isNull();
    }

    /** PasswordChangeForm 의 @Pattern 이 같은 규칙을 쓰도록 값을 고정한다. */
    @Test void patternConstantIsTheSharedRegex() {
        assertThat(PasswordPolicy.PATTERN)
            .isEqualTo("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");
    }
}
