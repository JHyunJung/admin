# SUPER 관제 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SUPER 가 고객사를 고르지 않고 여는 `/monitoring` 화면에서 당일 끊김·예외 급증(원본 건수)과 어제까지의 성공률·증감(통계)을 전 고객사에 대해 본다.

**Architecture:** 새 패키지 `monitoring`. 날짜 테이블을 한 번 읽어 고객사×분 단위 건수(`DayProfile`)로 만들고 메모리에 보관한다(당일 60초, 지난 날짜는 계속). 상태 판정과 주간 계산은 DB·시계와 분리한 순수 함수다. 기존 대시보드·통계 배치는 건드리지 않는다.

**Tech Stack:** Kotlin, Spring Boot(MVC·Security), `NamedParameterJdbcTemplate`, Oracle, Thymeleaf, JUnit5 + mockito-kotlin + AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-25-super-monitoring-design.md`

## Global Constraints

- 작업 브랜치는 `main` 에서 딴 `feat/super-monitoring` 이다(현재 `build/nexus-repo-url` 과 미커밋 `README.md` 는 건드리지 않는다).
- `JSONDATA`(CLOB)를 읽지 않는다. 새 테이블·인덱스를 만들지 않는다. 통계 배치, `StatisticsQueryService`, `dashboard/*` 를 고치지 않는다.
- 날짜 테이블 이름은 `FidoLogTable.nameFor(LocalDate)` 로만 만든다. 그 밖의 값은 모두 바인드한다.
- 시각은 Asia/Seoul 벽시계다. JDBC 비교는 `Timestamp.valueOf(LocalDateTime)` 을 쓴다(`HourlyAggregator` 와 같다). 시간대는 `FidoStatisticsJob.ZONE` 을 쓴다.
- 고객사 범위는 `CCFA_COMPANY` 에 있는 것이다. `COMPANY_IDX` 가 0·NULL·목록에 없는 값은 고객사 줄이 아니라 "공통"으로 간다. `IDX = 0` 인 고객사는 줄을 만들지 않는다.
- 읽지 못한 값은 0 이 아니라 `null`(화면에서 "확인 불가")이다. 설정으로 끈 값도 `null`(화면에서 "사용 안 함")이다.
- 비율은 소수 첫째 자리까지 반올림한다(`Math.round(x * 10) / 10.0`, `StatTotals.authSuccessRate` 와 같다). 분모 0 은 `null`.
- 설정 기본값: `live-logs-enabled=true`, `exceptions-enabled=true`, `min-baseline=20`, `drop-ratio=0.5`, `exception-threshold=10`, `success-drop-points=5.0`, `min-auth=100`, `stale-hours=3`, `cache-seconds=60`, `query-timeout-seconds=10`.
- 접근은 두 겹이다: `SecurityConfig.SUPER_PATHS` 와 서비스의 `tenant.require().isSuper` 검사.
- 주석·커밋 메시지·테스트 이름은 기존 관례(한국어, `feat:`/`test:` 접두)를 따른다. 커밋 끝에 세션 attribution 을 붙인다.
- 기준선: `./gradlew test` 1041 tests · 0 fail · 1 skip. 줄어들면 안 된다.

## Review Focus

1. **자정 직후(00:00~00:59)에 연다.** 최근 60분이 전날 테이블로 넘어간다. 전날 꼬리와 오늘 머리를 더해야 하고 기준일도 같은 방식이어야 한다. → Task 2·5 테스트.
2. **당일 테이블이 아직 없다(로그 0건인 날).** 500 이 아니라 모두 0, 마지막 로그 "-", 기준이 20 이상이면 끊김. → Task 3·5 테스트.
3. **조회 실패·타임아웃.** 로그 조회가 실패해도 예외 열과 통계 표는 나오고, 실패한 열은 "확인 불가"다. 실패 결과는 보관하지 않는다(다음 요청에 다시 시도). → Task 3·5 테스트.
4. **`CCFA_COMPANY` 에 없는 `COMPANY_IDX` 의 예외·통계 행.** 예외는 공통 줄로 모으고, 통계 표에는 줄을 만들지 않는다. NPE 가 나면 안 된다. → Task 4·5 테스트.
5. **통계가 14일보다 짧게 있거나 전혀 없다.** 직전 7일이 비면 증감·변화는 "-" 이고 하락 표시를 하지 않는다. 전혀 없으면 표 대신 "집계된 통계가 없습니다". → Task 1·4·6 테스트.

---

## File Structure

`src/main/kotlin/com/crosscert/fidoadmin/monitoring/`

| 파일 | 책임 |
|---|---|
| `MonitoringProperties.kt` | `fido-admin.monitoring.*` 설정과 검증. `MonitoringConfig`(같은 파일)가 등록한다 |
| `LiveStatus.kt` | 상태 enum 과 판정 순수 함수 |
| `WeekTotals.kt` | 한 고객사 7일 합계와 비율·증감 순수 계산 |
| `DayProfile.kt` | 한 날짜의 고객사×분 건수와 구간 합 |
| `DayProfileReader.kt` | 날짜 테이블 → `DayProfile` SQL |
| `DayProfileCache.kt` | 보관·만료·단일 조회 |
| `ExceptionCountReader.kt` | `CCFA_EXCEPTIONS` ERROR 건수 SQL |
| `StatBoardReader.kt` | `FIDO_STATISTICS` 최신 GROUPBY·주간 합계 SQL |
| `MonitoringBoard.kt` | 화면 모델(값 객체들) |
| `MonitoringService.kt` | 위를 모아 `MonitoringBoard` 를 만든다. SUPER 검사 |
| `MonitoringController.kt` | `GET /monitoring` |

그 밖: `templates/monitoring/index.html`(새로), `config/SecurityConfig.kt`·`common/MenuRegistry.kt`·`application.yml`(수정), `docker/init/03-dummy.sql`(선택).

테스트는 `src/test/kotlin/com/crosscert/fidoadmin/monitoring/` 와 `integration/MonitoringReadersIntegrationTest.kt`.

---

### Task 1: 설정과 순수 계산 (상태 판정, 주간 합계)

**Files:**
- Create: `monitoring/MonitoringProperties.kt`, `monitoring/LiveStatus.kt`, `monitoring/WeekTotals.kt`
- Modify: `src/main/resources/application.yml` (`fido-admin.statistics` 블록 아래에 `monitoring` 블록)
- Test: `monitoring/LiveStatusTest.kt`, `monitoring/WeekTotalsTest.kt`, `monitoring/MonitoringPropertiesTest.kt`

**Interfaces — Produces:**

```kotlin
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
)
// 같은 파일: @Configuration @EnableConfigurationProperties(MonitoringProperties::class) class MonitoringConfig

enum class LiveStatus(val label: String, val badge: String) {
    DOWN("끊김", "text-bg-danger"), EXCEPTION_SPIKE("예외 급증", "text-bg-danger"),
    DROP("감소", "text-bg-warning"), OK("정상", "text-bg-success"),
    NONE("-", ""), UNKNOWN("확인 불가", "text-bg-secondary");
    companion object {
        /** null = 그 값을 모른다(조회 실패·사용 안 함·기준일 없음). 선언 순서가 화면 정렬 순서다. */
        fun judge(recent: Long?, baseline: Long?, todayTotal: Long?, recentErrors: Long?, p: MonitoringProperties): LiveStatus
    }
}

data class WeekTotals(val authS: Long, val authF: Long, val regS: Long, val regF: Long, val deregS: Long) {
    fun authTotal(): Long
    fun authRate(): Double?          // 분모 0 → null
    fun regRate(): Double?
    fun netReg(): Long               // regS - deregS
    fun authChangePct(prev: WeekTotals?): Double?   // prev 없음 또는 prev.authTotal()==0 → null
    fun authRatePointChange(prev: WeekTotals?): Double?  // 어느 한쪽 rate 가 null → null
    fun dropped(prev: WeekTotals?, p: MonitoringProperties): Boolean
}
```

판정 규칙(위에서부터 먼저 맞는 것):
1. `recent != null && baseline != null && baseline >= minBaseline && recent == 0` → `DOWN`
2. `recentErrors != null && recentErrors >= exceptionThreshold` → `EXCEPTION_SPIKE`
3. `recent != null && baseline != null && baseline >= minBaseline && recent < baseline * dropRatio` → `DROP`
4. `recent == null || todayTotal == null` → `UNKNOWN`
5. `todayTotal > 0` → `OK`, 아니면 `NONE`

`dropped` = `authTotal() >= minAuth` 이고 `authRatePointChange(prev)` 가 `-successDropPoints` 이하.

검증(`init`): `minBaseline >= 1`, `0 < dropRatio < 1`, `exceptionThreshold >= 1`, `staleHours >= 0`, `cacheSeconds >= 1`, `queryTimeoutSeconds >= 1`. 어기면 `IllegalArgumentException`(메시지에 키 이름).

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`LiveStatusTest` (props 는 기본값 `P`):

```kotlin
@Test fun 기준이_있는데_최근이_0이면_끊김() = assertThat(judge(0, 20, 300, 0, P)).isEqualTo(DOWN)
@Test fun 기준이_최소치_미만이면_끊김이_아니다() = assertThat(judge(0, 19, 0, 0, P)).isEqualTo(NONE)
@Test fun 절반_미만이면_감소() = assertThat(judge(49, 100, 500, 0, P)).isEqualTo(DROP)
@Test fun 정확히_절반은_감소가_아니다() = assertThat(judge(50, 100, 500, 0, P)).isEqualTo(OK)
@Test fun 예외_급증은_감소보다_앞선다() = assertThat(judge(49, 100, 500, 10, P)).isEqualTo(EXCEPTION_SPIKE)
@Test fun 끊김은_예외_급증보다_앞선다() = assertThat(judge(0, 100, 500, 99, P)).isEqualTo(DOWN)
@Test fun 기준일이_없으면_끊김_감소를_판정하지_않는다() = assertThat(judge(0, null, 5, 0, P)).isEqualTo(OK)
@Test fun 로그를_모르면_확인_불가() = assertThat(judge(null, null, null, 0, P)).isEqualTo(UNKNOWN)
@Test fun 로그를_몰라도_예외_급증은_보인다() = assertThat(judge(null, null, null, 10, P)).isEqualTo(EXCEPTION_SPIKE)
@Test fun 예외를_몰라도_로그_판정은_한다() = assertThat(judge(30, 30, 100, null, P)).isEqualTo(OK)
@Test fun 오늘도_기준도_0이면_트래픽_없음() = assertThat(judge(0, 0, 0, 0, P)).isEqualTo(NONE)
```

`WeekTotalsTest`:

```kotlin
val cur = WeekTotals(912, 88, 97, 3, 20); val prev = WeekTotals(976, 24, 50, 0, 0)
assertThat(cur.authRate()).isEqualTo(91.2); assertThat(cur.regRate()).isEqualTo(97.0); assertThat(cur.netReg()).isEqualTo(77)
assertThat(cur.authChangePct(prev)).isEqualTo(0.0)
assertThat(cur.authRatePointChange(prev)).isEqualTo(-6.4)
assertThat(cur.dropped(prev, P)).isTrue()
assertThat(WeekTotals(0, 0, 0, 0, 0).authRate()).isNull()
assertThat(cur.authChangePct(null)).isNull(); assertThat(cur.dropped(null, P)).isFalse()
assertThat(cur.authChangePct(WeekTotals(0, 0, 0, 0, 0))).isNull()
// 인증량이 min-auth 미만이면 하락 표시를 하지 않는다
assertThat(WeekTotals(50, 49, 0, 0, 0).dropped(WeekTotals(99, 0, 0, 0, 0), P)).isFalse()
// 정확히 -5.0%p 는 하락이다
assertThat(WeekTotals(950, 50, 0, 0, 0).dropped(WeekTotals(1000, 0, 0, 0, 0), P)).isTrue()
```

`MonitoringPropertiesTest`: `dropRatio = 1.0`, `cacheSeconds = 0`, `minBaseline = 0` 이 각각 `IllegalArgumentException`.

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests 'com.crosscert.fidoadmin.monitoring.*'` → 컴파일 실패.
- [ ] **Step 3: 세 파일을 구현하고 `application.yml` 에 블록을 넣는다.** yml 은 `statistics` 블록과 같은 양식(키마다 환경변수 이름 주석)으로 `live-logs-enabled: ${FIDO_ADMIN_MONITORING_LIVE_LOGS_ENABLED:true}`, `exceptions-enabled: ${FIDO_ADMIN_MONITORING_EXCEPTIONS_ENABLED:true}`, `cache-seconds: ${FIDO_ADMIN_MONITORING_CACHE_SECONDS:60}` 세 개만 환경변수로 연다. 나머지는 기본값을 쓴다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS.
- [ ] **Step 5: 커밋** — `feat: 관제 화면의 설정과 상태 판정·주간 계산을 둔다`

---

### Task 2: DayProfile — 고객사×분 건수와 구간 합

**Files:**
- Create: `monitoring/DayProfile.kt`
- Test: `monitoring/DayProfileTest.kt`

**Interfaces — Produces:**

```kotlin
class DayProfile(val date: LocalDate, private val counts: Map<Long, LongArray> /* 길이 1440 */, private val last: Map<Long, LocalDateTime>) {
    fun total(companyIdx: Long): Long
    fun lastLog(companyIdx: Long): LocalDateTime?
    /** [from, to) 중 이 날짜에 속하는 부분만 센다. 범위가 이 날짜와 겹치지 않으면 0. */
    fun count(companyIdx: Long, from: LocalDateTime, to: LocalDateTime): Long
    /** 어느 고객사든 [from, to) 에 로그가 있는가. */
    fun anyBetween(from: LocalDateTime, to: LocalDateTime): Boolean
    class Builder(val date: LocalDate) { fun add(companyIdx: Long, minuteOfDay: Int, n: Long, last: LocalDateTime): Builder; fun build(): DayProfile }
    companion object { fun empty(date: LocalDate): DayProfile }
}
```

분 단위로 자른다: `from`·`to` 는 분으로 내림한다. 자정을 넘는 구간은 호출하는 쪽이 두 날짜의 `count` 를 더한다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
val d = LocalDate.of(2021, 1, 2)
val p = DayProfile.Builder(d).add(1, 14 * 60 + 5, 3, d.atTime(14, 5, 40)).add(1, 14 * 60 + 59, 2, d.atTime(14, 59, 1))
    .add(1, 15 * 60, 7, d.atTime(15, 0, 9)).add(2, 0, 4, d.atTime(0, 0, 30)).build()
assertThat(p.total(1)).isEqualTo(12); assertThat(p.total(9)).isEqualTo(0)
assertThat(p.lastLog(1)).isEqualTo(d.atTime(15, 0, 9)); assertThat(p.lastLog(9)).isNull()
assertThat(p.count(1, d.atTime(14, 0), d.atTime(15, 0))).isEqualTo(5)      // 끝은 포함하지 않는다
assertThat(p.count(1, d.atTime(14, 5, 59), d.atTime(15, 0, 59))).isEqualTo(5) // 초는 버린다
// 자정을 넘는 구간: 이 날짜에 속한 부분만
assertThat(p.count(2, d.minusDays(1).atTime(23, 30), d.atTime(0, 30))).isEqualTo(4)
assertThat(p.count(2, d.minusDays(1).atTime(22, 0), d.minusDays(1).atTime(23, 0))).isEqualTo(0)
assertThat(p.count(1, d.atTime(23, 30), d.plusDays(1).atTime(0, 30))).isEqualTo(0)
assertThat(p.anyBetween(d.atTime(15, 0), d.atTime(16, 0))).isTrue()
assertThat(p.anyBetween(d.atTime(16, 0), d.atTime(23, 0))).isFalse()
assertThat(DayProfile.empty(d).total(1)).isEqualTo(0)
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*DayProfileTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat: 날짜 테이블의 고객사×분 건수를 담는 DayProfile`

---

### Task 3: 날짜 테이블 조회와 보관

**Files:**
- Create: `monitoring/DayProfileReader.kt`, `monitoring/DayProfileCache.kt`
- Test: `monitoring/DayProfileCacheTest.kt`, `integration/MonitoringReadersIntegrationTest.kt`(이 태스크에서 만들고 Task 4 가 덧붙인다)

**Interfaces:**
- Consumes: `DayProfile`, `MonitoringProperties`, `FidoLogTable.nameFor/exists`
- Produces:

```kotlin
@Component
class DayProfileReader(dataSource: DataSource, private val tables: FidoLogTable, props: MonitoringProperties) {
    /** 테이블이 없으면 null. 조회 실패·타임아웃은 예외(DataAccessException)를 그대로 던진다. */
    fun read(date: LocalDate): DayProfile?
}

@Component
class DayProfileCache(private val reader: DayProfileReader, private val props: MonitoringProperties, private val clock: Clock) {
    /** 테이블이 없으면 DayProfile.empty(date). 조회 실패면 null(보관하지 않는다). */
    fun get(date: LocalDate): DayProfile?
}
```

- SQL: `SELECT COMPANY_IDX, TO_CHAR(CREATEDTIME,'HH24:MI') AS HM, COUNT(*) AS N, MAX(CREATEDTIME) AS LAST_AT FROM <table> WHERE COMPANY_IDX IN (SELECT IDX FROM CCFA_COMPANY) GROUP BY COMPANY_IDX, TO_CHAR(CREATEDTIME,'HH24:MI')`.
- `DayProfileReader` 는 `HourlyAggregator` 처럼 자기 `JdbcTemplate` 을 만든다: `fetchSize = 500`, `queryTimeout = props.queryTimeoutSeconds`.
- 보관 규칙: `clock` 기준 오늘 날짜는 `cacheSeconds` 동안, 지난 날짜는 계속 보관한다. 오늘보다 8일 넘게 지난 항목은 `get` 때 버린다. `get` 은 `@Synchronized` 다(동시 요청에 조회 한 번).
- `Clock` 은 `PasswordExpiryPolicy` 처럼 주 생성자로 받고, 스프링이 쓰는 보조 생성자(`@Autowired`)가 `Clock.system(FidoStatisticsJob.ZONE)` 을 넘긴다.
- 실패는 `log.warn` 한 줄(테이블 이름과 예외 클래스)만 남긴다.

- [ ] **Step 1: 실패하는 테스트**

`DayProfileCacheTest` (reader 는 mock, 시계는 바꿀 수 있는 `Clock` — `Clock.fixed` 를 갈아 끼우는 작은 테스트용 `MutableClock` 을 테스트 파일 안에 둔다):

```kotlin
@Test fun 당일은_보관_시간_안에_다시_읽지_않는다()   // get(today) 두 번 → verify(reader, times(1)).read(today)
@Test fun 당일은_보관_시간이_지나면_다시_읽는다()    // 61초 뒤 → times(2)
@Test fun 지난_날짜는_계속_보관한다()                // 하루 뒤에도 get(yesterday) → times(1)
@Test fun 테이블이_없으면_빈_프로파일이고_보관한다()  // read → null; get(...).total(1) == 0; times(1)
@Test fun 실패는_null이고_보관하지_않는다()           // read throws QueryTimeoutException → get == null; 다시 get → times(2)
@Test fun 날짜가_바뀌면_어제는_지난_날짜로_남는다()   // today 보관 후 시계를 다음 날로 → get(어제) 는 한 번 더 읽고(당일 보관본은 덜 찼다) 그 뒤로는 다시 읽지 않는다
@Test fun 여드레_넘은_항목은_버린다()
```

`MonitoringReadersIntegrationTest` (`StatisticsBatchIntegrationTest` 와 같은 틀: `@SpringBootTest @ActiveProfiles("test")`, `OracleContainerSupport`, `FIDO_LOGS_20210101` 을 빌려 `IDX 9300~9399` 를 쓰고 `@AfterEach` 에서 지운다):

```kotlin
@Test fun 날짜_테이블을_고객사_분별로_센다() {
    log(9301, 1, "2021-01-01 14:05:10"); log(9302, 1, "2021-01-01 14:05:50"); log(9303, 1, "2021-01-01 15:00:00")
    log(9304, 2, "2021-01-01 00:00:30"); log(9305, 999999, "2021-01-01 14:05:00") // CCFA_COMPANY 에 없는 고객사는 세지 않는다
    val p = reader.read(LocalDate.of(2021, 1, 1))!!
    assertThat(p.total(1)).isEqualTo(3); assertThat(p.total(999999)).isEqualTo(0)
    assertThat(p.count(1, at(14, 5), at(14, 6))).isEqualTo(2)
    assertThat(p.lastLog(1)).isEqualTo(LocalDateTime.of(2021, 1, 1, 15, 0, 0))
}
@Test fun 테이블이_없는_날짜는_null() { assertThat(reader.read(LocalDate.of(2019, 1, 1))).isNull() }
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*DayProfileCacheTest' --tests '*MonitoringReadersIntegrationTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat: 날짜 테이블 건수 조회와 60초 보관`

---

### Task 4: 예외 건수·통계 조회

**Files:**
- Create: `monitoring/ExceptionCountReader.kt`, `monitoring/StatBoardReader.kt`
- Test: `integration/MonitoringReadersIntegrationTest.kt`(덧붙임)

**Interfaces — Produces:**

```kotlin
data class ErrorCounts(val recent: Long, val today: Long)

@Component
class ExceptionCountReader(dataSource: DataSource, props: MonitoringProperties) {
    /** 키는 COMPANY_IDX(NULL 은 0 으로 모은다). 실패는 예외를 던진다. now 는 분으로 내림한다. */
    fun read(now: LocalDateTime): Map<Long, ErrorCounts>
}

@Component
class StatBoardReader(private val jdbc: NamedParameterJdbcTemplate) {
    /** 시간 형식(yyyy/MM/dd/HH) GROUPBY 의 최댓값. 없으면 null. */
    fun latestGroupby(): String?
    /** [from, to] 날짜(양끝 포함)의 고객사별 합계. CCFA_COMPANY 에 없는 고객사는 뺀다. */
    fun totals(from: LocalDate, to: LocalDate): Map<Long, WeekTotals>
}
```

- 예외 SQL: `E_LEVEL = 'ERROR'` 이고 `CREATEDTIME >= :start AND CREATEDTIME < :end` 를 문자열로 비교한다. `:start` = `min(오늘 00:00, now-60분)`, `:end` = now+1분, 형식 `yyyy-MM-dd HH:mm:ss`. `GROUP BY NVL(COMPANY_IDX, 0)`. `recent` = `CREATEDTIME >= :recentFrom` 인 건수, `today` = `CREATEDTIME >= :dayStart` 인 건수(둘 다 `SUM(CASE …)`). 타임아웃은 `DayProfileReader` 와 같다.
- `latestGroupby` 의 형식 필터는 `StatisticsQueryService.lastStatDate` 와 같은 `REGEXP_LIKE` 다.
- `totals` 의 범위는 `GROUPBY >= :fromKey AND GROUPBY < :toKey`(`yyyy/MM/dd/'00'`, `to` 다음 날 0시 미포함)이고 `SUM` 은 `NVL(…,0)` 이다.

- [ ] **Step 1: 실패하는 테스트** (`CCFA_EXCEPTIONS` 는 `IDX 9300~9399` 를, `FIDO_STATISTICS` 는 `SERVICE_NAME LIKE 'montest%'` 를 `@AfterEach` 에서 지운다. 아래 `stat(…)` 의 서비스 이름은 읽기 쉽게 줄여 적었고 실제로는 모두 `montest-` 접두를 붙인다 — `day` 같은 옛 GROUPBY 행과 다른 통합 테스트의 2021/01/01 행을 건드리지 않기 위해서다)

```kotlin
@Test fun 예외는_ERROR만_최근과_오늘로_센다() {
    val now = LocalDateTime.of(2021, 1, 2, 14, 30)
    ex(9301, 1, "ERROR", "2021-01-02 13:45:00"); ex(9302, 1, "ERROR", "2021-01-02 09:00:00")
    ex(9303, 1, "WARN", "2021-01-02 14:00:00");  ex(9304, 1, "ERROR", "2021-01-01 23:00:00")
    ex(9305, 0, "ERROR", "2021-01-02 14:29:00"); ex(9306, null, "ERROR", "2021-01-02 14:10:00")
    val m = exceptions.read(now)
    assertThat(m[1L]).isEqualTo(ErrorCounts(recent = 1, today = 2))
    assertThat(m[0L]).isEqualTo(ErrorCounts(recent = 2, today = 2))
}
@Test fun 자정_직후에는_전날_꼬리가_최근에_들어간다() {
    ex(9307, 1, "ERROR", "2021-01-01 23:40:00"); ex(9308, 1, "ERROR", "2021-01-02 00:05:00")
    assertThat(exceptions.read(LocalDateTime.of(2021, 1, 2, 0, 20))[1L]).isEqualTo(ErrorCounts(recent = 2, today = 1))
}
@Test fun 최신_GROUPBY는_시간_형식만_본다() {
    stat(1, "kbstar", "2021/01/05/13", authS = 1); stat(1, "kbstar", "day", authS = 1)
    assertThat(stats.latestGroupby()).isEqualTo("2021/01/05/13")
}
@Test fun 기간_합계는_양끝_날짜를_포함하고_서비스를_합친다() {
    stat(1, "kbstar", "2021/01/03/00", authS = 10, authF = 1); stat(1, "kbpay", "2021/01/04/23", authS = 5, regS = 2, deregS = 1)
    stat(1, "kbstar", "2021/01/05/00", authS = 100)            // 범위 밖
    stat(999999, "x", "2021/01/03/10", authS = 7)              // 없는 고객사
    val m = stats.totals(LocalDate.of(2021, 1, 3), LocalDate.of(2021, 1, 4))
    assertThat(m[1L]).isEqualTo(WeekTotals(15, 1, 2, 0, 1)); assertThat(m).doesNotContainKey(999999L)
}
```

`latestGroupby` 테스트는 공용 DB 에 더 새로운 통계가 있을 수 있으므로, 단언은 "`day` 를 돌려주지 않고 `2021/01/05/13` 이상"으로 쓴다(`isGreaterThanOrEqualTo`, 형식 정규식 일치).

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*MonitoringReadersIntegrationTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat: 관제 화면의 예외 건수·통계 합계 조회`

---

### Task 5: MonitoringService — 화면 모델 조립

**Files:**
- Create: `monitoring/MonitoringBoard.kt`, `monitoring/MonitoringService.kt`
- Test: `monitoring/MonitoringServiceTest.kt`

**Interfaces:**
- Consumes: Task 1~4 의 전부, `CompanyLookup.all(): List<CcfaCompany>`, `TenantContext.require()`
- Produces:

```kotlin
enum class ColumnState { OK, DISABLED, FAILED }

data class LiveRow(val companyIdx: Long, val name: String, val status: LiveStatus,
    val recent: Long?, val baseline: Long?, val todayTotal: Long?, val lastLog: LocalDateTime?,
    val recentErrors: Long?, val todayErrors: Long?)
data class CommonRow(val recentErrors: Long, val todayErrors: Long, val spike: Boolean)
data class WeekRow(val companyIdx: Long, val name: String, val cur: WeekTotals, val prev: WeekTotals?, val dropped: Boolean)
data class WeekTable(val from: LocalDate, val to: LocalDate, val rows: List<WeekRow>)

data class MonitoringBoard(
    val asOf: LocalDateTime, val cacheSeconds: Long,
    val logs: ColumnState, val exceptions: ColumnState,
    val baselineDate: LocalDate?,          // null = 기준일 없음
    val common: CommonRow?,                // 예외를 모르면 null
    val live: List<LiveRow>,
    val staleNotice: String?,              // null = 알림 없음
    val week: WeekTable?,                  // null = 통계 없음
)

@Service
class MonitoringService(/* readers, cache, companies, tenant, props, clock */) {
    /** SUPER 가 아니면 AccessDeniedException. */
    fun board(): MonitoringBoard
}
```

조립 규칙:
- `now` = `clock` 을 분으로 내림. 최근 구간 = `[now-60분, now)`.
- 기준일 = `today-7` 의 `cache.get` 이 로그가 하나라도 있는 프로파일이면 그것, 아니면 `today-1`, 그것도 비었으면 `baselineDate = null`. "비었다"는 테이블 없음과 조회 실패를 모두 포함한다. 기준 구간은 최근 구간을 같은 일수만큼 뒤로 민 것이다.
- 자정을 넘는 구간은 두 날짜 프로파일의 `count` 를 더한다(오늘 쪽·기준일 쪽 모두). 필요한 전날 프로파일이 `null`(실패)이면 그 값은 `null` 이다.
- `logs` = 꺼져 있으면 `DISABLED`(프로파일을 읽지 않는다), 당일 `cache.get` 이 `null` 이면 `FAILED`, 아니면 `OK`. `OK` 가 아니면 모든 줄의 `recent`·`baseline`·`todayTotal`·`lastLog` 가 `null` 이다.
- `exceptions` 도 같다(`DISABLED` / 예외를 잡으면 `FAILED`). `OK` 가 아니면 `recentErrors`·`todayErrors`·`common` 이 `null`.
- 고객사 줄은 `CompanyLookup.all()` 중 `idx != 0` 인 것 전부다. 예외 맵에서 그 목록에 없는 키는 모두 `common` 으로 더한다. `common.spike` = `recentErrors >= exceptionThreshold`.
- `live` 정렬: `status.ordinal`, 그다음 `recent` 내림차순(null 은 뒤), 그다음 이름.
- 집계 멈춤: `latest` = `latestGroupby()` 의 시각. `due` = `now 의 정각 - 24시간 - staleHours`. `latest == null` → `"집계된 통계가 없습니다."`. `latest < due` 이고 `(latest+1시간, due]` 구간의 날짜들(가장 최근 3일까지만 본다) 중 `cache.get(d)?.anyBetween(…)` 이 하나라도 참이면 → `"통계 집계가 yyyy-MM-dd HH시에서 멈췄습니다."`. 로그 열이 `DISABLED` 면 원본을 보지 않고 `latest < due` 만으로 알린다. 그 밖에는 `null`.
- 주간: `latest == null` 이면 `week = null`. `end` = `latest` 날짜의 전날, 최근 = `[end-6, end]`, 직전 = `[end-13, end-7]`. 줄은 최근·직전 어느 쪽이든 합계가 있는 고객사만. `prev` 는 직전 합계가 없으면 `null`. 정렬: `dropped` 먼저, 그다음 `authRatePointChange` 오름차순(null 은 뒤), 그다음 이름.
- 통계 조회 실패는 잡지 않는다(기존 대시보드와 같은 DB 라 화면 전체 오류가 맞다).

- [ ] **Step 1: 실패하는 테스트** (readers·cache·`CompanyLookup` 은 mock, `StatisticsTenantScopeTest` 의 `login` 방식, 시계는 `Clock.fixed`. 고객사 1 "A은행", 2 "B카드", 0 "크로스서트")

```kotlin
@Test fun COMPANY_운영자는_거부한다()                        // login(1L) → AccessDeniedException, reader 호출 없음
@Test fun 고객사를_고르지_않은_SUPER도_열린다()               // login(0L), selected 비움 → board() 성공
@Test fun 끊긴_고객사가_맨_위에_온다()                        // A: recent 0/baseline 412 → DOWN, B: 85/301 → DROP; live[0].name == "A은행"
@Test fun IDX_0_고객사는_줄을_만들지_않는다()
@Test fun 로그가_없는_고객사도_줄이_있다()                    // 프로파일에 고객사 2 없음 → todayTotal 0, lastLog null, status NONE
@Test fun 칠일_전_테이블이_없으면_전일을_기준으로_쓴다()       // baselineDate == today-1
@Test fun 기준일이_전혀_없으면_판정하지_않는다()               // baselineDate null, baseline null, status OK
@Test fun 자정_직후에는_전날_꼬리를_더한다()                  // now 00:20, 어제 23:20~24:00 5건 + 오늘 3건 → recent 8; 기준도 같은 방식
@Test fun 당일_테이블이_없으면_0이고_기준이_있으면_끊김()      // cache.get(today) == empty
@Test fun 로그_조회가_실패해도_예외와_통계는_나온다()          // cache.get(today) == null → logs FAILED, recent null, exceptions OK, week != null
@Test fun 예외_조회가_실패하면_확인_불가이고_로그_판정은_한다() // exceptions FAILED, common null, status 는 로그로 판정
@Test fun 목록에_없는_고객사의_예외는_공통으로_모은다()        // {0: (2,2), 999: (9,9)} → common (11, 11, spike=true)
@Test fun 설정으로_끄면_읽지_않는다()                          // liveLogsEnabled=false → verifyNoInteractions(cache), logs DISABLED
@Test fun 통계가_늦고_그_구간에_로그가_있으면_멈춤을_알린다()
@Test fun 통계가_늦어도_그_구간에_로그가_없으면_알리지_않는다()
@Test fun 통계가_없으면_주간_표가_없고_알린다()                // week null, staleNotice "집계된 통계가 없습니다."
@Test fun 주간_표는_다_찬_마지막_날에서_끝난다()               // latest "2026/11/24/13" → week.to == 2026-11-23, from == 2026-11-17, totals(…) 인자 검증
@Test fun 직전_주가_없으면_prev가_null이고_하락이_아니다()
@Test fun 하락한_고객사가_주간_표_맨_위에_온다()
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*MonitoringServiceTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat: 관제 화면 모델을 조립하는 MonitoringService`

---

### Task 6: 화면 — 컨트롤러, 템플릿, 메뉴, 접근 제어

**Files:**
- Create: `monitoring/MonitoringController.kt`, `src/main/resources/templates/monitoring/index.html`
- Modify: `config/SecurityConfig.kt`(`SUPER_PATHS` 에 `"/monitoring"`, `"/monitoring/**"`), `common/MenuRegistry.kt`(`ALL` 의 "통계" 다음 줄에 `MenuItem(MenuArea.SYSTEM, "대시보드", "관제", "/monitoring", true, "bi-activity")` 와 영역이 SYSTEM 인 이유 주석)
- Test: `monitoring/MonitoringControllerWebTest.kt`

**Interfaces:**
- Consumes: `MonitoringService.board(): MonitoringBoard`
- Produces: `GET /monitoring` → 뷰 `monitoring/index`, 모델 속성 `board`

템플릿은 `dashboard/index.html` 의 틀(`layout/base`, `fa-page-head`, `fa-table-card`, `fa-table`, `.badge`)을 따른다. 고정 문구:

- 머리: `관제`, 설명 `기준 시각 yyyy-MM-dd HH:mm · {cacheSeconds}초마다 갱신된 값입니다.`
- `staleNotice` 가 있으면 맨 위 경고 상자에 그대로.
- 구역 1 제목 `지금 (당일 원본)`. 열: `고객사 / 상태 / 최근 1시간 / 기준일 같은 시간 / 오늘 누계 / 마지막 로그 / 예외 1시간 / 예외 오늘`. 표 아래 `기준일: yyyy-MM-dd (요일)` 또는 `기준일 없음 — 끊김·감소를 판정하지 않습니다.`
- `common` 이 있으면 표 첫 줄 `공통(고객사 없음)`: 로그 열은 `-`, `spike` 면 `예외 급증` 배지.
- 값이 `null` 인 칸: 그 열의 상태가 `FAILED` 면 `확인 불가`, `DISABLED` 면 `사용 안 함`, 둘 다 아니면(기준일 없음, 마지막 로그 없음) `-`.
- 구역 2 제목 `어제까지 (통계)`, 부제 `yyyy-MM-dd ~ yyyy-MM-dd, 직전 7일 대비`. 열: `고객사 / 인증량 / 증감 / 인증 성공률 / 변화 / 등록 성공률 / 순증 등록`. `dropped` 면 변화 칸에 `text-bg-danger` 배지. `week == null` 이면 표 대신 `집계된 통계가 없습니다.`
- 숫자는 천 단위 쉼표, 증감은 부호와 `%`, 변화는 부호와 `%p`, 순증은 부호. `null` 은 `-`.

- [ ] **Step 1: 실패하는 테스트** (`DashboardControllerWebTest` 와 같은 `@WebMvcTest` 틀과 `@Import`·`@MockitoBean` 목록, `MonitoringService` 는 `@MockitoBean`)

```kotlin
@Test fun SUPER는_고객사를_고르지_않아도_열린다()   // superUser(companyIdx 0, isSuper) → 200, 리다이렉트 아님; "A은행", "끊김", "412" 포함
@Test fun COMPANY_운영자는_403()                    // companyUser → status().isForbidden; verifyNoInteractions(service)
@Test fun 로그인하지_않으면_로그인으로_간다()
@Test fun 실패한_열은_확인_불가로_보인다()           // logs FAILED, recent null → "확인 불가" 포함, "끊김" 없음
@Test fun 꺼진_열은_사용_안_함으로_보인다()
@Test fun 공통_줄과_멈춤_알림을_보여_준다()          // common(11, 11, true), staleNotice → "공통(고객사 없음)", 알림 문구 포함
@Test fun 통계가_없으면_안내_문구를_보여_준다()      // week null → "집계된 통계가 없습니다."
@Test fun 하락은_부호와_단위를_붙여_보여_준다()      // cur/prev 는 Task 1 테스트의 값 → "91.2%", "-6.4%p", "+77"
@Test fun 고객사_이름은_이스케이프된다()             // name "<b>x</b>" → 원문 태그가 응답에 없다
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*MonitoringControllerWebTest'`
- [ ] **Step 3: 컨트롤러·템플릿을 만들고 `SUPER_PATHS`·`MenuRegistry` 를 고친다.**
- [ ] **Step 4: 통과 확인** — 위 테스트와 `./gradlew test --tests '*ArchitectureRulesTest' --tests '*MenuRegistry*' --tests '*SecurityConfig*'` → PASS. 메뉴 개수·순서를 고정한 기존 테스트가 깨지면 새 항목을 반영해 고친다(다른 단언은 건드리지 않는다).
- [ ] **Step 5: 커밋** — `feat: SUPER 관제 화면(/monitoring)을 추가한다`

---

### Task 7: 전체 검증과 기록

**Files:**
- Modify: `docker/init/03-dummy.sql`(선택 — 로컬에서 표가 비지 않게 오늘 날짜에 의존하지 않는 통계 행만), `tasks/todo.md`

- [ ] **Step 1: 전체 테스트** — `./gradlew test` → 0 fail, 테스트 수가 1041 보다 늘었다. 결과 숫자를 적어 둔다.
- [ ] **Step 2: 로컬에서 화면을 연다.** 로컬 Oracle 로 앱을 띄우고 SUPER 로 로그인해 `/monitoring` 을 고객사 선택 없이 연다. 확인: 사이드바 "관제" 항목, 두 표, 당일 테이블이 없을 때 500 이 아닌 것, COMPANY 계정으로는 403. 브라우저는 ego-browser 만 쓴다.
- [ ] **Step 3: `tasks/todo.md`** — "남은 일"의 이 작업 항목을 지우고, "운영 배포 전 확인"으로 스펙의 확인용 쿼리 3종(실시간 적재, 당일 테이블 행 수, `CCFA_EXCEPTIONS` 형식·행 수·레벨 값)과 끄는 설정 키 두 개를 남긴다. 새 기준선 숫자를 고친다.
- [ ] **Step 4: 커밋** — `docs: 관제 화면의 검증 결과와 운영 배포 전 확인 항목을 남긴다`
