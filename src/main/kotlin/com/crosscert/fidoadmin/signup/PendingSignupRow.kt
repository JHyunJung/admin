package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.manager.entity.CcfaManager
import java.time.LocalDateTime

/**
 * 가입 승인 화면(`signup/list.html`)의 신청 1건. 템플릿이 읽는 8개 프로퍼티만 싣는다.
 * USER_PW 해시가 뷰 모델에 올라가지 않게 하기 위해 엔티티 대신 쓴다.
 */
data class PendingSignupRow(
    val idx: Long?, val userId: String?, val userNm: String?, val userEmail: String?,
    val userPhone: String?, val status: String?, val etc: String?, val createdtime: LocalDateTime?,
) {
    companion object {
        fun of(m: CcfaManager): PendingSignupRow = PendingSignupRow(
            m.idx, m.userId, m.userNm, m.userEmail,
            m.userPhone, m.status, m.etc, m.createdtime,
        )
    }
}
