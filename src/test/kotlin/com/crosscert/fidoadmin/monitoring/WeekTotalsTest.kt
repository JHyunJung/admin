package com.crosscert.fidoadmin.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeekTotalsTest {

    private val P = MonitoringProperties(
        liveLogsEnabled = true, exceptionsEnabled = true, minBaseline = 20, dropRatio = 0.5,
        exceptionThreshold = 10, successDropPoints = 5.0, minAuth = 100, staleHours = 3,
        cacheSeconds = 60, queryTimeoutSeconds = 10,
    )
    private val cur = WeekTotals(912, 88, 97, 3, 20)
    private val prev = WeekTotals(976, 24, 50, 0, 0)
    private val zero = WeekTotals(0, 0, 0, 0, 0)

    @Test fun 비율과_순증감을_계산한다() {
        assertThat(cur.authTotal()).isEqualTo(1000)
        assertThat(cur.authRate()).isEqualTo(91.2)
        assertThat(cur.regRate()).isEqualTo(97.0)
        assertThat(cur.netReg()).isEqualTo(77)
    }

    @Test fun 분모가_0이면_비율은_null() {
        assertThat(zero.authRate()).isNull()
        assertThat(zero.regRate()).isNull()
    }

    @Test fun 지난주와_비교한다() {
        assertThat(cur.authChangePct(prev)).isEqualTo(0.0)
        assertThat(cur.authRatePointChange(prev)).isEqualTo(-6.4)
        assertThat(cur.dropped(prev, P)).isTrue()
    }

    @Test fun 지난주가_없거나_0이면_변화율은_null() {
        assertThat(cur.authChangePct(null)).isNull()
        assertThat(cur.authChangePct(zero)).isNull()
        assertThat(cur.dropped(null, P)).isFalse()
    }

    @Test fun 어느_한쪽_비율이_null이면_p_변화도_null() {
        assertThat(cur.authRatePointChange(zero)).isNull()
        assertThat(zero.authRatePointChange(prev)).isNull()
    }

    @Test fun 인증량이_min_auth_미만이면_하락_표시를_하지_않는다() {
        assertThat(WeekTotals(50, 49, 0, 0, 0).dropped(WeekTotals(99, 0, 0, 0, 0), P)).isFalse()
    }

    @Test fun 정확히_5_0p_하락은_하락이다() {
        assertThat(WeekTotals(950, 50, 0, 0, 0).dropped(WeekTotals(1000, 0, 0, 0, 0), P)).isTrue()
    }
}
