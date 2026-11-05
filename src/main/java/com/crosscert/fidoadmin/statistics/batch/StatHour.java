package com.crosscert.fidoadmin.statistics.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 집계 단위인 한 시간 구간 {@code [start, start+1h)}. 시각은 KST 벽시계 값이다.
 *
 * <p>{@link #groupby()} 는 FIDO_STATISTICS.GROUPBY 표기({@code yyyy/MM/dd/HH}, 24시간제)다.
 * 구 배치가 SQL {@code TO_CHAR(CREATEDTIME,'yyyy/MM/dd/hh24')} 로 만든 값과 같다.
 */
public record StatHour(LocalDateTime start) implements Comparable<StatHour> {

    static final DateTimeFormatter GROUPBY = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    public StatHour {
        if (start == null) throw new IllegalArgumentException("start");
        start = start.truncatedTo(ChronoUnit.HOURS);
    }

    public static StatHour of(LocalDateTime time) {
        return new StatHour(time);
    }

    public static StatHour parse(String groupby) {
        return new StatHour(LocalDateTime.parse(groupby + "/00/00",
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")));
    }

    public String groupby() {
        return start.format(GROUPBY);
    }

    public LocalDateTime end() {
        return start.plusHours(1);
    }

    /** 이 시간의 로그가 들어 있는 날짜 테이블의 날짜. */
    public LocalDate date() {
        return start.toLocalDate();
    }

    public StatHour plusHours(long hours) {
        return new StatHour(start.plusHours(hours));
    }

    @Override
    public int compareTo(StatHour o) {
        return start.compareTo(o.start);
    }
}
