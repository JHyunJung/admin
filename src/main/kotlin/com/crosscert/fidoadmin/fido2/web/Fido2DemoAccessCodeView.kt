package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import java.time.LocalDateTime

/** 목록·상세 공용 출력. epoch 원값과 서울 일시를 함께 싣는다. */
@JvmRecord
data class Fido2DemoAccessCodeView(
    val accesscode: String?, val vendorname: String?, val starttime: Long?, val endtime: Long?,
    val startAt: LocalDateTime?, val endAt: LocalDateTime?, val status: String?, val note: String?, val etc: String?,
) {
    companion object {
        @JvmStatic
        fun of(c: Fido2DemoAccessCode): Fido2DemoAccessCodeView = Fido2DemoAccessCodeView(
            c.accesscode, c.vendorname, c.starttime, c.endtime,
            EpochSeconds.fromEpoch(c.starttime), EpochSeconds.fromEpoch(c.endtime),
            c.status, c.note, c.etc,
        )
    }
}
