# FIDO Admin 설계 문서

- 작성일: 2026-09-16
- 상태: 승인 대기
- 근거 ERD: KBFIDO 스키마 전체 ERD (Oracle 19c, 39 테이블 / 354 컬럼) — 추출본 `docs/erd/kbfido-columns.txt`

## 1. 목적과 범위

기존 KBFIDO 스키마(Oracle)를 **구조 변경 없이** 그대로 사용하는 신규 관리자 웹(FIDO Admin)을 만든다.
기존 어드민 소스는 참고하지 않으며, 메뉴·화면은 이 문서에서 새로 정의한다.
프로토타입 확인 후 불필요한 화면은 제외한다.

### 1.1 고정 제약

| 제약 | 내용 |
|---|---|
| 스택 | Java 17, Spring Boot 3.5.x, Gradle Wrapper 8.14 (Groovy DSL) |
| DB | Oracle. ERD 이외의 테이블·컬럼을 추가·수정·삭제하지 않는다. `ddl-auto: none` |
| 뷰 | Thymeleaf만 사용. SPA·REST 프런트엔드 없음 |
| 네트워크 | 사내망. CSS/JS/폰트는 외부 호출 없이 jar 내부(WebJars 또는 `static/`)에서 서빙. 본문 폰트는 Pretendard 한글 상용 서브셋(OFL-1.1, 3 무게 약 786KB)을 번들하고, 서브셋 밖 글자는 시스템 폰트 스택이 받는다 |
| 미포함 테이블 | `CCFA_FIDO_TLOG`는 ERD에 없으므로 다루지 않는다 |

### 1.2 화면 대상 테이블

| 구분 | 테이블 |
|---|---|
| CRUD | CCFA_COMPANY, CCFA_MANAGER(+CCFA_MANAGER_PW_POLICY), APPID, APPSERVER, CCFA_FDS_POLICY, CCFA_LICENSE, CCFA_FIDOCLIENT, CCFA_SYSTEM_PROP, CCFA_SYSTEM_INFO, CCFA_ERROR_TABLE, CCFA_CRITERIA, FIDO2_METADATA, FIDO2_CREDENTIAL_PARAMS, FIDO2_DEMO_ACCESS_CODE, CCFA_MENU, CCFA_OPTION, CCFA_OPTIONS, CCFA_FIELDS |
| 조회 + 상태 변경 | USERINFO |
| 조회 전용 | FIDO_LOGS, CCFA_AUDIT_LOG, CCFA_EXCEPTIONS, CCFA_MAILING, SIGN, TRANSACTIONHASH, TRANSACTION_CONFIRMATION, CHALLENGE, CRITERIA, FIDO_STATISTICS |
| 대시보드 | FIDO_STATISTICS 집계 |
| 화면 없음 | FIDO_LOGS_20210101, FIDO_LOGS_20210102, BAK_FIDO_LOGS_BAK, BAK_FIDO_LOGS_TEST, FIDO_STATISTICS_BAK, CCFA_STATISTICS, CCFA_STATISTICS_FILTER, CCFA_STATISTICS_ORDER, AWS_INFO |

화면이 없는 테이블도 엔티티는 작성한다(ERD 1:1 매핑 검증 대상). 리포지토리·화면은 만들지 않는다.

## 2. 아키텍처

### 2.1 기술 스택

| 항목 | 선택 |
|---|---|
| 프레임워크 | Spring Boot 3.5.x, Spring MVC, Thymeleaf, Spring Security 6 |
| 데이터 | Spring Data JPA (Hibernate 6), ojdbc11, HikariCP |
| 정적 자원 | WebJars: `bootstrap` 5.3, `chart.js` 4, `webjars-locator-lite` |
| 검증 환경 | Docker `gvenzl/oracle-free:23-slim` + 로컬 전용 DDL/시드 |
| 테스트 | JUnit 5, Spring Boot Test, Testcontainers (Oracle Free) |

### 2.2 계층

Controller(Thymeleaf 뷰) → Service(트랜잭션, 테넌트 필터, 감사 로그) → Repository(Spring Data JPA + Specification).
엔티티는 뷰에 직접 노출하지 않는다. 폼 DTO(입력)와 목록/상세 DTO(출력)를 거친다.
민감 컬럼 `CCFA_MANAGER.USER_PW`, `USERINFO.PUBKEY`, `USERINFO.CERTIFICATE`, `AWS_INFO.AMZ_TOKEN`은 출력 DTO에서 제외하거나 앞 16자만 표시한다.

### 2.3 패키지

```
com.crosscert.fidoadmin
├── config/      SecurityConfig, JpaConfig, WebMvcConfig
├── common/      CrudController, ReadOnlyController, CrudService, SearchForm,
│                PageResult, TenantContext, AuditLogger, GlobalExceptionHandler
├── auth/        LoginController, ManagerUserDetails(Service), Sha256PasswordEncoder,
│                LoginSuccess/FailureHandler, PasswordChangeController
├── dashboard/   DashboardController, StatisticsQueryService
├── company/     CCFA_COMPANY, CCFA_FDS_POLICY, CCFA_LICENSE
├── manager/     CCFA_MANAGER, CCFA_MANAGER_PW_POLICY
├── fido/        APPID, APPSERVER, USERINFO, CHALLENGE, CRITERIA, SIGN,
│                TRANSACTIONHASH, TRANSACTION_CONFIRMATION
├── fido2/       FIDO2_METADATA, FIDO2_CREDENTIAL_PARAMS, FIDO2_DEMO_ACCESS_CODE
├── log/         FIDO_LOGS(+아카이브/BAK 엔티티), CCFA_AUDIT_LOG, CCFA_EXCEPTIONS, CCFA_MAILING
├── statistics/  FIDO_STATISTICS(+BAK), CCFA_STATISTICS(+FILTER/ORDER) 엔티티
├── aws/         AWS_INFO 엔티티
└── system/      CCFA_SYSTEM_PROP, CCFA_SYSTEM_INFO, CCFA_ERROR_TABLE, CCFA_FIDOCLIENT,
                 CCFA_CRITERIA, CCFA_MENU, CCFA_OPTION, CCFA_OPTIONS, CCFA_FIELDS
```

각 도메인은 `entity`, `repository`, `service`, `web` 하위 패키지를 가진다.
템플릿 경로는 `templates/<도메인>/<테이블>/list.html`, `form.html`, `detail.html`.

### 2.4 JPA 설정 원칙

- `spring.jpa.hibernate.ddl-auto: none`, `open-in-view: false`
- 네이밍 전략: `PhysicalNamingStrategyStandardImpl` (물리명 그대로). 모든 필드에 `@Column(name = "...")` 명시
- `hibernate.jdbc.time_zone: Asia/Seoul`
- 시퀀스: `@SequenceGenerator(allocationSize = 1)`

## 3. 인증·권한·멀티테넌트

### 3.1 로그인 흐름

1. `GET /login` 폼(USER_ID, USER_PW) → Spring Security `formLogin`, `POST /login`.
2. `ManagerUserDetailsService`가 `CCFA_MANAGER`를 `USER_ID`로 조회. `STATUS != '활성'`이면 `DisabledException`.
3. `CCFA_MANAGER_PW_POLICY.ACCOUNT_LOCK = 'Y'`이면 `LockedException`. 행이 없으면 잠기지 않은 것으로 본다.
4. `Sha256PasswordEncoder`: 입력값의 SHA-256 hex(소문자 64자)를 `USER_PW`와 대소문자 무시 비교.
5. 성공: `LOGIN = 'ON-LINE'`, `LAST_ACCESS = now`, `PW_FAIL_CNT = 0`. 감사 로그 `TYPE = 'LOGIN'`.
6. 실패: `PW_FAIL_CNT + 1`. 임계값 이상이면 `ACCOUNT_LOCK = 'Y'`, `CCFA_MANAGER.BLOCK_TIME = now`.
   PW_POLICY 행이 없으면 새로 만든다. 임계값은 `CCFA_SYSTEM_PROP(PROP_KEY='PW_FAIL_LIMIT', COMPANY_IDX=0)`이 있으면 그 값, 없으면 5.
7. 로그아웃: `LOGIN = 'OFF-LINE'`, 감사 로그 `TYPE = 'LOGOUT'`.

### 3.2 역할

| 역할 | 조건 | 접근 범위 |
|---|---|---|
| `ROLE_SUPER` | `COMPANY_IDX = 0` | 전체 고객사 데이터, 시스템·메타 테이블, 고객사·라이선스·운영자 관리 |
| `ROLE_COMPANY` | 그 외 | 자기 `COMPANY_IDX` 데이터만. SUPER 전용 메뉴는 숨기고 URL 접근은 403 |

### 3.3 테넌트 격리

- `TenantContext`가 SecurityContext에서 현재 사용자의 `COMPANY_IDX`와 역할을 제공한다.
- `COMPANY_IDX` 컬럼이 있는 엔티티의 목록 조회: `CrudService`가 Specification에 `companyIdx = 현재값`을 자동 AND. SUPER는 필터 없음(검색 폼에서 고객사 선택 가능).
- 상세·수정·삭제: 조회 후 `COMPANY_IDX` 불일치면 404.
- 등록: COMPANY 역할은 `COMPANY_IDX`를 자기 값으로 강제 덮어쓴다.
- `COMPANY_IDX`가 없는 테이블(메타·시스템 정보 등)은 SUPER 전용.

### 3.4 보안 설정

- CSRF 활성(Thymeleaf 폼 자동 토큰), 세션 고정 방지, 동시 세션 1개, 세션 타임아웃 30분.
- 익명 허용: `/login`, `/webjars/**`, `/css/**`, `/js/**`, `/error`.
- 비밀번호 변경: SHA-256 hex로 저장(기존 방식 유지). salt 도입은 다른 시스템과의 호환 문제로 이번 범위 제외.

### 3.5 감사 로그

`AuditLogger`가 `CCFA_AUDIT_LOG`에 기록한다. 로그인/로그아웃과 모든 등록·수정·삭제·상태 변경이 대상이다.

| 컬럼 | 값 |
|---|---|
| COMPANY_IDX, COMPANY_NAME | 로그인 사용자의 고객사 (IDX 0이면 이름은 `CCFA_COMPANY` IDX 0 레코드의 이름) |
| USER_ID, USER_NAME | 로그인 사용자 |
| TYPE | `LOGIN`, `LOGOUT`, `CREATE`, `UPDATE`, `DELETE`, `STATUS` |
| MESSAGE | `"<테이블명> <행위> <식별자>"` 형식, 4000자 초과 시 절단 |
| IP | 요청 원격 주소, 15자 초과 시 앞 15자 |
| UA | User-Agent, 2048자 초과 시 절단 |
| INTERGRITY_HASH | 위 필드를 `|`로 이어 붙인 문자열의 SHA-256 hex |
| CREATEDTIME | now |

기록은 `REQUIRES_NEW` 트랜잭션에서 수행하고, 실패해도 본 트랜잭션은 깨지 않는다(로그만 남김).

## 4. 공통 CRUD 기반

### 4.1 클래스

- `CrudService<E, ID, S extends SearchForm>`: `search(S, Pageable)`, `get(ID)`, `create(E)`, `update(ID, E)`, `delete(ID)`.
  테넌트 필터, 감사 로그, `CREATEDTIME`/`UPDATEDTIME` 갱신을 처리한다.
  하위 클래스는 `toSpecification(S)`, 엔티티의 `companyIdx` 접근자(없으면 null), 기본값 채움을 구현한다.
- `CrudController<E, ID, F, S>`:

  | 메서드 | 경로 | 뷰 |
  |---|---|---|
  | GET | `/{base}` | `list` |
  | GET | `/{base}/new` | `form` |
  | POST | `/{base}` | redirect 상세 |
  | GET | `/{base}/{id}` | `detail` |
  | GET | `/{base}/{id}/edit` | `form` |
  | POST | `/{base}/{id}` | redirect 상세 |
  | POST | `/{base}/{id}/delete` | redirect 목록 |

- `ReadOnlyController`: 목록·상세만.
- `SearchForm`: `page`(0부터), `size`(기본 20), `sort`, `companyIdx`, `fromDate`, `toDate`. 테이블별 필드 추가.
- 폼 검증: Bean Validation. 문자열 길이는 ERD 길이를 `@Size(max=…)`로 옮긴다.

### 4.2 엔티티 매핑 규칙

| 상황 | 규칙 |
|---|---|
| IDX PK (29개) | `@Id @GeneratedValue(SEQUENCE)` + `@SequenceGenerator(sequenceName="<TABLE>_SEQ", allocationSize=1)`. **임시 이름**이며 실제 시퀀스 이름 제공 시 교체 |
| 문자열 단일 PK (CCFA_SYSTEM_INFO, CCFA_ERROR_TABLE, CCFA_FIDOCLIENT, FIDO2_DEMO_ACCESS_CODE) | `@Id String`. 등록 시 존재 여부 검사 후 저장 |
| CCFA_FDS_POLICY | `@Id Long companyIdx`, 채번 없음 |
| 복합키 (FIDO_STATISTICS, FIDO_STATISTICS_BAK, CCFA_STATISTICS_FILTER, CCFA_STATISTICS_ORDER, CCFA_SYSTEM_PROP) | `@EmbeddedId` |
| 예약어 `TO`, `LIMIT`, `VALUE` | `@Column(name = "\"TO\"")` 형태로 따옴표 명시 |
| CLOB (14개) | `@Lob String`. 목록은 CLOB 제외 인터페이스 프로젝션 |
| VARCHAR2 시각 (`CCFA_EXCEPTIONS.CREATEDTIME`, `CCFA_MAILING.SENDTIME`, `AWS_INFO.EXPIRATIONDATE`) | `String` |
| TIMESTAMP | `LocalDateTime` |
| NUMBER 정수 | `Long` |
| `FIDO2_DEMO_ACCESS_CODE.STARTTIME/ENDTIME` (epoch) | `Long` 저장, 화면에서 일시로 변환 표시·입력 |
| 참조 컬럼 (`COMPANY_IDX`, `OPTION_IDX`, `MENU_PARENT_IDX` 등) | 연관관계 없이 `Long`. 표시용 이름은 `CompanyLookup` 등 조회 서비스가 IDX→이름 맵으로 채움 |
| 기본값 컬럼 | 폼이 비면 서비스에서 ERD 기본값을 채움(`APPID.STATUS='use'`, `DEVICE_DEFAULT='F'`, `USERINFO.STATUS='O'` 등) |
| `EMPTY_CLOB()` | 빈 문자열과 null을 동일 취급 |

원칙: 엔티티 컬럼은 ERD와 1:1. 하나도 빼거나 더하지 않는다.

### 4.3 페이징·정렬

Spring Data `Pageable` → Hibernate Oracle `OFFSET … FETCH`. 기본 정렬은 PK 내림차순, 로그류는 `CREATEDTIME` 내림차순.

## 5. 화면

### 5.1 레이아웃

`layout/base.html` 하나. 좌측 사이드바(메뉴, 역할별 표시), 상단 바(사용자, 고객사, 로그아웃), 본문.
프래그먼트: `fragments/sidebar`, `pagination`, `search-bar`, `alert`, `confirm-modal`.
메뉴는 코드에 고정한다(`CCFA_MENU`는 데이터로만 다룬다).

### 5.2 메뉴와 화면 (S = SUPER 전용)

| 메뉴 | 화면 | 경로 | 유형 | 검색 조건 | 특수 처리 |
|---|---|---|---|---|---|
| 대시보드 | 통계 | `/` | 집계 | 기간, 고객사, 서비스명 | FIDO_STATISTICS 합계 카드(인증/등록/해지/TC 성공·실패) + Chart.js 일별 추이 |
| 고객사 | 고객사 (S) | `/companies` | CRUD | 이름, 유형, 사용상태 | 삭제 시 하위 데이터(APPID, USERINFO, MANAGER) 존재하면 차단 |
| | FDS 정책 | `/fds-policies` | CRUD | 고객사 | COMPANY_IDX가 PK, 고객사당 1건 |
| | 라이선스 (S) | `/licenses` | CRUD | 고객사, 서비스명 | |
| 운영자 | 운영자 (S) | `/managers` | CRUD | ID, 이름, 고객사, 상태 | 비밀번호 SHA-256 저장, 잠금 해제 버튼(PW_POLICY 초기화) |
| | 내 비밀번호 변경 | `/me/password` | 폼 | | 현재 비밀번호 확인 |
| FIDO | 앱 ID | `/appids` | CRUD | APPID, 서비스명, 상태 | |
| | 앱 서버 | `/appservers` | CRUD | 코드, ID, 유형 | |
| | 사용자 | `/users` | 조회+상태변경 | USERID, 서비스명, AAID, 상태 | PUBKEY/CERTIFICATE 앞 16자만. 상태 'O'/'X' 변경 |
| | 챌린지 | `/challenges` | 조회 | USERID, 서비스명, 기간 | |
| | 서명 | `/signs` | 조회 | USERID, 기간, DEL | PLAINTEXT 상세에서만 |
| | 거래 해시 | `/transaction-hashes` | 조회 | USERID, 기간 | |
| | 거래 확인 | `/transaction-confirmations` | 조회 | USERID, AAID, 기간 | |
| | 인증기기 기준 | `/criteria` | 조회 | AAID | |
| FIDO2 | 메타데이터 | `/fido2/metadata` | CRUD | AAGUID, 설명, 프로토콜 | ICON·인증서 CLOB 상세에서만 |
| | 크리덴셜 파라미터 | `/fido2/credential-params` | CRUD | 타입, 상태 | |
| | 데모 접근코드 | `/fido2/demo-access-codes` | CRUD | 코드, 벤더, 상태 | epoch ↔ 일시 변환 |
| 로그 | FIDO 로그 | `/logs/fido` | 조회 | 고객사, 서비스명, 시리얼, 기간 | JSONDATA 정리 출력 |
| | 감사 로그 | `/logs/audit` | 조회 | 고객사, 사용자, 유형, 기간 | |
| | 예외 로그 | `/logs/exceptions` | 조회 | 유형, 레벨, 메시지 | CREATEDTIME 문자열 검색 |
| | 메일/SMS 큐 | `/logs/mailing` | 조회 | 상태, 수신자 | |
| 시스템 (S) | 시스템 설정 | `/system/props` | CRUD | 키, 고객사 | 복합키 PROP_KEY+COMPANY_IDX |
| | 시스템 정보 | `/system/info` | CRUD | 키 | |
| | 에러 코드 | `/system/error-codes` | CRUD | 코드, 유형 | |
| | FIDO 서버 | `/system/fido-clients` | CRUD | 코드, 상태 | |
| | 어드민 기준 | `/system/criteria` | CRUD | AAID | JSONDATA 편집 |
| | 메뉴 정의 | `/system/menus` | CRUD | 이름, 코드 | 부모 IDX 선택 |
| | 코드 그룹/코드 | `/system/options` | CRUD | 이름 | OPTION 상세 안에 OPTIONS 인라인 목록·추가·삭제 |
| | 필드 정의 | `/system/fields` | CRUD | 테이블, 이름 | |

총 29개 화면(대시보드 1, 폼 1, CRUD 18, 조회 9).

### 5.3 공통 UX

- 검색 폼은 GET 쿼리스트링. 뒤로가기·북마크 유지.
- 등록·수정·삭제 성공 시 플래시 메시지.
- 삭제는 Bootstrap 확인 모달. JS `alert/confirm` 사용 금지.
- 목록은 대표 컬럼 6~8개, 상세는 전체 컬럼.
- 조회 전용 화면에는 등록·수정·삭제 버튼 없음.

## 6. 오류 처리

- `GlobalExceptionHandler(@ControllerAdvice)`: 미존재/테넌트 불일치 → 404, 권한 부족 → 403, 그 외 → 500. `templates/error/404.html`, `403.html`, `500.html`. 스택트레이스는 로그에만.
- 폼 검증 실패: 같은 폼 재렌더링, 필드별 메시지.
- `DataIntegrityViolationException`: 폼 상단 "이미 존재하는 값입니다" 메시지.
- 낙관적 잠금 없음(버전 컬럼 없음).

## 7. 설정

- `application.yml`: 공통. DB 접속은 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 환경 변수만. 기본값 없음.
- `application-local.yml`: Docker Oracle(`jdbc:oracle:thin:@localhost:1521/FREEPDB1`, 계정 `kbfido`), SQL 로그 활성.
- 서버 포트 8080, 컨텍스트 경로 `/`, 세션 타임아웃 30분.

## 8. 로컬 검증 환경 (운영 DB에 적용 금지)

- `docker/docker-compose.yml`: `gvenzl/oracle-free:23-slim`, 1521 포트, `docker/init` 마운트.
- `docker/init/01-schema.sql`: ERD 39개 테이블을 컬럼·타입·길이·NULL·기본값 그대로 옮긴 DDL + IDX 테이블 29개용 시퀀스(임시 이름 `<TABLE>_SEQ`) + ERD의 인덱스 33개.
- `docker/init/02-seed.sql`: `CCFA_COMPANY` IDX 0(전역)과 고객사 1건, `superuser`(SHA-256), 고객사 운영자 1명, 화면 대상 테이블별 샘플 3~5건, 최근 30일 `FIDO_STATISTICS`.
- 파일 머리와 README에 "로컬 검증 전용, 운영 스키마에 적용하지 말 것"을 명시한다.

## 9. 시퀀스 이름 교체 절차

실제 시퀀스 이름은 아래 쿼리로 확인한다.

```sql
SELECT table_name, column_name, data_default
  FROM user_tab_columns
 WHERE UPPER(data_default) LIKE '%NEXTVAL%'
 ORDER BY table_name;
```

결과를 받으면 (1) 각 엔티티의 `@SequenceGenerator.sequenceName`, (2) `docker/init/01-schema.sql`의 시퀀스 이름을 교체한다.
교체 전까지 운영 DB에 대한 INSERT는 동작을 보장하지 않는다.

## 10. 테스트

| 종류 | 대상 |
|---|---|
| 단위 | `Sha256PasswordEncoder`, `AuditLogger` 해시, epoch 변환, 테넌트 필터 Specification, 기본값 채움 |
| 엔티티 정합성 | 리플렉션으로 39개 엔티티의 `@Table`/`@Column` 이름 집합을 `src/test/resources/erd-columns.txt`(ERD 추출본)와 대조. 누락·추가 시 실패 |
| 통합 (Testcontainers Oracle Free) | 리포지토리 저장·조회, 시퀀스 채번, 예약어 컬럼 `TO`/`LIMIT`/`VALUE` 읽기·쓰기, 복합키 저장·조회, CLOB 저장 |
| 웹 (`@WebMvcTest`) | SUPER/COMPANY 역할별 접근 제어, 테넌트 필터 강제, CSRF |
| 수동 | Docker Oracle 기동 후 브라우저로 29개 화면 목록·상세·등록·수정·삭제 확인 |

## 11. 인도물

- 실행 가능한 Gradle 프로젝트 (`./gradlew bootRun --args='--spring.profiles.active=local'`)
- `README.md`: 실행 방법, Docker 기동, 시퀀스 이름 교체 위치, 로컬 DDL 주의사항, 기본 계정
- 본 설계 문서와 `docs/erd/kbfido-columns.txt`

## 12. 범위 밖

- `CCFA_FIDO_TLOG` (ERD에 없음)
- 비밀번호 저장 방식 강화(salt/bcrypt)
- `CCFA_MENU` 기반 동적 메뉴
- 엑셀 내보내기, 다국어
- 로그 테이블 인덱스 추가 등 DB 튜닝
