package com.crosscert.fidoadmin.statistics.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StatHourTest {

    @Test void groupby는_24시간제_두자리다() {
        assertThat(StatHour.of(LocalDateTime.of(2026, 10, 15, 0, 59)).groupby()).isEqualTo("2026/10/15/00");
        assertThat(StatHour.of(LocalDateTime.of(2026, 10, 15, 14, 15, 30)).groupby()).isEqualTo("2026/10/15/14");
        assertThat(StatHour.of(LocalDateTime.of(2026, 10, 15, 23, 0)).groupby()).isEqualTo("2026/10/15/23");
    }

    @Test void 정각으로_자르고_한시간_구간이다() {
        StatHour h = StatHour.of(LocalDateTime.of(2026, 10, 15, 23, 40));
        assertThat(h.start()).isEqualTo(LocalDateTime.of(2026, 10, 15, 23, 0));
        assertThat(h.end()).isEqualTo(LocalDateTime.of(2026, 10, 16, 0, 0));
        assertThat(h.date()).isEqualTo(LocalDate.of(2026, 10, 15)); // 끝이 다음 날 0시여도 테이블은 그날
    }

    @Test void 자정을_넘기면_날짜가_바뀐다() {
        StatHour h = StatHour.parse("2026/10/15/23").plusHours(1);
        assertThat(h.groupby()).isEqualTo("2026/10/16/00");
        assertThat(h.date()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test void parse와_groupby는_서로_되돌린다() {
        assertThat(StatHour.parse("2026/01/02/03").groupby()).isEqualTo("2026/01/02/03");
    }
}
