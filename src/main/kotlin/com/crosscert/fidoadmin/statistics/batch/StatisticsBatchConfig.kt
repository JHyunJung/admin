package com.crosscert.fidoadmin.statistics.batch

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 통계 배치의 스케줄링을 켠다. `fido-admin.statistics.enabled=false` 면 스케줄러도 잡도 없다.
 * 스프링 기본 스케줄러는 스레드 하나라 별도 풀을 두지 않는다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(StatisticsBatchProperties::class)
@ConditionalOnProperty(prefix = "fido-admin.statistics", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class StatisticsBatchConfig
