package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

/**
 * FIDO2_DEMO_ACCESS_CODE 입력 폼. 시작·종료는 화면에서 일시로 받고 저장 시 epoch 초로 바꾼다.
 * ACCESSCODE 는 할당형 PK 라 수정 화면에서는 readonly 이고 applyTo 는 기존 값을 바꾸지 않는다.
 * ACCESSCODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL에 안전한 문자만 허용한다.
 */
class Fido2DemoAccessCodeForm {
    @field:NotBlank @field:ByteSize(max = 128)
    @field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "접근코드는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    var accesscode: String? = null
    @field:ByteSize(max = 128) var vendorname: String? = null
    /** 초를 보존한다. HH:mm 만 들어와도 파싱되고, 값 표시는 항상 :ss 까지 포맷한다(수정 시 초 유실 방지). */
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]") var starttime: LocalDateTime? = null
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]") var endtime: LocalDateTime? = null
    @field:NotBlank @field:ByteSize(max = 1) var status: String? = "E"
    @field:ByteSize(max = 300) var note: String? = null
    @field:ByteSize(max = 1024) var etc: String? = null

    /** 신규 엔티티에만 식별자를 채운다. 수정 시에는 applyTo 만 쓰므로 식별자가 바뀌지 않는다. */
    fun toNewEntity(): Fido2DemoAccessCode {
        val c = Fido2DemoAccessCode()
        c.accesscode = accesscode
        applyTo(c)
        return c
    }

    fun applyTo(c: Fido2DemoAccessCode) {
        c.vendorname = vendorname
        c.starttime = EpochSeconds.toEpoch(starttime); c.endtime = EpochSeconds.toEpoch(endtime)
        c.status = status; c.note = note; c.etc = etc
    }

    companion object {
        @JvmStatic
        fun from(c: Fido2DemoAccessCode): Fido2DemoAccessCodeForm = Fido2DemoAccessCodeForm().apply {
            accesscode = c.accesscode; vendorname = c.vendorname
            starttime = EpochSeconds.fromEpoch(c.starttime); endtime = EpochSeconds.fromEpoch(c.endtime)
            status = c.status; note = c.note; etc = c.etc
        }
    }
}
