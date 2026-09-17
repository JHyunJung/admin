package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

class SignupPolicyTest {

    record Form(String password, String passwordConfirm) {}

    private BindingResult bind(String pw, String confirm) {
        BindingResult b = new BeanPropertyBindingResult(new Form(pw, confirm), "form");
        SignupPolicy.validatePassword(pw, confirm, b, "password", "passwordConfirm");
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

    /** 상수는 기존 인증 코드의 활성 표기와 정확히 같아야 한다. */
    @Test void statusConstantsMatchExistingCode() {
        assertThat(SignupPolicy.STATUS_ACTIVE).isEqualTo("활성");
        assertThat(SignupPolicy.STATUS_PENDING).isEqualTo("승인대기");
        assertThat(SignupPolicy.STATUS_REJECTED).isEqualTo("거절");
        assertThat(SignupPolicy.UNASSIGNED_COMPANY_IDX).isEqualTo(-1L);
    }
}
