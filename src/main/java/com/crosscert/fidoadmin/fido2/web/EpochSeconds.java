package com.crosscert.fidoadmin.fido2.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * FIDO2_DEMO_ACCESS_CODE.STARTTIME/ENDTIME 은 epoch 초(NUMBER) 로 저장된다(설계 4.2).
 * 화면은 서울 시각으로 보여주고 입력받는다. 밀리초가 아니라 초 단위다(시드 1767225600 = 2026-01-01T00:00Z).
 */
public final class EpochSeconds {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private EpochSeconds() {}

    public static Long toEpoch(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toEpochSecond();
    }

    public static LocalDateTime fromEpoch(Long epochSeconds) {
        return epochSeconds == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZONE);
    }
}
