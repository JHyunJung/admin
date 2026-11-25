package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.statistics.batch.FidoStatisticsJob
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/**
 * [DayProfileReader] 앞의 메모리 보관소. 관제 화면이 열릴 때마다 날짜 테이블을 훑지 않게 한다.
 *
 * 당일 테이블은 계속 늘어나므로 `cacheSeconds` 동안만 쓰고, 지난 날짜는 더 늘지 않으니 계속 보관한다.
 * 다만 당일에 읽어 둔 것은 그날의 끝이 빠져 있을 수 있어 "지난 날짜"로 치지 않는다. 날짜가 바뀐 뒤 한 번 다시 읽고
 * 나서야 계속 보관한다. 오늘보다 8일 넘게 지난 항목은 [get] 때 버려 메모리가 쌓이지 않게 한다.
 * `@Synchronized` 라 동시 요청이 와도 조회는 한 번이다.
 */
@Component
class DayProfileCache internal constructor(
    private val reader: DayProfileReader,
    private val props: MonitoringProperties,
    private val clock: Clock,
) {

    @Autowired
    constructor(reader: DayProfileReader, props: MonitoringProperties) :
        this(reader, props, Clock.system(FidoStatisticsJob.ZONE))

    /** @param readOn 읽은 날(오늘). 날짜가 이보다 앞서야 그날이 끝난 뒤에 읽은 완전한 값이다 */
    private class Entry(val profile: DayProfile, val readAt: Instant, val readOn: LocalDate)

    private val entries = HashMap<LocalDate, Entry>()

    /** 테이블이 없으면 DayProfile.empty(date). 조회 실패면 null(보관하지 않는다). */
    @Synchronized
    fun get(date: LocalDate): DayProfile? {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        entries.keys.removeIf { it.isBefore(today.minusDays(RETAIN_DAYS)) }

        val cached = entries[date]
        if (cached != null && usable(cached, date, now)) return cached.profile

        val profile = try {
            reader.read(date) ?: DayProfile.empty(date)
        } catch (e: DataAccessException) {
            log.warn("monitoring: {} 날짜 테이블 조회 실패 — {}", date, e.javaClass.simpleName)
            return null
        }
        entries[date] = Entry(profile, now, today)
        return profile
    }

    private fun usable(e: Entry, date: LocalDate, now: Instant): Boolean =
        date.isBefore(e.readOn) || now.isBefore(e.readAt.plusSeconds(props.cacheSeconds))

    companion object {
        private val log = LoggerFactory.getLogger(DayProfileCache::class.java)

        private const val RETAIN_DAYS = 8L
    }
}
