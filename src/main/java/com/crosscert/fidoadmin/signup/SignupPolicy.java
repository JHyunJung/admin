package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.common.ManagerStatus;

/**
 * 가입 신청·승인 워크플로 전용 상수.
 *
 * <p>승인이 기록하는 활성 상태는 여기에 두지 않는다. 로그인 판정이 보는
 * {@link ManagerStatus#ACTIVE} 를 직접 쓴다. 정의가 하나뿐이어야 어긋날 수 없고,
 * 상수는 컴파일 시 값이 박히므로 "같은 값인지" 검사하는 테스트로는 중복을 잡을 수 없다.
 *
 * <p>비밀번호 형식 규칙은 가입만의 것이 아니므로
 * {@link com.crosscert.fidoadmin.common.PasswordPolicy} 에 있다.
 */
public final class SignupPolicy {

    private SignupPolicy() {}

    /** 신청 직후. 로그인 불가(기존 상태 검사가 막는다). */
    public static final String STATUS_PENDING = "승인대기";
    /** 반려됨. 로그인 불가. 승인·거절 처리가 쓴다(설계서 6.2). */
    public static final String STATUS_REJECTED = "거절";

    /**
     * 소속 미배정 표식. 실제 고객사 IDX 는 0 이상이므로 겹치지 않는다.
     * 0 은 SUPER 를 뜻하므로 가입 경로에서 절대 쓰지 않는다.
     */
    public static final long UNASSIGNED_COMPANY_IDX = -1L;

    /** SUPER 를 뜻하는 소속. 승인 시 이 값으로 배정하는 것을 금지한다(설계서 6.2, 권한 상승 차단 2단계). */
    public static final long SUPER_COMPANY_IDX = 0L;
}
