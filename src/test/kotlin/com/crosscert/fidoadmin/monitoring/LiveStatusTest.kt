package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.monitoring.LiveStatus.Companion.judge
import com.crosscert.fidoadmin.monitoring.LiveStatus.DOWN
import com.crosscert.fidoadmin.monitoring.LiveStatus.DROP
import com.crosscert.fidoadmin.monitoring.LiveStatus.EXCEPTION_SPIKE
import com.crosscert.fidoadmin.monitoring.LiveStatus.NONE
import com.crosscert.fidoadmin.monitoring.LiveStatus.OK
import com.crosscert.fidoadmin.monitoring.LiveStatus.UNKNOWN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LiveStatusTest {

    private val P = MonitoringProperties(
        liveLogsEnabled = true, exceptionsEnabled = true, minBaseline = 20, dropRatio = 0.5,
        exceptionThreshold = 10, successDropPoints = 5.0, minAuth = 100, staleHours = 3,
        cacheSeconds = 60, queryTimeoutSeconds = 10,
    )

    @Test fun 기준이_있는데_최근이_0이면_끊김() { assertThat(judge(0, 20, 300, 0, P)).isEqualTo(DOWN) }
    @Test fun 기준이_최소치_미만이면_끊김이_아니다() { assertThat(judge(0, 19, 0, 0, P)).isEqualTo(NONE) }
    @Test fun 절반_미만이면_감소() { assertThat(judge(49, 100, 500, 0, P)).isEqualTo(DROP) }
    @Test fun 정확히_절반은_감소가_아니다() { assertThat(judge(50, 100, 500, 0, P)).isEqualTo(OK) }
    @Test fun 예외_급증은_감소보다_앞선다() { assertThat(judge(49, 100, 500, 10, P)).isEqualTo(EXCEPTION_SPIKE) }
    @Test fun 끊김은_예외_급증보다_앞선다() { assertThat(judge(0, 100, 500, 99, P)).isEqualTo(DOWN) }
    @Test fun 기준일이_없으면_끊김_감소를_판정하지_않는다() { assertThat(judge(0, null, 5, 0, P)).isEqualTo(OK) }
    @Test fun 로그를_모르면_확인_불가() { assertThat(judge(null, null, null, 0, P)).isEqualTo(UNKNOWN) }
    @Test fun 로그를_몰라도_예외_급증은_보인다() { assertThat(judge(null, null, null, 10, P)).isEqualTo(EXCEPTION_SPIKE) }
    @Test fun 예외를_몰라도_로그_판정은_한다() { assertThat(judge(30, 30, 100, null, P)).isEqualTo(OK) }
    @Test fun 오늘도_기준도_0이면_트래픽_없음() { assertThat(judge(0, 0, 0, 0, P)).isEqualTo(NONE) }
}
