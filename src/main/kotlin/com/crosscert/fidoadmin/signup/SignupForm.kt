package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.ByteSize
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * 가입 신청 폼.
 *
 * companyIdx·status 필드를 의도적으로 두지 않는다. 필드가 없으면 파라미터로 넘어와도
 * 바인딩되지 않으므로, 가입 경로로 소속이나 상태를 지정할 통로 자체가 사라진다.
 * "덮어쓴다"가 아니라 "받을 곳이 없다"가 방어의 근거다.
 *
 * 길이 제한은 [ByteSize] 로 건다. DB 의 VARCHAR2 는 바이트 의미
 * (NLS_LENGTH_SEMANTICS=BYTE, AL32UTF8)라 한글 1자가 3바이트다. 문자 수로 재는
 * `@Size` 를 쓰면 검증을 통과한 값이 컬럼에 안 들어가 ORA-12899 로 500 이 난다.
 *
 * 문자열 입력은 바인딩되는 순간 앞뒤 공백을 떼어 보관한다
 * (`ManagerForm.applyTo()` 가 USER_ID 에 하는 정규화와 같다).
 * 아래 세터와 [trimToNull] 이 그 자리이며, 읽는 쪽에서는 정규화하지 않는다.
 */
class SignupForm {

    /**
     * USER_ID 는 로그인 키다. Spring Security 의 UsernamePasswordAuthenticationFilter 는
     * 로그인 때 username 을 trim 하므로, " newbie " 로 저장된 계정은 승인을 받아도
     * 영영 로그인되지 않는다. 신청은 성공하고 승인자에게도 정상으로 보이므로 화면에서는
     * 원인을 알 길이 없다(`LoginAttemptService.normalize()` 도 같은 이유로 같은
     * 정규화를 한다).
     *
     * 정규화를 세터에 두는 이유: 중복 검사와 저장이 각자 trim 하면 언젠가 한쪽만
     * 고쳐져 어긋난다. 필드에 정규화된 값만 들어오게 하면 읽는 쪽이 어긋날 수 없고,
     * 검증(`@NotBlank`·`@ByteSize`)과 다시 그린 폼도 같은 값을 본다.
     *
     * 공백뿐인 입력은 여기서 빈 문자열이 되고 `@NotBlank` 가 잡는다.
     * 따로 검증을 더하지 않는다.
     */
    @field:NotBlank(message = "아이디는 필수입니다.")
    @field:ByteSize(max = 64)
    var userId: String? = null
        set(value) { field = trim(value) }

    /** 형식 검사는 PasswordPolicy 가 한다(운영자 등록 화면과 같은 규칙을 쓰기 위해서다). */
    var password: String? = null
    var passwordConfirm: String? = null

    /**
     * 이름도 같이 정규화한다. 목록·상세에 그대로 나오는 값이라 딸려 온 공백이
     * 눈에 띄지 않게 남고, 공백을 뗀 뒤에 재야 `@ByteSize` 가 실제 내용의
     * 길이를 잰다.
     */
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:ByteSize(max = 50)
    var userNm: String? = null
        set(value) { field = trim(value) }

    /**
     * 이메일도 마찬가지다. USER_ID 만큼 치명적이지는 않지만(로그인 키가 아니다),
     * 붙여넣기로 딸려 온 공백을 두면 알림 발송에서 실패하고 목록에서도 눈에 띄지 않는다.
     * 마침 `@Email` 은 " a@b.local " 을 형식 오류로 보므로, 정규화하지 않으면
     * 멀쩡한 주소가 거부된다.
     */
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:ByteSize(max = 256)
    var userEmail: String? = null
        set(value) { field = trim(value) }

    @field:ByteSize(max = 20)
    var userPhone: String? = null

    /**
     * ETC 컬럼이 2048바이트인데 저장 시 "신청 사유: " 접두가 붙고 나중에 거절 사유가
     * 덧붙는다. 그 여유를 남기고 1700바이트로 제한한다.
     */
    @field:ByteSize(max = 1700)
    var reason: String? = null

    /** 빈 문자열은 null 로 다룬다(선택 항목). */
    fun normalizedPhone(): String? = trimToNull(userPhone)

    fun normalizedReason(): String? = trimToNull(reason)

    private companion object {
        /**
         * 선택 항목은 이미 이 한 곳만 거쳐 나가므로 공백 제거도 여기서 한다(세터를 따로
         * 두지 않는 이유다). 전화번호는 딸려 온 공백이 저장되면 조회가 어긋나고,
         * 신청 사유는 "신청 사유: " 접두 뒤에 공백이 남는다.
         */
        fun trimToNull(s: String?): String? {
            val trimmed = trim(s)
            return if (trimmed == null || trimmed.isEmpty()) null else trimmed
        }

        fun trim(s: String?): String? = s?.javaTrim()
    }
}
