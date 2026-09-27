package com.crosscert.fidoadmin.company.web;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * FDS 모니터링 목록 행. 직전·직후 요청과의 간격(초)을 미리 계산해 둔다.
 *
 * <p>간격을 레코드 컴포넌트로 두는 이유: Thymeleaf 가 {@code r.gapBeforeSec} 로 바로 읽는다.
 * 계산 메서드로 두면 접근자 해석에 기대야 한다. 값은 소수점을 유지한다 — SQL 에서
 * 초로 바꾸면 밀리초가 날아가는데, 반복 탐지에서는 0.3초와 3초가 다르다.
 */
public record FdsMonitorRow(Long idx, String servicename, String serialcode, LocalDateTime createdtime,
                            Double gapBeforeSec, Double gapAfterSec, long repeats) {

    public static FdsMonitorRow of(Long idx, String servicename, String serialcode, LocalDateTime createdtime,
                                   LocalDateTime prevTime, LocalDateTime nextTime, long repeats) {
        return new FdsMonitorRow(idx, servicename, serialcode, createdtime,
            seconds(prevTime, createdtime), seconds(createdtime, nextTime), repeats);
    }

    private static Double seconds(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        return Duration.between(from, to).toNanos() / 1_000_000_000.0;
    }
}
