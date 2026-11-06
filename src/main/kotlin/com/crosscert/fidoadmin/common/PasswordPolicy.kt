package com.crosscert.fidoadmin.common

import java.util.regex.Pattern
import org.springframework.validation.BindingResult

/**
 * 운영자 비밀번호 형식 정책. 비밀번호를 직접 입력받는 화면이면 어디서든 같은 규칙을 쓴다.
 *
 * 현재 소비자는 운영자 등록·수정(`manager.web.ManagerController`), 가입 신청·승인,
 * 그리고 비밀번호 변경([com.crosscert.fidoadmin.auth.PasswordChangeForm])이다.
 * 앞의 둘은 [validatePassword] 를 부르고, 마지막은 어노테이션 속성이 컴파일 상수여야 해서
 * [PATTERN]·[MIN_LENGTH]·[MAX_LENGTH] 를 참조한다.
 *
 * **규칙의 선언은 이 파일 하나뿐이다.** 다만 자바는 `static final` 상수를 컴파일 시
 * 호출부에 값으로 박아 넣으므로, "값이 같은가" 를 보는 테스트로는 참조와 리터럴 재선언을
 * 구별할 수 없다. 이 단일 선언은 테스트가 아니라 구조로 보장된다.
 */
object PasswordPolicy {

    /** 영문 대문자·소문자·숫자·특수문자를 각각 하나 이상 포함해야 한다. */
    const val PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$"

    /** 형식 위반 문구. 어노테이션 속성에서도 참조하므로 컴파일 상수다. */
    const val MESSAGE = "영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다."

    /** 허용 길이(경계 포함). */
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    private val COMPILED: Pattern = Pattern.compile(PATTERN)

    /** 필드명이 password/passwordConfirm 인 일반적인 폼용 단축 호출. */
    @JvmStatic
    fun validatePassword(pw: String?, confirm: String?, binding: BindingResult) {
        validatePassword(pw, confirm, binding, "password", "passwordConfirm")
    }

    /** 정책 위반 시 binding 에 필드 오류를 등록한다. */
    @JvmStatic
    fun validatePassword(pw: String?, confirm: String?, binding: BindingResult,
                         pwField: String, confirmField: String) {
        if (pw == null || pw.javaIsBlank()) {
            binding.rejectValue(pwField, "required", "비밀번호는 필수입니다.")
            return
        }
        if (pw.length < MIN_LENGTH || pw.length > MAX_LENGTH) {
            binding.rejectValue(pwField, "size", "비밀번호는 8자 이상 64자 이하여야 합니다.")
        } else if (!COMPILED.matcher(pw).matches()) {
            binding.rejectValue(pwField, "policy", MESSAGE)
        }
        if (pw != confirm) {
            binding.rejectValue(confirmField, "mismatch", "비밀번호 확인이 일치하지 않습니다.")
        }
    }
}
