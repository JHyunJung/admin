package com.crosscert.fidoadmin.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MonitoringPropertiesTest {

    private fun props(
        minBaseline: Long = 20, dropRatio: Double = 0.5, cacheSeconds: Long = 60,
        successDropPoints: Double = 5.0, minAuth: Long = 100,
    ) = MonitoringProperties(
        liveLogsEnabled = true, exceptionsEnabled = true, minBaseline = minBaseline, dropRatio = dropRatio,
        exceptionThreshold = 10, successDropPoints = successDropPoints, minAuth = minAuth, staleHours = 3,
        cacheSeconds = cacheSeconds, queryTimeoutSeconds = 10,
    )

    @Test fun drop_ratio_가_1_이면_거부한다() {
        assertThatThrownBy { props(dropRatio = 1.0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("drop-ratio")
    }

    @Test fun cache_seconds_가_0_이면_거부한다() {
        assertThatThrownBy { props(cacheSeconds = 0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("cache-seconds")
    }

    @Test fun min_baseline_이_0_이면_거부한다() {
        assertThatThrownBy { props(minBaseline = 0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("min-baseline")
    }

    @Test fun success_drop_points_가_0_이하면_거부한다() {
        // 0 이하면 성공률이 그대로이거나 올라도 모든 줄이 하락이 된다
        assertThatThrownBy { props(successDropPoints = 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("success-drop-points")
        assertThatThrownBy { props(successDropPoints = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("success-drop-points")
    }

    @Test fun min_auth_가_음수면_거부한다() {
        assertThatThrownBy { props(minAuth = -1) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("min-auth")
    }

    @Test fun min_auth_0_은_허용한다() {
        assertThat(props(minAuth = 0).minAuth).isEqualTo(0L)
    }
}
