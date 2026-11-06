package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import com.crosscert.fidoadmin.statistics.batch.StatisticsBatchProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 배치를 켠 설정으로 앱이 뜨고 잡이 실제 DB 에서 한 번 끝까지 도는지. 다른 테스트는 배치를 끈다.
 * cron 은 먼 미래(1년에 한 번)로 둬 테스트 중 저절로 돌지 않게 한다.
 */
@SpringBootTest(properties = [
    "fido-admin.statistics.enabled=true",
    "fido-admin.statistics.cron=0 0 0 1 1 *",
    "fido-admin.statistics.catch-up-hours=48"])
@ActiveProfiles("test")
class StatisticsJobWiringIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var job: FidoStatisticsJob
    @Autowired lateinit var props: StatisticsBatchProperties

    @Test
    fun 켜면_잡이_등록되고_한번_돈다() {
        assertThat(props.catchUpHours).isEqualTo(48)
        // 시드의 최근 시간 통계가 이미 있고 최근 날짜 테이블은 없으므로 예외 없이 끝나기만 하면 된다.
        job.run()
    }
}
