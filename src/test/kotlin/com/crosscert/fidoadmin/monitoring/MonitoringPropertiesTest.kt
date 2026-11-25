package com.crosscert.fidoadmin.monitoring

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MonitoringPropertiesTest {

    private fun props(minBaseline: Long = 20, dropRatio: Double = 0.5, cacheSeconds: Long = 60) = MonitoringProperties(
        liveLogsEnabled = true, exceptionsEnabled = true, minBaseline = minBaseline, dropRatio = dropRatio,
        exceptionThreshold = 10, successDropPoints = 5.0, minAuth = 100, staleHours = 3,
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
}
