package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.auth.isJavaBlank
import com.crosscert.fidoadmin.auth.javaTrim
import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.signup.SignupPolicy
import jakarta.validation.constraints.NotBlank

/**
 * CCFA_MANAGER 입력 폼. 비밀번호는 평문으로 받아 컨트롤러가 인코딩한다.
 * 등록 시 필수·수정 시 선택은 컨트롤러 validate() 가 본다(Bean Validation 으로는 구분할 수 없다).
 *
 * companyIdx 는 폼에서 고르지 않는다 — 세션이 정한 유효 테넌트를
 * `CrudService.create()`/`update()` 가 덮어쓴다. 그래서 여기 `@NotNull` 을
 * 두면 폼에 값이 실려 오지 않는 모든 요청이 `@Valid` 단계에서 막혀, 등록 자체가 불가능해진다.
 */
class ManagerForm {
    @field:NotBlank(message = "ID 는 필수입니다.") @field:ByteSize(max = 64) var userId: String? = null
    @field:ByteSize(max = 128) var password: String? = null
    var passwordConfirm: String? = null
    @field:ByteSize(max = 50) var userNm: String? = null
    @field:ByteSize(max = 256) var userEmail: String? = null
    @field:ByteSize(max = 20) var userPhone: String? = null
    var companyIdx: Long? = null
    @field:NotBlank(message = "상태는 필수입니다.") @field:ByteSize(max = 20) var status: String? = "활성"
    @field:ByteSize(max = 2048) var etc: String? = null
    @field:ByteSize(max = 32) var alramType: String? = "none"
    @field:ByteSize(max = 20) var alramLevel: String? = "0"

    /** USER_ID 는 등록 시에만 채운다(수정 화면에서는 readonly, 로그인 키라 바꾸지 않는다). USER_PW 는 컨트롤러가 채운다. */
    fun applyTo(m: CcfaManager) {
        if (m.userId == null) m.userId = userId?.javaTrim()
        m.userNm = userNm; m.userEmail = userEmail; m.userPhone = userPhone
        m.companyIdx = companyIdx; m.status = status; m.etc = etc
        m.alramType = alramType; m.alramLevel = alramLevel
    }

    fun hasPassword(): Boolean = password != null && !password!!.isJavaBlank()

    /**
     * 가입 신청 상태(승인대기·거절)인가. 화면이 상태 입력란을 읽기 전용으로 바꾸는 판단에 쓴다.
     * 실제 차단은 `ManagerService.update()` 가 한다(조작된 POST 는 폼을 거치지 않는다).
     */
    fun isSignupStatus(): Boolean = SignupPolicy.isSignupStatus(status)

    companion object {
        /** USER_PW 는 절대 폼으로 옮기지 않는다. */
        @JvmStatic
        fun from(m: CcfaManager): ManagerForm = ManagerForm().apply {
            userId = m.userId; userNm = m.userNm; userEmail = m.userEmail; userPhone = m.userPhone
            companyIdx = m.companyIdx; status = m.status; etc = m.etc
            alramType = m.alramType; alramLevel = m.alramLevel
        }
    }
}
