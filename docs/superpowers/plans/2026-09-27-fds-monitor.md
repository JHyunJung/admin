# FDS 모니터링 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 선택한 날짜의 FIDO 로그 중 같은 기기(SERIALCODE)의 직전·직후 요청이 N초 이내인 행을 목록으로 보여 주는 조회 전용 화면 `/fds-monitor` 를 만든다.

**Architecture:** 날짜별 분할 테이블 `FIDO_LOGS_yyyyMMdd` 를 JDBC 로 읽고, `LAG/LEAD` 창 함수로 직전·직후 시각을 붙여 한 번의 스캔으로 걸러낸다. N 은 고객사의 `CCFA_FDS_POLICY.AND_TERM/OR_TERM` 중 짧은 쪽이며 화면 입력이 그 조회에 한해 우선한다. 테이블 이름 헬퍼는 기존 `FidoLogQueryService` 에서 `FidoLogTable` 로 빼내 두 서비스가 공유한다.

**Tech Stack:** Spring Boot 3, Spring MVC + Thymeleaf, `NamedParameterJdbcTemplate`, Oracle(창 함수·`NUMTODSINTERVAL`·`rownum`), JUnit 5 + Mockito + `@WebMvcTest`.

**Spec:** `docs/superpowers/specs/2026-09-27-fds-monitor-design.md`

## Global Constraints

- 고객사 경계는 `TenantContext.companyIdx()` 만 쓴다. 검색 폼에 `companyIdx` 를 두지 않는다.
- SQL 에 문자열로 이어 붙이는 값은 **테이블 이름 하나**뿐이며, 그 이름은 `LocalDate` 를 `yyyyMMdd` 로 포맷한 결과만 쓴다(`FidoLogTable`). 나머지는 전부 바인드 파라미터.
- 테넌트 조건 `COMPANY_IDX = :companyIdx` 는 창 함수 **안쪽** `WHERE` 에 둔다.
- 정렬 파라미터를 받지 않는다. `ORDER BY CREATEDTIME DESC, IDX DESC` 고정.
- 없는 날짜의 테이블은 오류가 아니라 빈 목록이다.
- 화면 문구는 한국어. 기존 목록 화면과 같은 레이아웃(`layout/base`, `fragments/pagination`).
- `FidoLogQueryService` 의 외부 동작은 바뀌지 않는다(기존 테스트가 그대로 통과해야 한다).
- 커밋 메시지는 한국어, 본문에 "왜"를 적는다. 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- 셸에서 여러 파일을 돌릴 때는 zsh 배열을 쓴다. Thymeleaf 문자열(`${…}`)이 든 파일은 perl/sed 가 아니라 Edit 도구로 고친다(`tasks/lessons.md` 2026-09-27).

## Review Focus

1. **`SERIALCODE` 가 NULL 인 행** — `PARTITION BY SERIALCODE` 는 NULL 을 한 묶음으로 모아 서로 무관한 요청이 "같은 기기의 반복"으로 잡힌다. 안쪽 `WHERE` 에 `SERIALCODE IS NOT NULL` 이 있어야 한다. → Task 3 테스트 `sqlExcludesNullSerialcode`.
2. **`term` 상한** — 하루치 테이블이므로 86400초를 넘는 값은 의미가 없고, 매우 큰 값은 전부를 잡아 화면이 무의미해진다. `@Max(86400)` 로 막고 폼 오류를 보여 준다. → Task 4 테스트 `termAboveOneDayIsFormError`.
3. **같은 시각의 두 요청** — `CREATEDTIME` 이 같으면 간격 0초. 0 ≤ N 이므로 잡혀야 하고, 화면에 `0.000` 으로 보여야 한다(NULL 이나 빈칸이 아니라). → Task 3 테스트 `FdsMonitorRowTest.equalTimestampsGiveZeroGap`, Task 4 테스트 `listUsesPolicyTermAndRendersRows`(`0.000` 렌더).
4. **공백만 있는 서비스명** — `"  "` 를 `LIKE '%  %'` 로 보내면 아무것도 안 잡힌다. `FidoLogQueryService` 와 같이 null 로 바꿔 조건을 건너뛴다. → Task 3 테스트 `blankServicenameSkipsFilter`.
5. **정책 행은 있는데 기간이 공백·0·문자** — Oracle 은 빈 문자열을 NULL 로 저장하고, 운영자가 `"0"` 이나 `"30초"` 를 넣어 둘 수 있다. 전부 "없음"으로 보고 안내를 띄운다. → Task 2 테스트 `unparsableOrNonPositiveTermsAreAbsent`.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java` (신규) | 날짜 → `FIDO_LOGS_yyyyMMdd`, 현재 스키마에 그 테이블이 있는지. SQL 에 이어 붙는 유일한 값의 신뢰 경계 |
| `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java` (수정) | 위 헬퍼를 쓰도록 바꾼다. 동작 불변 |
| `src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java` (신규) | 정책의 반복 주기 해석, 반복 요청 목록·총건수 조회 |
| `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java` (신규) | 목록 행. 직전·직후 간격(초)을 미리 계산해 담는다 |
| `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java` (신규) | `logDate`, `servicename`, `term` |
| `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java` (신규) | `GET /fds-monitor` |
| `src/main/resources/templates/company/fds-monitor/list.html` (신규) | 검색폼, 기준 안내, 목록, 정책 없음 안내 |
| `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java` (수정) | 메뉴 항목 추가, "아직 없다" 주석 정리 |

테스트: `log/service/FidoLogTableTest`, `log/service/FidoLogQueryServiceTest`(이동·정리), `company/service/FdsMonitorQueryServiceTest`, `company/web/FdsMonitorControllerWebTest`, `common/MenuRegistryTest`.

---

### Task 1: `FidoLogTable` — 테이블 이름 헬퍼 분리

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java` (필드·생성자, `tableName`/`tableExists` 제거, 호출 2곳)
- Create: `src/test/java/com/crosscert/fidoadmin/log/service/FidoLogTableTest.java`
- Modify: `src/test/java/com/crosscert/fidoadmin/log/service/FidoLogQueryServiceTest.java` (생성자, `tableName` 테스트 3개 제거)

**Interfaces:**
- Consumes: 없음.
- Produces: `public class FidoLogTable { public String nameFor(LocalDate date); public boolean exists(String table); }` — Spring `@Component`, 생성자 `FidoLogTable(NamedParameterJdbcTemplate jdbc)`. `nameFor` 는 날짜가 null 이거나 연도가 1~9999 밖이면 `ResponseStatusException(400)`.
- `FidoLogQueryService` 생성자는 `(NamedParameterJdbcTemplate jdbc, TenantContext tenant, FidoLogTable tables)` 가 된다.

- [ ] **Step 1: 헬퍼 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/service/FidoLogTableTest.java`:

```java
package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 테이블 이름은 SQL 에 문자열로 들어가는 유일한 값이다. 날짜 포맷 결과만 나가는지 확인한다. */
class FidoLogTableTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    FidoLogTable tables = new FidoLogTable(jdbc);

    @Test void nameIsDatePartitioned() {
        assertThat(tables.nameFor(LocalDate.of(2026, 9, 27))).isEqualTo("FIDO_LOGS_20260927");
        assertThat(tables.nameFor(LocalDate.of(2026, 1, 2))).isEqualTo("FIDO_LOGS_20260102");
    }

    @Test void missingDateIsRejected() {
        assertThatThrownBy(() -> tables.nameFor(null)).isInstanceOf(ResponseStatusException.class);
    }

    /** 연도가 네 자리를 벗어나면 이름 자릿수가 달라진다. 형태를 벗어나는 입력을 여기서 끊는다. */
    @Test void outOfRangeYearIsRejected() {
        assertThatThrownBy(() -> tables.nameFor(LocalDate.of(0, 1, 1))).isInstanceOf(ResponseStatusException.class);
    }

    /** 현재 스키마로 한정한다. 다른 계정의 동명 테이블이 보이면 남의 로그를 읽는다. */
    @Test void existenceCheckIsScopedToCurrentSchema() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(1);

        assertThat(tables.exists("FIDO_LOGS_20260927")).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Integer.class));
        assertThat(sql.getValue()).contains("ALL_TABLES").contains("SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')");
        assertThat(params.getValue().getValue("tableName")).isEqualTo("FIDO_LOGS_20260927");
    }

    @Test void zeroCountMeansAbsent() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(0);
        assertThat(tables.exists("FIDO_LOGS_19990101")).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*FidoLogTableTest' --console=plain 2>&1 | grep -E 'error:|tests completed|BUILD'`
Expected: 컴파일 오류 `cannot find symbol ... FidoLogTable`

- [ ] **Step 3: 헬퍼 작성**

`src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java`:

```java
package com.crosscert.fidoadmin.log.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 날짜별 분할 테이블 {@code FIDO_LOGS_yyyyMMdd} 의 이름과 존재 여부.
 *
 * <p>테이블 이름은 SQL 에 문자열로 이어 붙는 유일한 값이다. 그래서 여기가 신뢰 경계다 —
 * {@link LocalDate} 를 포맷한 결과만 내보내고 사용자 문자열은 절대 싣지 않는다.
 * 포맷 결과는 항상 숫자 8자리라 다른 것이 섞일 수 없지만, 연도가 범위를 벗어나면
 * 자릿수가 달라지므로 그것만 막는다.
 *
 * <p>FIDO 로그 화면과 FDS 모니터링이 같은 테이블을 읽으므로 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class FidoLogTable {

    private final NamedParameterJdbcTemplate jdbc;

    public String nameFor(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 조회 날짜입니다");
        }
        return "FIDO_LOGS_" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * 그 테이블이 있는가. 로그가 없는 날은 테이블 자체가 없어 그냥 조회하면 ORA-00942 가
     * 500 으로 올라온다. 빈 목록으로 보여 주려고 먼저 묻는다.
     *
     * <p>현재 스키마로 한정한다. 다른 계정의 동명 테이블이 보이면 남의 로그를 읽게 된다.
     */
    public boolean exists(String table) {
        String sql = """
            SELECT COUNT(*)
              FROM ALL_TABLES
             WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')
               AND TABLE_NAME = :tableName
            """;
        Integer found = jdbc.queryForObject(sql, new MapSqlParameterSource("tableName", table), Integer.class);
        return found != null && found > 0;
    }
}
```

- [ ] **Step 4: 헬퍼 테스트 통과 확인**

Run: `./gradlew test --tests '*FidoLogTableTest' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL` (5 tests)

- [ ] **Step 5: `FidoLogQueryService` 가 헬퍼를 쓰게 바꾼다**

`src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java` 에서:

(a) 필드에 헬퍼를 더한다. `@RequiredArgsConstructor` 라 생성자 인자 순서는 필드 순서다.

```java
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final FidoLogTable tables;
```

(b) `String tableName(LocalDate date) { ... }` 메서드와 `private boolean tableExists(String table) { ... }` 메서드를 **통째로 지운다**. 클래스 javadoc 의 `{@link #tableName(LocalDate)}` 문장은 다음으로 바꾼다:

```java
 * 이름을 SQL 에 이어 붙이는 유일한 자리라, {@link FidoLogTable} 이 날짜를 포맷해 만든
 * 이름만 쓰고 사용자 문자열은 절대 싣지 않는다.
```

(c) `search()` 와 `get()` 의 호출을 바꾼다 (두 곳 모두):

```java
        String table = tables.nameFor(form.getLogDate());   // search()
        if (!tables.exists(table)) {
```
```java
        String table = tables.nameFor(date);                // get()
        if (!tables.exists(table)) {
```

(d) 더 이상 쓰지 않는 import 를 지운다: `java.time.format.DateTimeFormatter`. (`HttpStatus`, `ResponseStatusException` 은 `get()` 의 404 에서 계속 쓴다.)

- [ ] **Step 6: 기존 서비스 테스트를 맞춘다**

`src/test/java/com/crosscert/fidoadmin/log/service/FidoLogQueryServiceTest.java`:

(a) 생성자:
```java
    FidoLogQueryService service = new FidoLogQueryService(jdbc, new TenantContext(new SelectedTenant()), new FidoLogTable(jdbc));
```
(b) `tableNameIsDatePartitioned`, `missingDateIsRejected`, `outOfRangeYearIsRejected` 세 테스트를 **지운다** (Task 1 Step 1 의 `FidoLogTableTest` 로 옮겼다). 나머지 테스트는 `jdbc` mock 을 공유하므로 `tableExists(boolean)` 헬퍼가 그대로 동작한다.

- [ ] **Step 7: 로그 관련 테스트 전체 통과 확인**

Run: `./gradlew test --tests '*FidoLog*' --console=plain 2>&1 | grep -E 'error:|tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL`. `FidoLogQueryServiceTest` 7건, `FidoLogTableTest` 5건, `FidoLogControllerWebTest` 3건.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java \
        src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java \
        src/test/java/com/crosscert/fidoadmin/log/service/FidoLogTableTest.java \
        src/test/java/com/crosscert/fidoadmin/log/service/FidoLogQueryServiceTest.java
git commit -q -F - <<'EOF'
refactor: 날짜별 로그 테이블 이름 헬퍼를 FidoLogTable 로 뺀다

FDS 모니터링이 FIDO 로그와 같은 FIDO_LOGS_yyyyMMdd 를 읽는다. 테이블 이름은 SQL 에
문자열로 들어가는 유일한 값이라 신뢰 경계가 한 곳에 있어야 한다. FidoLogQueryService
의 동작은 바뀌지 않는다.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 2: `FdsMonitorQueryService.policyTerm()` — 반복 주기 해석

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java`
- Create: `src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java`

**Interfaces:**
- Consumes: `FidoLogTable`(Task 1), `CcfaFdsPolicyRepository.findById(Long)`, `TenantContext.companyIdx()`, 엔티티 `CcfaFdsPolicy.getAndTerm()/getOrTerm()` (String).
- Produces: `public Optional<Integer> policyTerm()`, `static Optional<Integer> parseTerm(String)`, `static Optional<Integer> shorter(Optional<Integer>, Optional<Integer>)`. `search(...)` 는 Task 3 에서 더한다.

- [ ] **Step 1: 정책 해석 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java`:

```java
package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FdsMonitorQueryServiceTest {

    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    CcfaFdsPolicyRepository policies = mock(CcfaFdsPolicyRepository.class);
    FidoLogTable tables = mock(FidoLogTable.class);
    FdsMonitorQueryService service =
        new FdsMonitorQueryService(jdbc, new TenantContext(new SelectedTenant()), policies, tables);

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void policy(String andTerm, String orTerm) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(1L); p.setAndTerm(andTerm); p.setOrTerm(orTerm);
        when(policies.findById(1L)).thenReturn(Optional.of(p));
    }

    // ---- 정책 해석 ----

    @Test void andTermAloneIsUsed() {
        policy("30", null);
        assertThat(service.policyTerm()).contains(30);
    }

    @Test void orTermAloneIsUsed() {
        policy(null, "45");
        assertThat(service.policyTerm()).contains(45);
    }

    /** 둘 다 있으면 짧은 쪽 — 더 민감한 기준이 결과를 더 많이 잡는다. */
    @Test void bothTermsPickTheShorter() {
        policy("60", "20");
        assertThat(service.policyTerm()).contains(20);
    }

    @Test void noPolicyRowMeansAbsent() {
        when(policies.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.policyTerm()).isEmpty();
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장하고, 운영자가 "0" 이나 "30초" 를 넣어 둘 수 있다. 전부 없음이다. */
    @Test void unparsableOrNonPositiveTermsAreAbsent() {
        assertThat(FdsMonitorQueryService.parseTerm(null)).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("   ")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("0")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("-5")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm("30초")).isEmpty();
        assertThat(FdsMonitorQueryService.parseTerm(" 30 ")).contains(30);
    }

    @Test void policyTermReadsTheEffectiveTenant() {
        policy("10", null);
        service.policyTerm();
        org.mockito.Mockito.verify(policies).findById(1L);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*FdsMonitorQueryServiceTest' --console=plain 2>&1 | grep -E 'error:|BUILD' | head -3`
Expected: 컴파일 오류 `cannot find symbol ... FdsMonitorQueryService`

- [ ] **Step 3: 서비스 골격과 정책 해석 작성**

`src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java`:

```java
package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FDS 모니터링 — 같은 기기(SERIALCODE)의 직전·직후 요청이 N초 이내인 로그를 찾는다.
 *
 * <p>이전 어드민의 {@code fds.FDSMonitor} 를 현재 스키마로 다시 정의한 것이다. 그쪽이 보던
 * IP 대역·기기 유형·국가는 현재 FIDO_LOGS 에 컬럼이 없어 뺐고, 반복 주기만 남겼다.
 * 주체도 IP+사용자(CCFA_FIDO_TLOG, 지금은 없음)에서 SERIALCODE 로 바꿨다.
 * 설계: docs/superpowers/specs/2026-09-27-fds-monitor-design.md
 *
 * <p>{@code CrudService} 를 상속하지 않는다. 날짜별 분할 테이블이라 JPA 엔티티가 없다.
 * 테넌트 경계는 같은 규칙으로 지킨다 — 모든 조회가 {@code COMPANY_IDX = 유효 테넌트} 를 건다.
 */
@Service
@RequiredArgsConstructor
public class FdsMonitorQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final CcfaFdsPolicyRepository policies;
    private final FidoLogTable tables;

    /**
     * 현재 고객사 FDS 정책의 반복 주기(초). 없으면 비어 있다.
     *
     * <p>AND_TERM 과 OR_TERM 중 파싱되는 값을 쓰고, 둘 다 있으면 <b>짧은 쪽</b>이다.
     * 이전 어드민이 두 그룹을 나눈 이유는 IP·국가 조건과 조합하기 위해서였는데, 그 조건들이
     * 없는 지금은 두 그룹이 같은 조건 하나로 줄어 구분이 의미를 잃는다. 하나로 합친다.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> policyTerm() {
        Long companyIdx = tenant.companyIdx();
        return policies.findById(companyIdx)
            .flatMap(p -> shorter(parseTerm(p.getAndTerm()), parseTerm(p.getOrTerm())));
    }

    /** 양의 정수(초)만 값으로 본다. Oracle 은 빈 문자열을 NULL 로 저장하고 운영자가 "30초" 를 넣어 둘 수 있다. */
    static Optional<Integer> parseTerm(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<Integer> shorter(Optional<Integer> a, Optional<Integer> b) {
        if (a.isPresent() && b.isPresent()) return Optional.of(Math.min(a.get(), b.get()));
        return a.isPresent() ? a : b;
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests '*FdsMonitorQueryServiceTest' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL` (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java \
        src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java
git commit -q -F - <<'EOF'
feat: FDS 정책의 반복 주기를 읽는다

AND_TERM/OR_TERM 중 파싱되는 값을 쓰고 둘 다 있으면 짧은 쪽이다. 이전 어드민이 두
그룹을 나눈 이유(IP·국가와 조합)는 그 컬럼이 없는 지금 의미가 없다.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 3: `FdsMonitorRow` 와 `FdsMonitorQueryService.search()` — 반복 요청 조회

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java` (`search` 추가)
- Modify: `src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java` (조회 테스트 추가)
- Create: `src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorRowTest.java`

**Interfaces:**
- Consumes: Task 1 `FidoLogTable.nameFor/exists`, Task 2 서비스 골격.
- Produces:
  - `public record FdsMonitorRow(Long idx, String servicename, String serialcode, LocalDateTime createdtime, Double gapBeforeSec, Double gapAfterSec, long repeats)` + `public static FdsMonitorRow of(Long idx, String servicename, String serialcode, LocalDateTime createdtime, LocalDateTime prevTime, LocalDateTime nextTime, long repeats)`. 간격은 직전/직후가 없으면 null.
  - `public Page<FdsMonitorRow> search(LocalDate date, String servicename, int term, Pageable pageable)`.

- [ ] **Step 1: 행 DTO 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorRowTest.java`:

```java
package com.crosscert.fidoadmin.company.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FdsMonitorRowTest {

    LocalDateTime t = LocalDateTime.of(2026, 9, 27, 10, 0, 0);

    /** 간격은 소수점을 유지한다. SQL 에서 초로 바꾸면 밀리초가 날아간다. */
    @Test void gapsKeepFractionalSeconds() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, t.minusNanos(1_500_000_000L), t.plusSeconds(3), 4);
        assertThat(r.gapBeforeSec()).isEqualTo(1.5);
        assertThat(r.gapAfterSec()).isEqualTo(3.0);
        assertThat(r.repeats()).isEqualTo(4);
    }

    /** 같은 시각의 두 요청은 간격 0 — 빈칸이나 null 이 아니라 0 으로 보여야 한다. */
    @Test void equalTimestampsGiveZeroGap() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, t, null, 2);
        assertThat(r.gapBeforeSec()).isEqualTo(0.0);
        assertThat(r.gapAfterSec()).isNull();
    }

    @Test void missingNeighboursGiveNull() {
        FdsMonitorRow r = FdsMonitorRow.of(1L, "kbstar", "SN-1", t, null, null, 1);
        assertThat(r.gapBeforeSec()).isNull();
        assertThat(r.gapAfterSec()).isNull();
    }
}
```

- [ ] **Step 2: 행 DTO 작성**

`src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java`:

```java
package com.crosscert.fidoadmin.company.web;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * FDS 모니터링 목록 행. 직전·직후 요청과의 간격(초)을 미리 계산해 둔다.
 *
 * <p>간격을 레코드 컴포넌트로 두는 이유: Thymeleaf 가 {@code r.gapBeforeSec} 로 바로 읽는다.
 * 계산 메서드로 두면 접근자 해석에 기대야 한다. 값은 소수점을 유지한다 — SQL 에서
 * 초로 바꾸면 밀리초가 날아가는데, 반복 탐지에서는 0.3초와 3초가 다르다.
 */
public record FdsMonitorRow(Long idx, String servicename, String serialcode, LocalDateTime createdtime,
                            Double gapBeforeSec, Double gapAfterSec, long repeats) {

    public static FdsMonitorRow of(Long idx, String servicename, String serialcode, LocalDateTime createdtime,
                                   LocalDateTime prevTime, LocalDateTime nextTime, long repeats) {
        return new FdsMonitorRow(idx, servicename, serialcode, createdtime,
            seconds(prevTime, createdtime), seconds(createdtime, nextTime), repeats);
    }

    private static Double seconds(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        return Duration.between(from, to).toNanos() / 1_000_000_000.0;
    }
}
```

- [ ] **Step 3: 행 테스트 통과 확인**

Run: `./gradlew test --tests '*FdsMonitorRowTest' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL` (3 tests)

- [ ] **Step 4: 조회 테스트 추가**

`FdsMonitorQueryServiceTest` 에 import 를 더한다:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
```

그리고 클래스 끝(마지막 `}` 앞)에 테스트를 더한다:

```java
    // ---- 조회 ----

    private void table(boolean exists) {
        when(tables.nameFor(any(LocalDate.class))).thenReturn("FIDO_LOGS_20260927");
        when(tables.exists("FIDO_LOGS_20260927")).thenReturn(exists);
    }

    private void rowsExist(long total) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(total);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    }

    /** 그날 로그가 없으면 테이블 자체가 없다. 오류가 아니라 빈 목록이다. */
    @Test void missingTableYieldsEmptyPage() {
        table(false);

        var page = service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /**
     * 창 함수로 직전·직후 시각을 붙이고, 테넌트 조건은 창 함수 <b>안쪽</b>에 있어야 한다.
     * 바깥에 두면 다른 고객사의 요청이 PREV/NEXT 로 섞여 들어온다.
     */
    @Test void sqlUsesWindowFunctionsWithTenantFilterInside() {
        table(true);
        rowsExist(3);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String s = sql.getValue();
        assertThat(s).contains("FIDO_LOGS_20260927")
            .contains("LAG(CREATEDTIME)").contains("LEAD(CREATEDTIME)")
            .contains("PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX")
            .contains("NUMTODSINTERVAL(:term, 'SECOND')");
        // 테넌트 조건이 LAG 보다 뒤(안쪽 WHERE)에, 창 함수 결과를 거르는 바깥 WHERE 보다 앞에 있다.
        int tenantAt = s.indexOf("COMPANY_IDX = :companyIdx");
        assertThat(tenantAt).isGreaterThan(s.indexOf("LAG(CREATEDTIME)"));
        assertThat(tenantAt).isLessThan(s.indexOf("PREV_TIME IS NOT NULL"));
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(1L);
        assertThat(params.getValue().getValue("term")).isEqualTo(30);
        assertThat(s).doesNotContain("SECOND') OR 30").doesNotContain("<= 30");
    }

    /** SERIALCODE 가 NULL 인 행끼리 한 묶음이 되어 서로 무관한 요청이 반복으로 잡히는 것을 막는다. */
    @Test void sqlExcludesNullSerialcode() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("SERIALCODE IS NOT NULL");
    }

    @Test void blankServicenameSkipsFilter() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), "   ", 30, PageRequest.of(0, 20));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("servicename")).isNull();
    }

    @Test void servicenameBecomesLikePattern() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), "kb", 30, PageRequest.of(0, 20));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
        assertThat(params.getValue().getValue("servicename")).isEqualTo("%kb%");
    }

    @Test void zeroCountSkipsRowQuery() {
        table(true);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);

        var page = service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /** 정렬은 고정이다 — Pageable 의 Sort 를 무시하므로 사용자 입력이 ORDER BY 로 갈 길이 없다. */
    @Test void orderingIsFixed() {
        table(true);
        rowsExist(1);

        service.search(LocalDate.of(2026, 9, 27), null, 30, PageRequest.of(0, 20, Sort.by("servicename")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("ORDER BY CREATEDTIME DESC, IDX DESC").doesNotContain("servicename");
    }

    @Test void nonPositiveTermIsRejected() {
        assertThatThrownBy(() -> service.search(LocalDate.of(2026, 9, 27), null, 0, PageRequest.of(0, 20)))
            .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 5: 실패 확인**

Run: `./gradlew test --tests '*FdsMonitorQueryServiceTest' --console=plain 2>&1 | grep -E 'error:|BUILD' | head -3`
Expected: 컴파일 오류 `cannot find symbol ... method search`

- [ ] **Step 6: `search()` 작성**

`FdsMonitorQueryService` 에 import 를 더한다:

```java
import com.crosscert.fidoadmin.company.web.FdsMonitorRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
```

그리고 `shorter(...)` 아래에 더한다:

```java
    /**
     * 그 날짜에서, 같은 SERIALCODE 의 직전 또는 직후 요청이 {@code term} 초 이내인 행.
     *
     * <p>자기 조인이나 상관 서브쿼리를 쓰지 않는다. 이전 어드민은 행마다 ±N초 안의 건수를
     * 세었는데 그것은 행 수 제곱에 비례한다. 창 함수는 한 번의 스캔이다.
     *
     * <p>{@code term} 은 바인드 파라미터다. 테이블 이름 외에는 SQL 에 문자열로 들어가는 값이 없다.
     */
    @Transactional(readOnly = true)
    public Page<FdsMonitorRow> search(LocalDate date, String servicename, int term, Pageable pageable) {
        if (term <= 0) throw new IllegalArgumentException("반복 주기는 1초 이상이어야 합니다");
        Long companyIdx = tenant.companyIdx();
        String table = tables.nameFor(date);
        if (!tables.exists(table)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        var params = new MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("servicename", like(servicename))
            .addValue("term", term);

        // 테넌트·서비스명 조건은 창 함수 안쪽에 둔다. 바깥에 두면 다른 고객사(또는 다른 서비스)의
        // 요청이 PREV_TIME/NEXT_TIME 에 섞여 "같은 기기의 반복"이 아닌 것이 잡힌다.
        // SERIALCODE IS NOT NULL — NULL 끼리 한 묶음이 되어 서로 무관한 요청이 반복으로 잡히는 것을 막는다.
        String flagged = """
            SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME, PREV_TIME, NEXT_TIME, REPEATS
              FROM (
                SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME,
                       LAG(CREATEDTIME)  OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS PREV_TIME,
                       LEAD(CREATEDTIME) OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS NEXT_TIME,
                       COUNT(*)          OVER (PARTITION BY SERIALCODE)                             AS REPEATS
                  FROM %s
                 WHERE COMPANY_IDX = :companyIdx
                   AND SERIALCODE IS NOT NULL
                   AND (:servicename IS NULL OR SERVICENAME LIKE :servicename)
              )
             WHERE (PREV_TIME IS NOT NULL AND CREATEDTIME - PREV_TIME <= NUMTODSINTERVAL(:term, 'SECOND'))
                OR (NEXT_TIME IS NOT NULL AND NEXT_TIME - CREATEDTIME <= NUMTODSINTERVAL(:term, 'SECOND'))
            """.formatted(table);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + flagged + ")", params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Oracle 페이징. rownum 은 정렬 전에 매겨지므로 정렬을 끝낸 결과를 한 번 감싼다.
        params.addValue("offset", pageable.getOffset())
              .addValue("limit", pageable.getPageSize());
        String sql = "SELECT * FROM ("
            + "  SELECT b.*, rownum AS RN FROM ("
            + flagged
            + "     ORDER BY CREATEDTIME DESC, IDX DESC"
            + "  ) b WHERE rownum <= (:offset + :limit)"
            + ") WHERE RN > :offset";

        List<FdsMonitorRow> rows = jdbc.query(sql, params, (rs, i) -> FdsMonitorRow.of(
            rs.getLong("IDX"),
            rs.getString("SERVICENAME"),
            rs.getString("SERIALCODE"),
            toLocal(rs.getTimestamp("CREATEDTIME")),
            toLocal(rs.getTimestamp("PREV_TIME")),
            toLocal(rs.getTimestamp("NEXT_TIME")),
            rs.getLong("REPEATS")));

        return new PageImpl<>(rows, pageable, total);
    }

    private static java.time.LocalDateTime toLocal(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** 부분 일치 검색어. 빈 값은 null 로 바꿔 조건을 건너뛰게 한다. */
    private static String like(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim() + "%";
    }
```

주의: `%s` 로 들어가는 것은 `tables.nameFor()` 의 결과뿐이다. 다른 값을 `formatted` 에 넣지 않는다.

- [ ] **Step 7: 통과 확인**

Run: `./gradlew test --tests '*FdsMonitor*' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL`. `FdsMonitorQueryServiceTest` 14건, `FdsMonitorRowTest` 3건.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java \
        src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java \
        src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java \
        src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorRowTest.java
git commit -q -F - <<'EOF'
feat: 같은 기기의 N초 이내 반복 요청을 창 함수로 찾는다

LAG/LEAD 로 직전·직후 시각을 한 번의 스캔에 붙인다. 이전 어드민의 행마다 ±N초
count 는 행 수 제곱에 비례했다. 테넌트 조건은 창 함수 안쪽에 두어 다른 고객사의
요청이 직전/직후로 섞이지 않게 하고, SERIALCODE 가 NULL 인 행은 뺀다.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 4: 검색폼·컨트롤러·템플릿

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java`
- Create: `src/main/resources/templates/company/fds-monitor/list.html`
- Create: `src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorControllerWebTest.java`

**Interfaces:**
- Consumes: Task 2 `policyTerm()`, Task 3 `search(LocalDate, String, int, Pageable)`, `FdsMonitorRow`. 기존 `SearchForm.toPageable(Sort)`, `toQueryString()`, `fragments/pagination :: pagination(page, baseUrl)`(모델의 `searchQs` 를 읽는다).
- Produces: `GET /fds-monitor` 화면. 모델 속성 `search`, `page`, `policyTerm`(Integer|null), `effectiveTerm`(Integer|null), `basePath`, `searchQs`.

- [ ] **Step 1: 웹 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorControllerWebTest.java`:

```java
package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.FdsMonitorQueryService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FdsMonitorController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FdsMonitorControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FdsMonitorQueryService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private FdsMonitorRow row(long idx) {
        LocalDateTime t = LocalDateTime.of(2026, 9, 27, 10, 0, 0);
        return FdsMonitorRow.of(idx, "kbstar", "SN-0001", t, t.minusSeconds(2), t, 3);
    }

    /** 정책값으로 조회하고, 시리얼·간격·정책 기준이 보인다. IDX 는 FIDO 로그 상세로 간다. */
    @Test void listUsesPolicyTermAndRendersRows() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(eq(LocalDate.of(2026, 9, 27)), any(), eq(30), any()))
            .thenReturn(new PageImpl<>(List.of(row(5L))));

        mvc.perform(get("/fds-monitor").param("logDate", "2026-09-27").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-monitor/list"))
            .andExpect(content().string(containsString("SN-0001")))
            .andExpect(content().string(containsString("2.000")))
            .andExpect(content().string(containsString("0.000")))
            .andExpect(content().string(containsString("정책 반복 주기: 30초")))
            .andExpect(content().string(containsString("/logs/fido/2026-09-27/5")));
    }

    /** 화면 입력이 정책값보다 우선한다. 그 조회에 한해서다. */
    @Test void screenTermOverridesPolicy() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(any(), any(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/fds-monitor").param("logDate", "2026-09-27").param("term", "5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("이 조회는 5초 기준")));

        verify(service).search(eq(LocalDate.of(2026, 9, 27)), any(), eq(5), any());
    }

    /** 정책에도 입력에도 기간이 없으면 조회하지 않고 안내와 정책 화면 링크를 보여 준다. */
    @Test void noTermShowsGuidanceInsteadOfList() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.empty());

        mvc.perform(get("/fds-monitor").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("FDS 정책에 반복 주기를 설정하세요")))
            .andExpect(content().string(containsString("/fds-policies")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    @Test void nonNumericTermIsFormErrorNotServerError() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));

        mvc.perform(get("/fds-monitor").param("term", "abc").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("is-invalid")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    /** 하루치 테이블이라 86400초를 넘는 기준은 의미가 없다. */
    @Test void termAboveOneDayIsFormError() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));

        mvc.perform(get("/fds-monitor").param("term", "86401").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("is-invalid")));

        verify(service, never()).search(any(), any(), anyInt(), any());
    }

    /** 검색폼에 고객사 선택이 없다. 테넌트는 세션이 정한다. */
    @Test void noCompanySelectorInForm() throws Exception {
        when(service.policyTerm()).thenReturn(Optional.of(30));
        when(service.search(any(), any(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/fds-monitor").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*FdsMonitorControllerWebTest' --console=plain 2>&1 | grep -E 'error:|BUILD' | head -3`
Expected: 컴파일 오류 `cannot find symbol ... FdsMonitorController`

- [ ] **Step 3: 검색폼 작성**

`src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java`:

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * FDS 모니터링 검색 조건.
 *
 * <p>날짜 하나를 고른다 — FIDO_LOGS 는 날짜별로 테이블이 갈려 어느 테이블을 읽을지가 곧
 * 검색 조건이다({@code FidoLogSearchForm} 과 같은 이유). {@code term} 이 비어 있으면
 * 정책값을 쓴다. 하루치 테이블이므로 86400초를 넘는 기준은 의미가 없다.
 */
@Getter @Setter
public class FdsMonitorSearchForm extends SearchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate logDate = LocalDate.now();

    private String servicename;

    @Min(value = 1, message = "반복 주기는 1초 이상이어야 합니다.")
    @Max(value = 86400, message = "반복 주기는 하루(86400초)를 넘을 수 없습니다.")
    private Integer term;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logDate", logDate);
        m.put("servicename", servicename);
        m.put("term", term);
        return m;
    }
}
```

- [ ] **Step 4: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java`:

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.service.FdsMonitorQueryService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * FDS 모니터링 — 같은 기기의 N초 이내 반복 요청 목록. 조회 전용이다.
 *
 * <p>{@code ReadOnlyController} 를 상속하지 않는다. 그 기반은 {@code CrudService}(고정 테이블
 * JPA)를 전제하는데 이 화면은 날짜별 분할 테이블을 JDBC 로 읽는다({@code FidoLogController} 와 같다).
 */
@Controller
@RequestMapping("/fds-monitor")
@RequiredArgsConstructor
public class FdsMonitorController {

    private final FdsMonitorQueryService service;

    /**
     * 정렬은 서비스가 고정한다({@code Sort.unsorted()} 를 넘겨 정렬 파라미터를 무시한다).
     *
     * <p>{@code BindingResult} 를 폼 바로 뒤에 두어야 한다. 없으면 {@code term=abc} 같은
     * 타입 불일치가 400 으로 끝나 운영자가 무엇을 잘못 넣었는지 볼 수 없다.
     */
    @GetMapping
    public String list(@Valid @ModelAttribute("search") FdsMonitorSearchForm search, BindingResult binding, Model model) {
        Optional<Integer> policyTerm = service.policyTerm();
        Integer effective = search.getTerm() != null ? search.getTerm() : policyTerm.orElse(null);

        model.addAttribute("policyTerm", policyTerm.orElse(null));
        model.addAttribute("effectiveTerm", effective);
        model.addAttribute("basePath", "/fds-monitor");
        model.addAttribute("searchQs", search.toQueryString());

        // 폼 오류(term 이 숫자가 아니거나 범위 밖)나 기준 없음이면 조회하지 않는다.
        // 기준 없이 조회하면 "오늘은 이상 없음"처럼 보여 정책이 빠진 것을 가린다.
        if (binding.hasErrors() || effective == null) {
            model.addAttribute("page", Page.empty());
            return "company/fds-monitor/list";
        }

        model.addAttribute("page", service.search(search.getLogDate(), search.getServicename(), effective,
            search.toPageable(Sort.unsorted())));
        return "company/fds-monitor/list";
    }
}
```

- [ ] **Step 5: 템플릿 작성**

`src/main/resources/templates/company/fds-monitor/list.html`:

```html
<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>이상 징후 모니터링</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">이상 징후 모니터링</h1>
    <a th:href="@{/fds-policies}" class="btn btn-link btn-sm">FDS 정책</a>
  </div>

  <!-- 무엇을 기준으로 보고 있는지. 화면 입력으로 조회했으면 정책값과 다를 수 있다. -->
  <div class="small text-secondary mb-2">
    <span th:if="${policyTerm != null}" th:text="|정책 반복 주기: ${policyTerm}초|"></span>
    <span th:if="${policyTerm == null}">정책에 반복 주기가 없습니다.</span>
    <span th:if="${effectiveTerm != null and effectiveTerm != policyTerm}"
          class="ms-2 badge text-bg-warning" th:text="|이 조회는 ${effectiveTerm}초 기준|"></span>
  </div>

  <form method="get" th:action="@{/fds-monitor}" th:object="${search}"
        class="row g-2 align-items-end mb-3">
    <!-- 날짜별 분할 테이블이라 기간이 아니라 날짜 하나를 고른다(FdsMonitorSearchForm 주석 참고). -->
    <div class="col-auto">
      <label class="form-label small mb-0">조회 날짜</label>
      <input type="date" name="logDate" th:value="${search.logDate}" class="form-control form-control-sm" required>
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">서비스명</label>
      <input name="servicename" th:value="${search.servicename}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">반복 주기(초)</label>
      <input type="number" min="1" max="86400" th:field="*{term}"
             th:placeholder="${policyTerm != null} ? |정책값 ${policyTerm}| : '직접 입력'"
             class="form-control form-control-sm"
             th:classappend="${#fields.hasErrors('term')} ? 'is-invalid'">
      <div class="invalid-feedback" th:errors="*{term}"></div>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/fds-monitor}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <!-- 기준이 없으면 목록 대신 안내. 빈 목록을 보여 주면 "오늘은 이상 없음"으로 읽혀 정책이 빠진 것을 가린다. -->
  <div th:if="${effectiveTerm == null}" class="alert alert-warning py-2" role="status">
    FDS 정책에 반복 주기를 설정하세요. <a th:href="@{/fds-policies}" class="alert-link">FDS 정책으로 이동</a>
    — 또는 위 "반복 주기(초)"에 값을 넣어 이번 조회에만 적용할 수 있습니다.
  </div>

  <div th:if="${effectiveTerm != null}" class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover align-middle fa-table mb-0">
      <thead class="table-light">
      <tr>
        <th class="text-nowrap">IDX</th>
        <th class="text-nowrap">일시</th>
        <th>서비스명</th>
        <th class="text-nowrap">시리얼</th>
        <th class="text-nowrap text-end">직전 요청과 간격(초)</th>
        <th class="text-nowrap text-end">직후 요청과 간격(초)</th>
        <th class="text-nowrap text-end">그날 이 기기 요청 수</th>
      </tr>
      </thead>
      <tbody>
      <tr th:each="r : ${page.content}">
        <td><a th:href="@{/logs/fido/{date}/{id}(date=${search.logDate},id=${r.idx})}" th:text="${r.idx}"></a></td>
        <td class="text-nowrap" th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm:ss.SSS')}"></td>
        <td th:text="${r.servicename}"></td>
        <td class="fa-mono text-nowrap" th:text="${r.serialcode}"></td>
        <td class="text-end fa-mono"
            th:text="${r.gapBeforeSec == null} ? '-' : ${#numbers.formatDecimal(r.gapBeforeSec, 1, 3)}"
            th:classappend="${r.gapBeforeSec != null and r.gapBeforeSec <= effectiveTerm} ? 'text-danger fw-semibold'"></td>
        <td class="text-end fa-mono"
            th:text="${r.gapAfterSec == null} ? '-' : ${#numbers.formatDecimal(r.gapAfterSec, 1, 3)}"
            th:classappend="${r.gapAfterSec != null and r.gapAfterSec <= effectiveTerm} ? 'text-danger fw-semibold'"></td>
        <td class="text-end" th:text="${r.repeats}"></td>
      </tr>
      <tr th:if="${page.totalElements == 0}">
        <td colspan="7" class="text-center text-secondary py-4">데이터가 없습니다.</td>
      </tr>
      </tbody>
    </table>
  </div>

  <div th:if="${effectiveTerm != null}" class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|${page.totalElements}건이 검색되었습니다.|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`#numbers.formatDecimal(x, 1, 3)` 은 소수 셋째 자리까지 고정 표기라 `2.0` 이 `2.000`, `0.0` 이 `0.000` 으로 나온다(테스트가 그 문자열을 본다).

- [ ] **Step 6: 통과 확인**

Run: `./gradlew test --tests '*FdsMonitorControllerWebTest' --console=plain 2>&1 | grep -E 'error:|tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL` (6 tests)

만약 `nonNumericTermIsFormErrorNotServerError` 가 `is-invalid` 를 못 찾으면: `th:field="*{term}"` 이 바인딩 오류 시 원래 문자열을 되돌려 주는지 확인한다. `th:object` 가 폼에 있고 `BindingResult` 가 `@ModelAttribute("search")` 바로 뒤 인자여야 한다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java \
        src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java \
        src/main/resources/templates/company/fds-monitor/list.html \
        src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorControllerWebTest.java
git commit -q -F - <<'EOF'
feat: 이상 징후 모니터링 화면 /fds-monitor

같은 기기의 N초 이내 반복 요청을 날짜별로 보여 준다. N 은 정책값이 기본이고 화면
입력은 그 조회에 한해 우선한다. 기준이 없으면 빈 목록 대신 안내를 보여 준다 — 빈
목록은 "오늘은 이상 없음"으로 읽혀 정책이 빠진 것을 가린다.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

### Task 5: 메뉴 등록

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java`
- Modify: `src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java`

**Interfaces:**
- Consumes: Task 4 의 `/fds-monitor`.
- Produces: 사이드바 "이상 징후 탐지 › 모니터링". `areaOf("/fds-monitor…") == TENANT`, `titleFor("/fds-monitor") == "모니터링"`.

- [ ] **Step 1: 메뉴 테스트 고치기·추가**

`src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java`:

(a) `테넌트_영역_화면은_18개다` 를 `테넌트_영역_화면은_19개다` 로 바꾸고, 안의 `assertThat(tenant).isEqualTo(18);` 을 다음으로 바꾼다:
```java
        // 모니터링(/fds-monitor)이 더해져 18 → 19 다.
        assertThat(tenant).isEqualTo(19);
```
(b) `titleFor_resolves_longest_prefix_and_falls_back_to_path` 에 한 줄을 더한다:
```java
        assertThat(registry.titleFor("/fds-monitor")).isEqualTo("모니터링");
```
(c) 새 테스트를 클래스 끝에 더한다. `areaOf` 는 `request.getRequestURI()` 를 받으므로 쿼리스트링 없는 경로만 넣는다.
```java
    /** 모니터링은 고객사 데이터를 보므로 TENANT 다. SYSTEM 이면 미선택 SUPER 가 테넌트 선택 없이 들어온다. */
    @Test void fdsMonitorIsTenantAreaAndNotSuperOnly() {
        assertThat(registry.areaOf("/fds-monitor")).isEqualTo(MenuArea.TENANT);
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href).contains("/fds-monitor");
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*MenuRegistryTest' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD FAILED` — 19 기대에 18, `/fds-monitor` 없음.

- [ ] **Step 3: 메뉴 항목 추가**

`src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java`:

(a) 다음 두 줄을
```java
        // 이전 어드민의 "이상 징후 탐지 → 정책관리". 모니터링 화면은 아직 없다.
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
```
이렇게 바꾼다:
```java
        // 이전 어드민의 "이상 징후 탐지 → 정책관리 / 모니터링". 모니터링은 현재 컬럼으로 성립하는
        // 반복 주기 조건만 본다(docs/superpowers/specs/2026-09-27-fds-monitor-design.md).
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "모니터링", "/fds-monitor", false, "bi-activity"),
```
(b) 클래스 javadoc 의 다음 문단을
```java
     * <p>이전 어드민에 있던 "통계 → 통계관리"와 "이상 징후 탐지 → 모니터링"은 넣지 않았다.
     * 전자는 대시보드가 같은 일을 하고, 후자는 대응하는 화면이 없다. 화면 없는 메뉴는
     * 404 나 빈 페이지로 이어지므로, 화면이 생길 때 함께 추가한다.
```
이렇게 바꾼다:
```java
     * <p>이전 어드민에 있던 "통계 → 통계관리"는 넣지 않았다. 대시보드가 같은 일을 한다.
     * 화면 없는 메뉴는 404 나 빈 페이지로 이어지므로, 화면이 생길 때 함께 추가한다.
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests '*MenuRegistryTest' --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 전체 테스트**

Run: `./gradlew test --console=plain 2>&1 | grep -E 'tests completed|BUILD'`
Expected: 실패는 기존 DB 의존 4건(`AuditLogRoundTripTest`, `LoginLockLoadTest`, `SelectedTenantWiringTest`, `EntityBootTest`)뿐. 다음으로 확인한다:

```bash
python3 -c "
import glob,xml.etree.ElementTree as ET
for f in sorted(glob.glob('build/test-results/test/*.xml')):
    for tc in ET.parse(f).getroot().iter('testcase'):
        for b in list(tc.findall('failure'))+list(tc.findall('error')):
            print(tc.get('classname').split('.')[-1]+'.'+tc.get('name'))
"
grep -ho 'skipped="[0-9]*"' build/test-results/test/*.xml | grep -vc '"0"'   # 0 이어야 한다
```

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
        src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java
git commit -q -F - <<'EOF'
feat: 사이드바에 이상 징후 모니터링을 올린다

TENANT 영역이다. 고객사 데이터를 보므로 SYSTEM 에 두면 미선택 SUPER 가 테넌트
선택 없이 들어온다.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
EOF
```

---

## 마무리 확인

- [ ] `tasks/todo.md` 의 "레거시 매퍼 갭 반영" 절에서 `- [ ] 1. FDS 모니터링 화면` 을 `[x]` 로 바꾸고 결과(테스트 건수)를 한 줄 적는다.
- [ ] `git push origin main` 은 사용자가 요청할 때 한다.
