package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.company.entity.CcfaCompany

/**
 * 가입 신청·승인 워크플로 전용 상수.
 *
 * 승인이 기록하는 활성 상태는 여기에 두지 않는다. 로그인 판정이 보는
 * [ManagerStatus.ACTIVE] 를 직접 쓴다. 정의가 하나뿐이어야 어긋날 수 없고,
 * 상수는 컴파일 시 값이 박히므로 "같은 값인지" 검사하는 테스트로는 중복을 잡을 수 없다.
 *
 * 비밀번호 형식 규칙은 가입만의 것이 아니므로
 * [com.crosscert.fidoadmin.common.PasswordPolicy] 에 있다.
 */
object SignupPolicy {

    /** 신청 직후. 로그인 불가(기존 상태 검사가 막는다). */
    const val STATUS_PENDING: String = "승인대기"
    /** 반려됨. 로그인 불가. 승인·거절 처리가 쓴다. */
    const val STATUS_REJECTED: String = "거절"

    /**
     * 소속 미배정 표식. 실제 고객사 IDX 는 0 이상이므로 겹치지 않는다.
     * 0 은 SUPER 를 뜻하므로 가입 경로에서 절대 쓰지 않는다.
     */
    const val UNASSIGNED_COMPANY_IDX: Long = -1L

    /** SUPER 를 뜻하는 소속. 승인 시 이 값으로 배정하는 것을 금지한다(권한 상승 차단 2단계). */
    const val SUPER_COMPANY_IDX: Long = 0L

    /**
     * 가입 워크플로가 관리하는 상태인가. 이 상태의 행은 상태를 가입 승인 화면에서만 바꿀 수 있다.
     * 운영자 수정 화면이 이 판정을 보고 상태 편집을 막는다.
     */
    @JvmStatic
    fun isSignupStatus(status: String?): Boolean = STATUS_PENDING == status || STATUS_REJECTED == status

    /**
     * 화면에서 고를 수 있는 고객사. 전역([SUPER_COMPANY_IDX])과 IDX 가 없는 행을 뺀다.
     * 가입 승인 화면과 테넌트 선택 화면이 같은 기준을 쓰도록 한곳에 둔다.
     */
    fun selectableCompanies(all: List<CcfaCompany>): List<CcfaCompany> =
        all.stream()
            .filter { c -> c.idx != null && c.idx != SUPER_COMPANY_IDX }
            .toList()
}
