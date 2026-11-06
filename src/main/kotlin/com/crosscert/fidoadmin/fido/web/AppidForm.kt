package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.fido.entity.Appid
import jakarta.validation.constraints.NotBlank

/** APPID 입력 폼. 길이 제한은 DB 컬럼 길이(바이트). 식별자(IDX)는 폼에 두지 않는다. */
class AppidForm {
    @field:NotBlank @field:ByteSize(max = 128) var appid: String? = null
    @field:ByteSize(max = 512) var memo: String? = null
    @field:NotBlank @field:ByteSize(max = 12) var status: String? = "use"
    @field:ByteSize(max = 128) var device: String? = null
    @field:NotBlank @field:ByteSize(max = 1) var deviceDefault: String? = "F"
    @field:ByteSize(max = 512) var servicename: String? = null
    // companyIdx 는 두지 않는다 — 폼에 입력 칸이 없고, CrudService 가 등록·수정 때 유효 테넌트로 강제한다.

    fun applyTo(a: Appid) {
        a.appid = appid; a.memo = memo; a.status = status; a.device = device
        a.deviceDefault = deviceDefault; a.servicename = servicename
    }

    companion object {
        @JvmStatic
        fun from(a: Appid): AppidForm = AppidForm().apply {
            appid = a.appid; memo = a.memo; status = a.status; device = a.device
            deviceDefault = a.deviceDefault; servicename = a.servicename
        }
    }
}
