package com.crosscert.fidoadmin.statistics.batch

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * FIDO_STATISTICS 시간별 집계 배치 설정.
 *
 * @param enabled       배치를 켤지. 끄면 스케줄러 자체가 등록되지 않는다
 * @param cron          실행 주기(Asia/Seoul). 기본 매시 15분
 * @param catchUpHours  최근 몇 시간 안에서 빠진 구간을 채울지. 멈춘 구간을 한 번에 채울 때만 잠시 올린다
 */
@JvmRecord
@ConfigurationProperties("fido-admin.statistics")
data class StatisticsBatchProperties(
    @DefaultValue("true") val enabled: Boolean,
    @DefaultValue("0 15 * * * *") val cron: String,
    @DefaultValue("72") val catchUpHours: Int,
) {
    init {
        if (catchUpHours < 1) throw IllegalArgumentException("fido-admin.statistics.catch-up-hours 는 1 이상이어야 합니다")
    }
}
