package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.common.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 비밀번호 변경 폼.
 *
 * <p>형식 규칙은 {@link PasswordPolicy} 한 곳에만 선언돼 있고 여기서는 그 상수를 참조한다.
 * 어노테이션 속성은 컴파일 상수여야 해서 메서드 호출({@code PasswordPolicy.validatePassword})
 * 대신 상수 참조를 쓴다.
 *
 * <p><b>이 연결은 테스트로 고정할 수 없다.</b> 자바는 {@code static final} 상수를 컴파일 시
 * 호출부에 값으로 박아 넣으므로, "폼의 정규식이 정책의 정규식과 같은가" 를 보는 테스트는
 * 상수를 참조하든 리터럴을 다시 적든 똑같이 통과한다. 보장은 구조적인 것이다 —
 * 선언이 하나뿐이고 나머지는 그것을 가리킨다. 통과하는데 이유가 틀린 테스트는 두지 않는다.
 */
@Getter @Setter
public class PasswordChangeForm {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.PATTERN, message = "영문, 숫자, 특수문자를 모두 포함해야 합니다.")
    private String newPassword;
    @NotBlank private String confirmPassword;
}
