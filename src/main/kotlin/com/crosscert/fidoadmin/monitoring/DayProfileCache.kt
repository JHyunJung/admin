package com.crosscert.fidoadmin.monitoring

import com.crosscert.fidoadmin.log.service.FidoLogTable
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
 * 조회 실패(타임아웃 포함)도 날짜와 상관없이 `cacheSeconds` 동안 기억한다: 제한 시간에 걸리는 큰 테이블을
 * 화면을 열 때마다 다시 훑으면 부하 제한이 바로 그 경우에 사라진다. 보관 시간이 지나면 다시 시도한다.
 * `@Synchronized` 라 동시 요청이 와도 조회는 한 번이다.
 */
@Component
internal class DayProfileCache(
    private val reader: DayProfileReader,
    private val props: MonitoringProperties,
    private val clock: Clock,
    private val tables: FidoLogTable,
) {

    @Autowired
    constructor(reader: DayProfileReader, props: MonitoringProperties, tables: FidoLogTable) :
        this(reader, props, Clock.system(FidoStatisticsJob.ZONE), tables)

    /**
     * @param profile null = 조회 실패. 보관 시간 동안만 기억한다
     * @param readOn  읽은 날(오늘). 날짜가 이보다 앞서야 그날이 끝난 뒤에 읽은 완전한 값이다
     */
    private class Entry(val profile: DayProfile?, val readAt: Instant, val readOn: LocalDate)

    private val entries = HashMap<LocalDate, Entry>()

    /** 테이블이 없으면 DayProfile.empty(date). 조회 실패면 null(보관 시간 동안은 다시 읽지 않고 null). */
    @Synchronized
    fun get(date: LocalDate): DayProfile? {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        entries.keys.removeIf { it.isBefore(today.minusDays(RETAIN_DAYS)) }

        val cached = entries[date]
        if (cached != null && usable(cached, date, today, now)) return cached.profile

        val profile = try {
            reader.read(date) ?: DayProfile.empty(date)
        } catch (e: DataAccessException) {
            log.warn("monitoring: {} 조회 실패 — {}", tables.nameFor(date), e.javaClass.simpleName)
            null
        }
        // 보관 기준은 조회가 끝난 때다. 시작 때로 잡으면 타임아웃이 보관 시간보다 길 때 저장하자마자 만료돼
        // 기다리던 요청마다 다시 읽는다. 읽은 날은 시작 때의 날짜로 둔다 — 자정을 걸친 조회는 덜 찬 값이다.
        entries[date] = Entry(profile, clock.instant(), today)
        return profile
    }

    /**
     * 실패는 보관 시간 안일 때만 쓴다. 값은 그날이 끝난 뒤 읽은 것이거나, 읽은 날이 아직 오늘이면서 보관 시간 안일 때만
     * 쓴다. 자정을 넘기면 보관 시간이 남아도 다시 읽는다.
     */
    private fun usable(e: Entry, date: LocalDate, today: LocalDate, now: Instant): Boolean {
        val fresh = now.isBefore(e.readAt.plusSeconds(props.cacheSeconds))
        if (e.profile == null) return fresh
        return date.isBefore(e.readOn) || (e.readOn == today && fresh)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DayProfileCache::class.java)

        private const val RETAIN_DAYS = 8L
    }
}
