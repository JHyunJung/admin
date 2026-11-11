package com.crosscert.fidoadmin.auth

/**
 * 비밀번호 변경에서 현재 비밀번호가 틀렸을 때. 화면에서는 현재 비밀번호 칸의 오류로 보인다.
 * 이 예외는 트랜잭션을 되돌리지 않는다 — 같은 트랜잭션에서 센 실패 횟수가 남아야 한다.
 */
class WrongCurrentPasswordException(message: String?) : IllegalArgumentException(message)
