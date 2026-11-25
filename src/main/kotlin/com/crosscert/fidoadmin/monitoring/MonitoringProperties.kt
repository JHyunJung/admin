package com.crosscert.fidoadmin.monitoring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Configuration

/**
 * SUPER 관제 화면 설정.
 *
 * @param liveLogsEnabled     오늘의 FIDO_LOGS 를 읽어 실시간 상태를 낼지. 끄면 그 값은 "사용 안 함"이다
 * @param exceptionsEnabled   예외 로그를 읽을지. 끄면 그 값은 "사용 안 함"이다
 * @param minBaseline         끊김·감소를 판정할 최소 기준일 건수. 이보다 적으면 트래픽이 원래 적은 것으로 본다
 * @param dropRatio           기준 대비 이 비율 미만이면 감소(0 초과 1 미만)
 * @param exceptionThreshold  최근 구간의 예외가 이 건수 이상이면 예외 급증
 * @param successDropPoints   주간 인증 성공률이 지난주보다 이 %p 이상 내려가면 하락
 * @param minAuth             하락 표시를 할 최소 주간 인증량. 표본이 적으면 흔들림이 크다
 * @param staleHours          통계 집계가 이 시간 넘게 멈추면 오래된 것으로 본다
 * @param cacheSeconds        조회 결과를 캐시할 시간(초)
 * @param queryTimeoutSeconds 로그 조회 한 건의 제한 시간(초)
 */
@JvmRecord
@ConfigurationProperties("fido-admin.monitoring")
data class MonitoringProperties(
    @DefaultValue("true") val liveLogsEnabled: Boolean,
    @DefaultValue("true") val exceptionsEnabled: Boolean,
    @DefaultValue("20") val minBaseline: Long,
    @DefaultValue("0.5") val dropRatio: Double,
    @DefaultValue("10") val exceptionThreshold: Long,
    @DefaultValue("5.0") val successDropPoints: Double,
    @DefaultValue("100") val minAuth: Long,
    @DefaultValue("3") val staleHours: Int,
    @DefaultValue("60") val cacheSeconds: Long,
    @DefaultValue("10") val queryTimeoutSeconds: Int,
) {
    init {
        require(minBaseline >= 1) { "fido-admin.monitoring.min-baseline 은 1 이상이어야 합니다" }
        require(dropRatio > 0.0 && dropRatio < 1.0) { "fido-admin.monitoring.drop-ratio 는 0 초과 1 미만이어야 합니다" }
        require(exceptionThreshold >= 1) { "fido-admin.monitoring.exception-threshold 는 1 이상이어야 합니다" }
        // 0 이하면 성공률이 그대로여도 모든 줄이 하락이 된다
        require(successDropPoints > 0.0) { "fido-admin.monitoring.success-drop-points 는 0 보다 커야 합니다" }
        require(minAuth >= 0) { "fido-admin.monitoring.min-auth 는 0 이상이어야 합니다" }
        require(staleHours >= 0) { "fido-admin.monitoring.stale-hours 는 0 이상이어야 합니다" }
        require(cacheSeconds >= 1) { "fido-admin.monitoring.cache-seconds 는 1 이상이어야 합니다" }
        require(queryTimeoutSeconds >= 1) { "fido-admin.monitoring.query-timeout-seconds 는 1 이상이어야 합니다" }
    }
}

/** 관제 설정을 빈으로 등록한다. 조건 없이 항상 켜진다(끄는 값은 키마다 따로 있다). */
@Configuration
@EnableConfigurationProperties(MonitoringProperties::class)
class MonitoringConfig
