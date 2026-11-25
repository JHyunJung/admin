package com.crosscert.fidoadmin.monitoring

import java.time.LocalDate
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

    @Test fun 상태_칸은_NONE이면_배지_없이_대시다() {
        assertThat(MonitoringView.plainStatus(LiveStatus.NONE, ColumnState.OK)).isEqualTo("-")
    }

    @Test fun 로그를_껐을_때의_UNKNOWN은_배지_없이_사용_안_함이다() {
        assertThat(MonitoringView.plainStatus(LiveStatus.UNKNOWN, ColumnState.DISABLED)).isEqualTo("사용 안 함")
        // 실제 실패는 확인 불가 배지다
        assertThat(MonitoringView.plainStatus(LiveStatus.UNKNOWN, ColumnState.FAILED)).isNull()
        assertThat(MonitoringView.plainStatus(LiveStatus.UNKNOWN, ColumnState.OK)).isNull()
        // 로그를 꺼도 예외로 낸 판정은 배지다
        assertThat(MonitoringView.plainStatus(LiveStatus.EXCEPTION_SPIKE, ColumnState.DISABLED)).isNull()
        assertThat(MonitoringView.plainStatus(LiveStatus.OK, ColumnState.OK)).isNull()
    }

    @Test fun 기준일_문구는_왜_판정하지_않는지를_말한다() {
        val d = LocalDate.of(2026, 11, 18)
        assertThat(MonitoringView.baselineNote(ColumnState.OK, ColumnState.OK, d)).isEqualTo("기준일: 2026-11-18 (수)")
        assertThat(MonitoringView.baselineNote(ColumnState.OK, ColumnState.OK, null))
            .isEqualTo("기준일 없음 — 끊김·감소를 판정하지 않습니다.")
        assertThat(MonitoringView.baselineNote(ColumnState.OK, ColumnState.FAILED, null))
            .isEqualTo("기준일 확인 불가 — 끊김·감소를 판정하지 않습니다.")
        assertThat(MonitoringView.baselineNote(ColumnState.FAILED, ColumnState.FAILED, null))
            .isEqualTo("로그 확인 불가 — 끊김·감소를 판정하지 않습니다.")
        assertThat(MonitoringView.baselineNote(ColumnState.DISABLED, ColumnState.DISABLED, null))
            .isEqualTo("로그 조회 사용 안 함 — 끊김·감소를 판정하지 않습니다.")
    }
}
