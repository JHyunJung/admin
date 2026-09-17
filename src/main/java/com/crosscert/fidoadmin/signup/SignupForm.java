package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.common.ByteSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 가입 신청 폼.
 *
 * <p>companyIdx·status 필드를 의도적으로 두지 않는다. 필드가 없으면 파라미터로 넘어와도
 * 바인딩되지 않으므로, 가입 경로로 소속이나 상태를 지정할 통로 자체가 사라진다.
 * "덮어쓴다"가 아니라 "받을 곳이 없다"가 방어의 근거다.
 *
 * <p>길이 제한은 {@link ByteSize} 로 건다. KBFIDO 스키마의 VARCHAR2 는 바이트 의미
 * (NLS_LENGTH_SEMANTICS=BYTE, AL32UTF8)라 한글 1자가 3바이트다. 문자 수로 재는
 * {@code @Size} 를 쓰면 검증을 통과한 값이 컬럼에 안 들어가 ORA-12899 로 500 이 난다.
 */
@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "아이디는 필수입니다.")
    @ByteSize(max = 64)
    private String userId;

    /** 형식 검사는 PasswordPolicy 가 한다(운영자 등록 화면과 같은 규칙을 쓰기 위해서다). */
    private String password;
    private String passwordConfirm;

    @NotBlank(message = "이름은 필수입니다.")
    @ByteSize(max = 50)
    private String userNm;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @ByteSize(max = 256)
    private String userEmail;

    @ByteSize(max = 20)
    private String userPhone;

    /**
     * ETC 컬럼이 2048바이트인데 저장 시 "신청 사유: " 접두가 붙고 나중에 거절 사유가
     * 덧붙는다. 그 여유를 남기고 1700바이트로 제한한다.
     */
    @ByteSize(max = 1700)
    private String reason;

    /** 빈 문자열은 null 로 다룬다(선택 항목). */
    public String normalizedPhone() { return blankToNull(userPhone); }

    public String normalizedReason() { return blankToNull(reason); }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
