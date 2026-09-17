package com.crosscert.fidoadmin.common;

import java.util.regex.Pattern;
import org.springframework.validation.BindingResult;

/**
 * 운영자 비밀번호 형식 정책. 비밀번호를 직접 입력받는 화면이면 어디서든 같은 규칙을 쓴다.
 *
 * <p>현재 소비자는 운영자 등록·수정({@code manager.web.ManagerController})과
 * 가입 신청·승인이다. {@link com.crosscert.fidoadmin.auth.PasswordChangeForm} 은
 * 같은 규칙을 어노테이션으로 따로 선언하고 있으므로 {@link #PATTERN} 과 어긋나지 않게 유지한다.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    /** 영문·숫자·특수문자를 각각 하나 이상 포함해야 한다. */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    /** 허용 길이(경계 포함). */
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    /** 필드명이 password/passwordConfirm 인 일반적인 폼용 단축 호출. */
    public static void validatePassword(String pw, String confirm, BindingResult binding) {
        validatePassword(pw, confirm, binding, "password", "passwordConfirm");
    }

    /** 정책 위반 시 binding 에 필드 오류를 등록한다. */
    public static void validatePassword(String pw, String confirm, BindingResult binding,
                                        String pwField, String confirmField) {
        if (pw == null || pw.isBlank()) {
            binding.rejectValue(pwField, "required", "비밀번호는 필수입니다.");
            return;
        }
        if (pw.length() < MIN_LENGTH || pw.length() > MAX_LENGTH) {
            binding.rejectValue(pwField, "size", "비밀번호는 8자 이상 64자 이하여야 합니다.");
        } else if (!COMPILED.matcher(pw).matches()) {
            binding.rejectValue(pwField, "policy", "영문, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
        if (!pw.equals(confirm)) {
            binding.rejectValue(confirmField, "mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }
    }
}
