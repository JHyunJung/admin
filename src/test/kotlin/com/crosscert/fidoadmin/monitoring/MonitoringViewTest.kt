package com.crosscert.fidoadmin.monitoring

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MonitoringViewTest {

    @Test fun 건수는_천_단위_쉼표를_붙인다() {
        assertThat(MonitoringView.count(1234567L, ColumnState.OK)).isEqualTo("1,234,567")
        assertThat(MonitoringView.count(0L, ColumnState.OK)).isEqualTo("0")
    }

    @Test fun 값이_없으면_열_상태에_따라_문구가_갈린다() {
        assertThat(MonitoringView.count(null, ColumnState.FAILED)).isEqualTo("확인 불가")
        assertThat(MonitoringView.count(null, ColumnState.DISABLED)).isEqualTo("사용 안 함")
        assertThat(MonitoringView.count(null, ColumnState.OK)).isEqualTo("-")
    }

    @Test fun 값이_있으면_열_상태와_무관하게_값을_보인다() {
        assertThat(MonitoringView.count(5L, ColumnState.FAILED)).isEqualTo("5")
    }

    @Test fun 마지막_로그_시각은_시분초로_보인다() {
        assertThat(MonitoringView.time(LocalDateTime.of(2026, 11, 25, 13, 2, 9), ColumnState.OK)).isEqualTo("13:02:09")
        assertThat(MonitoringView.time(null, ColumnState.OK)).isEqualTo("-")
        assertThat(MonitoringView.time(null, ColumnState.FAILED)).isEqualTo("확인 불가")
    }

    @Test fun 증감은_부호와_퍼센트를_붙인다() {
        assertThat(MonitoringView.pct(12.3)).isEqualTo("+12.3%")
        assertThat(MonitoringView.pct(-6.5)).isEqualTo("-6.5%")
        assertThat(MonitoringView.pct(0.0)).isEqualTo("0.0%")
        assertThat(MonitoringView.pct(null)).isEqualTo("-")
    }

    @Test fun 큰_비율도_천_단위_쉼표를_붙인다() {
        assertThat(MonitoringView.pct(1200.0)).isEqualTo("+1,200.0%")
        assertThat(MonitoringView.pct(-1234.5)).isEqualTo("-1,234.5%")
        assertThat(MonitoringView.points(1200.0)).isEqualTo("+1,200.0%p")
        assertThat(MonitoringView.rate(1200.0)).isEqualTo("1,200.0%")
    }

    @Test fun 변화는_부호와_퍼센트포인트를_붙인다() {
        assertThat(MonitoringView.points(-6.4)).isEqualTo("-6.4%p")
        assertThat(MonitoringView.points(2.0)).isEqualTo("+2.0%p")
        assertThat(MonitoringView.points(null)).isEqualTo("-")
    }

    @Test fun 비율은_퍼센트만_붙인다() {
        assertThat(MonitoringView.rate(91.2)).isEqualTo("91.2%")
        assertThat(MonitoringView.rate(null)).isEqualTo("-")
    }

    @Test fun 순증은_부호를_붙인다() {
        assertThat(MonitoringView.signed(77L)).isEqualTo("+77")
        assertThat(MonitoringView.signed(-3L)).isEqualTo("-3")
        assertThat(MonitoringView.signed(0L)).isEqualTo("0")
        assertThat(MonitoringView.signed(1234L)).isEqualTo("+1,234")
    }
}
