package com.crosscert.fidoadmin.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayProfileTest {

    private val d = LocalDate.of(2021, 1, 2)

    private val p = DayProfile.Builder(d)
        .add(1, 14 * 60 + 5, 3, d.atTime(14, 5, 40))
        .add(1, 14 * 60 + 59, 2, d.atTime(14, 59, 1))
        .add(1, 15 * 60, 7, d.atTime(15, 0, 9))
        .add(2, 0, 4, d.atTime(0, 0, 30))
        .build()

    @Test fun 고객사별_합계와_마지막_로그_시각을_돌려준다() {
        assertThat(p.total(1)).isEqualTo(12)
        assertThat(p.total(9)).isEqualTo(0)
        assertThat(p.lastLog(1)).isEqualTo(d.atTime(15, 0, 9))
        assertThat(p.lastLog(9)).isNull()
    }

    @Test fun 구간의_끝은_포함하지_않는다() {
        assertThat(p.count(1, d.atTime(14, 0), d.atTime(15, 0))).isEqualTo(5)
    }

    @Test fun 구간의_초는_버린다() {
        assertThat(p.count(1, d.atTime(14, 5, 59), d.atTime(15, 0, 59))).isEqualTo(5)
    }

    @Test fun 자정을_넘는_구간은_이_날짜에_속한_부분만_센다() {
        assertThat(p.count(2, d.minusDays(1).atTime(23, 30), d.atTime(0, 30))).isEqualTo(4)
        assertThat(p.count(2, d.minusDays(1).atTime(22, 0), d.minusDays(1).atTime(23, 0))).isEqualTo(0)
        assertThat(p.count(1, d.atTime(23, 30), d.plusDays(1).atTime(0, 30))).isEqualTo(0)
    }

    @Test fun 어느_고객사든_구간에_로그가_있는지_본다() {
        assertThat(p.anyBetween(d.atTime(15, 0), d.atTime(16, 0))).isTrue()
        assertThat(p.anyBetween(d.atTime(16, 0), d.atTime(23, 0))).isFalse()
    }

    @Test fun build_이후_빌더에_더해도_이미_만든_프로필은_바뀌지_않는다() {
        val b = DayProfile.Builder(d).add(1, 10, 3, d.atTime(0, 10))
        val built = b.build()

        b.add(1, 10, 5, d.atTime(0, 11))

        assertThat(built.total(1)).isEqualTo(3)
        assertThat(built.lastLog(1)).isEqualTo(d.atTime(0, 10))
        assertThat(b.build().total(1)).isEqualTo(8)
    }

    @Test fun 빈_프로필은_모두_0이다() {
        assertThat(DayProfile.empty(d).total(1)).isEqualTo(0)
        assertThat(DayProfile.empty(d).anyBetween(d.atStartOfDay(), d.plusDays(1).atStartOfDay())).isFalse()
    }
}
