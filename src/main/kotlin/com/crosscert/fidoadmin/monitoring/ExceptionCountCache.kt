package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/**
 * [ExceptionCountReader] 앞의 보관소. `CCFA_EXCEPTIONS` 는 PK 말고 인덱스가 없어 조회가 곧 전체 읽기라,
 * 화면을 열 때마다 읽지 않고 마지막 결과(실패 포함)를 `cacheSeconds` 동안 그대로 준다.
 *
 * 보관 중에는 받은 `now` 와 상관없이 읽은 때의 값을 준다 — 화면 머리글이 "N초마다 갱신된 값"이라고 알린다.
 * 다만 날짜가 바뀌면 다시 읽는다(전날 누계를 '오늘'로 보이지 않게).
 * `@Synchronized` 라 동시 요청이 와도 조회는 한 번이다.
 */
@Component
internal class ExceptionCountCache(
    private val reader: ExceptionCountReader,
    private val props: MonitoringProperties,
    private val clock: Clock,
) {

    @Autowired
    constructor(reader: ExceptionCountReader, props: MonitoringProperties) :
        this(reader, props, Clock.system(FidoStatisticsJob.ZONE))

    /**
     * @param counts null = 조회 실패
     * @param readAt 조회가 끝난 때. 시작 때로 잡으면 조회가 보관 시간보다 오래 걸릴 때 저장하자마자 만료된다
     * @param readOn 읽은 날. '예외 오늘'은 날짜에 묶인 값이라 자정을 넘기면 보관 시간이 남아도 쓰지 않는다
     */
    private class Held(val counts: Map<Long, ErrorCounts>?, val readAt: Instant, val readOn: LocalDate)

    private var held: Held? = null

    /** 고객사별 건수. 조회 실패면 null. */
    @Synchronized
    fun get(now: LocalDateTime): Map<Long, ErrorCounts>? {
        val today = LocalDate.now(clock)
        held?.let {
            if (it.readOn == today && clock.instant().isBefore(it.readAt.plusSeconds(props.cacheSeconds))) return it.counts
        }

        val counts = try {
            reader.read(now)
        } catch (e: DataAccessException) {
            log.warn("monitoring: 예외 건수 조회 실패 — {}", e.javaClass.simpleName)
            null
        }
        held = Held(counts, clock.instant(), today)
        return counts
    }

    private companion object {
        val log = LoggerFactory.getLogger(ExceptionCountCache::class.java)
    }
}
