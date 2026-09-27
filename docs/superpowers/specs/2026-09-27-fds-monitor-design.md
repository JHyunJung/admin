# FDS 모니터링 화면 설계

날짜: 2026-09-27
대상: 기존 FIDO Admin. 이전 어드민의 "이상 징후 탐지 → 모니터링" 을 현재 스키마로 다시 정의한다.
근거: `docs/legacy/mybatis-mappers.md` 의 `fds.FDSMonitor` / `selectFDSMonitor` / `selectFDSMonitorTotalCount`

## 1. 문제

`MenuRegistry` 에 "모니터링 화면은 아직 없다" 로 남아 있는 화면이다. FDS 정책(`/fds-policies`)은
있지만, 그 정책으로 로그를 걸러 보여 주는 곳이 없어 정책이 무엇을 잡는지 운영자가 확인할 수 없다.

이전 어드민의 모니터링은 네 가지를 봤다.

| 조건 | 레거시 컬럼 | 현재 스키마 |
|---|---|---|
| IP 대역 | `FIDO_LOGS_{날짜}.LONGIP` | 없음 |
| 기기 유형(Android/iOS) | `UA` 에 `instr` | 없음 |
| 국가 | `COUNTRY` | 없음 |
| 반복 주기 | `CCFA_FIDO_TLOG` 에서 같은 `ACCESSIP`+`USERNAME` 이 ±N초 안에 재요청 | `CCFA_FIDO_TLOG` 없음 |

현재 `FIDO_LOGS_{날짜}` 의 구조화 컬럼은 `IDX`, `COMPANY_IDX`, `SERIALCODE`, `SERVICENAME`,
`CREATEDTIME` 뿐이다(`JSONDATA` 는 CLOB). ERD 원본(`docs/erd/kbfido-columns.txt`)에도 위 컬럼과
테이블은 없다. 레거시 쿼리를 그대로 옮길 수 없다.

**결정**: 현재 컬럼으로 성립하는 조건만 남긴다. 반복 주기 하나다. 주체는 `SERIALCODE`(기기 시리얼)로
바꾼다 — IP+사용자 대신 "같은 기기가 짧은 시간에 여러 번 요청했다" 를 본다.
IP·기기·국가는 데이터가 생기기 전까지 보지 않는다. FDS 정책 화면의 해당 입력 필드는 그대로 둔다
(지우면 저장된 값이 화면에서 사라져 정책이 바뀐 것처럼 보인다).

**목표**: 선택한 날짜의 로그 중, 같은 기기의 직전 또는 직후 요청이 N초 이내인 행을 목록으로 보여 준다.
조회 전용이다. 조치는 기존 화면(사용자 상태 변경 등)에서 한다.

## 2. 반복 주기 N 의 출처

`CCFA_FDS_POLICY`(PK = `COMPANY_IDX`)의 `AND_TERM` 과 `OR_TERM`.

- 각 값을 양의 정수(초)로 파싱한다. 파싱되지 않거나 0 이하면 없는 것으로 본다.
- 둘 다 있으면 **짧은 쪽**을 쓴다. 더 민감한 기준이 결과를 더 많이 잡으므로, 두 값을 둔
  운영자의 의도에 가깝다.
- 둘 다 없으면 정책에 반복 주기가 없는 것이다.

레거시가 AND 그룹과 OR 그룹을 나눈 이유는 IP·국가 조건과 결합하기 위해서였다. 그 조건들이
없는 지금은 두 그룹이 같은 조건 하나로 줄어들어 구분이 의미를 잃는다. 하나로 합친다.

화면에서 N 을 직접 넣으면 **그 조회에 한해** 그 값을 쓴다. 정책은 바뀌지 않는다.
정책에도 없고 입력도 없으면 목록 대신 안내를 보여 준다("FDS 정책에 반복 주기를 설정하세요" + 정책 화면 링크).

## 3. 화면

경로 `/fds-monitor`, 메뉴 "이상 징후 탐지 › 모니터링", 영역 TENANT, SUPER 전용 아님(FDS 정책과 같다).

검색 조건

| 이름 | 파라미터 | 기본값 | 비고 |
|---|---|---|---|
| 조회 날짜 | `logDate` | 오늘 | 날짜별 분할 테이블이라 하루 단위. `FidoLogSearchForm` 과 같은 이유 |
| 서비스명 | `servicename` | 없음 | 부분 일치 |
| 반복 주기(초) | `term` | 정책값 | 비어 있으면 정책값. 있으면 양의 정수여야 한다 |

목록 컬럼: 일시 · 서비스명 · 시리얼 · 직전 요청과 간격(초) · 직후 요청과 간격(초) · 그날 그 기기 요청 수 · IDX.
IDX 는 `/logs/fido/{logDate}/{idx}` 상세로 간다. 정렬은 `CREATEDTIME DESC, IDX DESC` 고정.
페이징은 다른 목록과 같은 `fragments/pagination`.

화면 상단에 "정책 반복 주기: N초" 를 보여 준다. 입력값으로 조회했으면 "이 조회는 M초 기준" 을 함께 보여
운영자가 지금 무엇을 보고 있는지 알게 한다.

## 4. 조회

JDBC. 날짜별 테이블이라 JPA 엔티티로 다룰 수 없다(`FidoLogQueryService` 와 같은 이유).

### 4.1 테이블 이름 헬퍼 분리

`FidoLogQueryService` 의 `tableName(LocalDate)` 과 `tableExists(String)` 을 `log.service.FidoLogTable`
(public 컴포넌트)로 옮긴다. 두 서비스가 같은 신뢰 경계를 공유한다 — 테이블 이름은 `LocalDate` 를
`yyyyMMdd` 로 포맷한 결과만 쓰고, 사용자 문자열은 절대 싣지 않는다. 연도 범위 검사도 그대로 옮긴다.
`FidoLogQueryService` 의 동작은 바뀌지 않는다(기존 테스트가 이를 확인한다).

### 4.2 SQL

분석 함수로 한 번에 직전·직후 시각을 붙인다. 자기 조인이나 상관 서브쿼리를 쓰지 않는다 —
레거시 방식(행마다 ±N초 count)은 행 수 제곱에 비례한다.

    SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME, PREV_TIME, NEXT_TIME, REPEATS
      FROM (
        SELECT IDX, SERVICENAME, SERIALCODE, CREATEDTIME,
               LAG(CREATEDTIME)  OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS PREV_TIME,
               LEAD(CREATEDTIME) OVER (PARTITION BY SERIALCODE ORDER BY CREATEDTIME, IDX) AS NEXT_TIME,
               COUNT(*)          OVER (PARTITION BY SERIALCODE)                             AS REPEATS
          FROM FIDO_LOGS_20260927
         WHERE COMPANY_IDX = :companyIdx
           AND (:servicename IS NULL OR SERVICENAME LIKE :servicename)
      )
     WHERE (PREV_TIME IS NOT NULL AND CREATEDTIME - PREV_TIME <= NUMTODSINTERVAL(:term, 'SECOND'))
        OR (NEXT_TIME IS NOT NULL AND NEXT_TIME - CREATEDTIME <= NUMTODSINTERVAL(:term, 'SECOND'))
     ORDER BY CREATEDTIME DESC, IDX DESC

- 테넌트 조건은 창 함수 **안쪽** `WHERE` 에 있어야 한다. 바깥에 두면 다른 고객사의 요청이
  `PREV_TIME`/`NEXT_TIME` 에 섞여 들어온다.
- `:term` 은 바인드 파라미터다. 문자열로 조립하지 않는다.
- `REPEATS` 는 그날 그 기기의 전체 요청 수다(플래그된 행 수가 아니다). "이 기기가 오늘 몇 번 왔나" 가
  운영자에게 더 쓸모 있다.
- 총건수는 같은 뼈대에 `SELECT COUNT(*)` 를 씌운다. 페이징은 `FidoLogQueryService` 와 같은 `rownum` 3중 중첩.
- 간격(초)은 Java 에서 `Duration.between` 으로 계산한다. SQL 에서 초로 바꾸면 소수점이 날아간다.

### 4.3 없는 날짜

테이블이 없으면 빈 목록이다. 오류가 아니다(`FidoLogQueryService` 와 같다).

## 5. 경계

- 고객사는 `TenantContext.companyIdx()` 만 쓴다. 폼에 `companyIdx` 는 없다.
- `term` 이 양의 정수가 아니면 폼 오류(400 이 아니라 목록 화면에 오류 표시).
- 정렬 파라미터는 받지 않는다. 사용자 입력이 `ORDER BY` 로 갈 경로가 없다.
- 조회 기록은 `AuditReadInterceptor` 가 MENU_VIEW 로 남긴다. 목록 화면이라 DATA_VIEW 는 없다.

## 6. 파일

새로 만든다.

| 파일 | 역할 |
|---|---|
| `log/service/FidoLogTable` | 날짜 → 테이블 이름, 존재 확인. `FidoLogQueryService` 에서 옮김 |
| `company/service/FdsMonitorQueryService` | 정책 해석(`policyTerm`), 목록·총건수 조회 |
| `company/web/FdsMonitorSearchForm` | `logDate`, `servicename`, `term` |
| `company/web/FdsMonitorRow` | 목록 행(간격 초 계산 포함) |
| `company/web/FdsMonitorController` | `GET /fds-monitor` |
| `templates/company/fds-monitor/list.html` | 목록·안내 |

고친다.

| 파일 | 변경 |
|---|---|
| `log/service/FidoLogQueryService` | `FidoLogTable` 사용. 동작 불변 |
| `common/MenuRegistry` | "이상 징후 탐지 › 모니터링" 항목 추가. "모니터링 화면은 아직 없다" 주석 제거 |
| `common/MenuRegistryTest` | TENANT 18 → 19, `/fds-monitor` 영역·제목 |

## 7. 테스트

`FdsMonitorQueryService`
- 정책 해석: AND 만 / OR 만 / 둘 다(짧은 쪽) / 없음 / 숫자 아님·0 이하 → 없음.
- SQL 에 `LAG`, `LEAD`, `NUMTODSINTERVAL`, 안쪽 `WHERE COMPANY_IDX = :companyIdx` 가 있다.
- `term` 이 바인드 파라미터로 들어간다(SQL 문자열에 숫자가 박히지 않는다).
- 없는 테이블 → 빈 페이지, 목록 쿼리 안 나감.
- 정렬 고정: `Sort.by("servicename")` 을 넘겨도 `ORDER BY CREATEDTIME DESC, IDX DESC`.

`FidoLogTable`
- `FidoLogQueryServiceTest` 의 `tableName` 관련 테스트를 옮긴다. `FidoLogQueryService` 쪽은 헬퍼를
  거쳐도 같은 결과를 내는지만 남긴다.

`FdsMonitorController` (WebMvcTest)
- 목록 렌더, 시리얼과 간격이 보인다.
- 정책·입력 모두 없으면 안내 문구와 `/fds-policies` 링크.
- 입력 `term` 이 정책값보다 우선한다(서비스에 넘어간 값을 캡처).
- `term=abc` → 폼 오류, 목록 조회 안 나감.

`MenuRegistryTest`
- TENANT 19개, `areaOf("/fds-monitor/x") == TENANT`, `titleFor("/fds-monitor") == "모니터링"`.

## 8. 남겨 두는 것

- IP·기기·국가 조건. 컬럼이 생기면 4.2 의 안쪽 `WHERE` 에 붙이면 된다.
- `JSONDATA` 안의 값(응답 코드 등). 키 구조가 확인되면 별도 설계.
- 여러 날짜에 걸친 조회. 분할 테이블을 `UNION ALL` 로 잇는 방식은 레거시 `basic.getList` 에 있으나
  이 화면은 "오늘 무슨 일이 있었나" 가 용도라 하루로 둔다.
