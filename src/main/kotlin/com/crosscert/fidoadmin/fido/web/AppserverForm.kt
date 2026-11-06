package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.fido.entity.Appserver
import jakarta.validation.constraints.NotBlank

/**
 * APPSERVER 입력 폼. MEMBER_ID, TYPE 은 DB NOT NULL.
 * MEMBER_CODE 는 서버가 만들고 바꾸지 않는다 — 수정 화면에 보여 주기만 하고 엔티티에 옮기지 않는다.
 */
class AppserverForm {
    /** 표시 전용. applyTo 가 무시한다. */
    var memberCode: String? = null
    @field:NotBlank @field:ByteSize(max = 32) var memberId: String? = null
    @field:NotBlank @field:ByteSize(max = 10) var type: String? = "use"
    @field:ByteSize(max = 128) var note: String? = null
    /** SUPER 만 선택. COMPANY 는 서비스가 자기 고객사로 강제한다. */
    var companyIdx: Long? = null

    fun applyTo(a: Appserver) {
        a.memberId = memberId; a.type = type
        a.note = note; a.companyIdx = companyIdx
    }

    companion object {
        @JvmStatic
        fun from(a: Appserver): AppserverForm = AppserverForm().apply {
            memberCode = a.memberCode; memberId = a.memberId; type = a.type
            note = a.note; companyIdx = a.companyIdx
        }
    }
}
