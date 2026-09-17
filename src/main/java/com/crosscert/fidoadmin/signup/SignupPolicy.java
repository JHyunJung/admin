package com.crosscert.fidoadmin.signup;

import java.util.regex.Pattern;
import org.springframework.validation.BindingResult;

/**
 * 가입 신청·승인에서 공유하는 상수와 비밀번호 정책.
 *
 * <p>STATUS_ACTIVE 는 {@link com.crosscert.fidoadmin.auth.ManagerUserDetailsService} 가
 * 로그인 가능 여부를 판단하는 값과 정확히 같아야 한다. 다르면 승인해도 로그인이 되지 않는다.
 *
 * <p>비밀번호 정책은 {@link com.crosscert.fidoadmin.auth.PasswordChangeForm} 의 정책과
 * 동일하게 유지한다.
 */
public final class SignupPolicy {

    private SignupPolicy() {}

    /** 신청 직후. 로그인 불가(기존 상태 검사가 막는다). */
    public static final String STATUS_PENDING = "승인대기";
    /** 승인 완료. 로그인 가능. */
    public static final String STATUS_ACTIVE = "활성";
    /** 반려됨. 로그인 불가. */
    public static final String STATUS_REJECTED = "거절";

    /**
     * 소속 미배정 표식. 실제 고객사 IDX 는 0 이상이므로 겹치지 않는다.
     * 0 은 SUPER 를 뜻하므로 가입 경로에서 절대 쓰지 않는다.
     */
    public static final long UNASSIGNED_COMPANY_IDX = -1L;

    /** SUPER 를 뜻하는 소속. 승인 시 이 값으로 배정하는 것을 금지한다. */
    public static final long SUPER_COMPANY_IDX = 0L;

    private static final Pattern PASSWORD_POLICY =
        Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

    /** 정책 위반 시 binding 에 필드 오류를 등록한다. */
    public static void validatePassword(String pw, String confirm, BindingResult binding,
                                        String pwField, String confirmField) {
        if (pw == null || pw.isBlank()) {
            binding.rejectValue(pwField, "required", "비밀번호는 필수입니다.");
            return;
        }
        if (pw.length() < 8 || pw.length() > 64) {
            binding.rejectValue(pwField, "size", "비밀번호는 8자 이상 64자 이하여야 합니다.");
        } else if (!PASSWORD_POLICY.matcher(pw).matches()) {
            binding.rejectValue(pwField, "policy", "영문, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
        if (!pw.equals(confirm)) {
            binding.rejectValue(confirmField, "mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }
    }
}
