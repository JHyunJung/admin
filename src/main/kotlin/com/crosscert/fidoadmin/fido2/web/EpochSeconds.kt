package com.crosscert.fidoadmin.fido2.web

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * FIDO2_DEMO_ACCESS_CODE.STARTTIME/ENDTIME 은 epoch 초(NUMBER) 로 저장된다.
 * 화면은 서울 시각으로 보여주고 입력받는다. 밀리초가 아니라 초 단위다(시드 1767225600 = 2026-01-01T00:00Z).
 */
object EpochSeconds {

    internal val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    @JvmStatic
    fun toEpoch(time: LocalDateTime?): Long? = time?.atZone(ZONE)?.toEpochSecond()

    @JvmStatic
    fun fromEpoch(epochSeconds: Long?): LocalDateTime? =
        epochSeconds?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZONE) }
}
