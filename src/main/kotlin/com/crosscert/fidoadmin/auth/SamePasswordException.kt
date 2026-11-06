package com.crosscert.fidoadmin.auth

/** 새 비밀번호가 현재 비밀번호와 같을 때. 화면에서는 새 비밀번호 칸의 오류로 보인다. */
class SamePasswordException(message: String?) : IllegalArgumentException(message)
