package com.crosscert.fidoadmin.common;

/**
 * CCFA_MANAGER.STATUS 에 저장되는 운영자 상태 표기. 이 값이 유일한 정의다.
 *
 * <p>{@link #ACTIVE} 는 {@code auth.ManagerUserDetailsService} 가 로그인 가능 여부를
 * 판단하는 값이자 가입 승인이 기록하는 값이다. 두 곳이 같은 상수를 보므로 어긋날 수 없다.
 *
 * <p>가입 신청 전용 상태(승인대기·거절)는 {@code signup.SignupPolicy} 가 갖는다.
 */
public final class ManagerStatus {

    private ManagerStatus() {}

    /** 로그인 가능한 정상 계정. */
    public static final String ACTIVE = "활성";

    /** CCFA_MANAGER.LOGIN — 접속 중. LoginAttemptService 가 로그인 성공 때 쓴다. */
    public static final String LOGIN_ONLINE = "ON-LINE";
    /** CCFA_MANAGER.LOGIN — 접속 아님. 새 계정의 기본값이자 로그아웃 때 값. */
    public static final String LOGIN_OFFLINE = "OFF-LINE";
    /** CCFA_MANAGER.ALRAM_TYPE 기본값(알림 없음). */
    public static final String ALARM_TYPE_NONE = "none";
    /** CCFA_MANAGER.ALRAM_LEVEL 기본값. */
    public static final String ALARM_LEVEL_DEFAULT = "0";
}
