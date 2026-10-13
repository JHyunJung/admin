# FIDO Admin

FIDO 서버 운영을 위한 관리자 웹. KBFIDO Oracle 스키마를 사용한다.

- Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Thymeleaf
- Oracle (운영), Docker Oracle Free (로컬·테스트)

## 목차

1. [로컬 실행](#로컬-실행)
2. [배포 설정](#배포-설정)
3. [화면 구성](#화면-구성)
4. [테스트](#테스트)
5. [개발 규칙](#개발-규칙)
6. [DB 참고 사항](#db-참고-사항)
7. [알려진 제약](#알려진-제약)

## 로컬 실행

1. Docker Desktop 을 켜고 로컬 Oracle 을 띄운다. 처음에는 이미지 다운로드와 초기화에 2~3분 걸린다.

   ```
   docker compose -f docker/docker-compose.yml up -d
   docker compose -f docker/docker-compose.yml ps   # healthy 확인
   ```

2. 애플리케이션을 띄운다.

   ```
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

3. `http://localhost:8080` 에 접속한다.

   | 계정 | 비밀번호 | 역할 |
   |---|---|---|
   | `superuser` | `Admin1234!` | 슈퍼관리자(SUPER) |
   | `kbadmin` | `Company1234!` | 고객사 운영자(COMPANY) |

로컬 DB 접속 정보와 초기화 방법은 [`docker/README.md`](docker/README.md) 에 있다.

## 배포 설정

### 프로파일

| 프로파일 | DB | SQL 출력 | 로그 레벨(앱/프레임워크) | 로그 출력 |
|---|---|---|---|---|
| `local` | 로컬 도커 고정 | 켬 | DEBUG / INFO | 콘솔 |
| `qa` | 환경 변수 | 끔 | DEBUG / INFO | 콘솔 + 파일 |
| `prod` (또는 미지정) | 환경 변수 | 끔 | INFO / WARN | 파일 |

### 환경 변수

| 변수 | 필수 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | 예 | JDBC URL. 기본값 없음 |
| `SPRING_DATASOURCE_USERNAME` | 예 | DB 계정 |
| `SPRING_DATASOURCE_PASSWORD` | 예 | DB 비밀번호 |
| `DB_CONFIG_KEY` | 암호문을 쓸 때 | DB 접속 정보 복호화 키(Base64, 디코딩 후 32바이트) |
| `LOGGING_FILE_PATH` | 아니오 | 로그 디렉터리. 기본 `./logs`(실행 위치 기준). 기동 스크립트는 `LOG_PATH` 로 받는다 |
| `FIDO_ADMIN_EXTERNAL_ALLOWED_CIDRS` | 아니오 | 인증 없는 서버 간 경로의 접근 허용 대역. 비우면 모두 허용 |

```
SPRING_DATASOURCE_URL=jdbc:oracle:thin:@db:1521/SVC \
SPRING_DATASOURCE_USERNAME=… SPRING_DATASOURCE_PASSWORD=… \
  java -jar fido-admin.jar --spring.profiles.active=prod
```

### 기동 스크립트

서버에서는 `scripts/fido-admin.sh` 로 띄우고 내린다. 프로파일은 무엇이든 지정할 수 있다.

```
./gradlew bootJar                                    # build/libs/fido-admin-*.jar
scripts/fido-admin.sh start qa                       # qa 로 기동
scripts/fido-admin.sh start prod                     # prod 로 기동
scripts/fido-admin.sh start local                    # 로컬 도커 DB 로 기동
scripts/fido-admin.sh start qa,debug                 # 여러 프로파일을 함께
scripts/fido-admin.sh restart                        # 실행 중인 프로파일 그대로 재기동
scripts/fido-admin.sh restart prod                   # 다른 프로파일로 재기동
scripts/fido-admin.sh stop | status
```

- 프로파일은 인자 > `SPRING_PROFILES_ACTIVE` 환경 변수 > `fido-admin.env` 의 값 > `prod` 순으로 정한다.
- 설정 파일은 `scripts/` 에서 읽는다(`FIDO_ADMIN_ENV_DIR` 로 바꿀 수 있다). `fido-admin.env.example` 을 복사해 쓴다.
  - `fido-admin-<프로파일>.env` — 그 프로파일 전용 값(예: `fido-admin-qa.env`, `fido-admin-prod.env`)
  - `fido-admin.env` — 모든 프로파일 공통 값
  - 같은 키는 환경 변수 > 프로파일 전용 파일 > 공통 파일 순으로 앞의 것이 이긴다. 이 파일들은 저장소에 올리지 않는다.
- `local` 이 아닌 프로파일은 DB 접속 정보 세 값이 없으면 기동하지 않는다.
- jar 안에 `application-<프로파일>.yml` 이 없는 프로파일은 경고만 하고 공통 설정으로 띄운다.
- jar 는 `APP_JAR` 를 쓰고, 없으면 앱 디렉터리와 `build/libs` 에서 가장 최근 `fido-admin*.jar` 를 찾는다.
- 로그는 `LOG_PATH`(기본 `<앱 디렉터리>/logs`)에 쓴다. 콘솔 출력은 `console.out`, PID 와 실행 프로파일은 `fido-admin.pid`(`.profile`)에 남긴다.
- `stop` 은 SIGTERM 후 `STOP_TIMEOUT`(기본 60초)까지 기다리고, 그래도 남아 있으면 강제 종료한다.
  감사 로그 대기열을 비우는 데 최대 30초를 쓰므로 이보다 짧게 잡지 않는다.

### DB 접속 정보 암호화

DB 접속 정보 세 값은 평문 대신 `ENC(...)` 암호문(AES-256-GCM)으로 줄 수 있다. 이때 `DB_CONFIG_KEY` 를 함께 준다.
모두 평문이면 키는 없어도 된다. `ENC(` 로 시작하는 평문은 앞에 `[plain]` 을 붙인다.

```
export DB_CONFIG_KEY=$(openssl rand -base64 32)          # 키는 안전한 곳에 따로 보관
DB_CONFIG_VALUE='비밀번호' ./gradlew -q encryptDbConfig  # → ENC(...) 출력
SPRING_DATASOURCE_PASSWORD='ENC(...)' java -jar fido-admin.jar --spring.profiles.active=prod
```

### 로그

- 로그 디렉터리 아래 `fido-admin.log`(전체)와 `fido-admin-error.log`(WARN 이상)에 쓴다. 일자별로 롤링하고 30일 보관한다.
- 모든 줄에 `[req=요청ID user=로그인ID tenant=소속]` 이 붙는다. 같은 요청 ID 가 응답 헤더 `X-Request-Id` 로 나가므로,
  오류 신고 때 이 값을 받으면 그 요청의 로그만 바로 찾을 수 있다.

### 보안 기본값

- 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax` 이고 오류 응답에 예외 정보를 싣지 않는다(`application.yml`).
  `local` 만 http 라 `Secure` 를 끈다.
- 인증 없는 서버 간 경로 `/api/svc/**`(FIDO 서버 자가등록)과 `/external/**`(라이선스 수신)는
  `FIDO_ADMIN_EXTERNAL_ALLOWED_CIDRS`(쉼표 구분 CIDR 또는 IP, 예 `10.0.0.0/8,192.168.1.5`)로 접근 대역을 제한한다.
  리버스 프록시 뒤라면 `server.forward-headers-strategy` 도 설정해야 실제 클라이언트 주소로 판정한다.

## 화면 구성

화면은 29개(대시보드 1, 폼 1, CRUD 18, 조회 9)와 운영자 가입 화면 2개다.
메뉴는 `MenuRegistry` 에 고정되어 있고 `CCFA_MENU` 는 데이터로만 다룬다.

### 영역과 테넌트

슈퍼관리자는 한 번에 한 고객사만 본다. 로그인하면 `/select-tenant` 에서 고객사를 고르고,
이후 사이드바의 고객사 전환기로 언제든 바꿀 수 있다. 선택한 고객사가 조회·등록·수정·삭제 전부의 경계다.

| 영역 | 열리는 조건 | 화면 |
|---|---|---|
| 테넌트 | 고객사를 선택해야 열림 | 대시보드, FIDO 데이터, 로그(감사 로그 포함), 라이선스, 운영자(`/managers`), 시스템 설정 등 `COMPANY_IDX` 로 데이터를 가르는 화면 |
| 시스템 | 항상 열림(SUPER 전용) | 고객사 관리, 가입 승인, 슈퍼관리자 계정, FIDO2 메타데이터, 시스템 정보 등 `COMPANY_IDX` 가 없는 전역 테이블 화면 |
| 개인 | 항상 열림 | 내 비밀번호 변경 |

- 고객사 운영자(`COMPANY`)는 고객사를 고르지 않고 항상 자기 소속 고객사만 본다.
- 슈퍼관리자 계정(`COMPANY_IDX = 0`)은 고객사로 선택될 수 없어 `/managers` 에 나타나지 않는다.
  슈퍼관리자 계정은 `/managers/super` 에서만 관리한다.

### 운영자 가입

- **가입 신청 `/signup`** — 로그인 없이 열리는 공개 폼. 로그인 화면의 "가입 신청" 링크로 들어간다.
  소속 고객사를 고르지 않으며 항상 `STATUS = 승인대기`, `COMPANY_IDX = -1`(미배정)로 저장된다. 신청만으로는 로그인할 수 없다.
- **가입 승인 `/signups`** — SUPER 전용. 승인할 때 고객사를 지정하고 `STATUS = 활성` 으로 바꾼다. 거절은 사유를 남긴다.
  전역 고객사(`IDX 0`)는 고를 수 없어 승인으로 SUPER 계정을 만들 수 없다.

### 로그 목록 동작

- 행이 많은 로그 목록(SIGN, CHALLENGE, TRANSACTIONHASH, TRANSACTION_CONFIRMATION, 감사 로그, FIDO 로그)은
  기본 정렬이 `IDX` 역순이다. 시각 정렬은 목록 머리글로 고를 수 있다(FIDO 로그 제외).
- 이 목록들은 같은 조건의 총 건수를 30초간 다시 쓴다. FIDO 로그는 조건 검색 결과도 30초간 다시 쓴다.
  그 사이 새로 쌓인 행과 "총 N건" 은 늦게 보일 수 있다.
- 감사 로그는 전용 스레드가 저장한다. 대기열(500건)이 차면 요청 스레드에서 바로 저장하고, 종료할 때는 대기열을 비운다.

## 테스트

```
./gradlew test
```

- Docker 가 있으면 Testcontainers Oracle 통합 테스트까지 실행하고, 없으면 건너뛴다.
- `EntityBootTest`, `AuditLogRoundTripTest`, `LoginLockLoadTest`, `SelectedTenantWiringTest`, `PartialFetchIntegrationTest` 는
  로컬 Docker Oracle(`local` 프로파일)을 쓴다. `localhost:1521` 이 닫혀 있으면 건너뛴다(`LocalOracle#isUp`).

> Testcontainers 가 "Could not find a valid Docker environment" 로 실패하면
> `~/.docker-java.properties` 에 `api.version=1.41` 을 넣는다.
> Docker Desktop 의 최소 API 버전과 docker-java 기본값이 어긋나는 경우가 있다.

## 개발 규칙

### 공통

- 외부 CSS/JS/폰트를 호출하지 않는다. 정적 자원은 WebJars 와 `src/main/resources/static` 에 둔다.
- JS `alert/confirm/prompt` 를 쓰지 않는다. 확인 창은 공통 Bootstrap 모달을 쓴다.
- `docker/init/*.sql` 은 로컬 검증 전용이다. 운영 DB 에 적용하지 않는다.
  스크립트 앞에 `DB_NAME` 이 `FREE` 가 아니면 멈추는 가드가 있지만, 가드를 믿고 실행하지 않는다.

### 화면을 추가할 때

- `CrudController`/`CrudService` 또는 `ReadOnlyController` 를 상속한다.
  테넌트 격리(목록 필터, 상세·수정·삭제 소유 검사, 등록 시 `COMPANY_IDX` 강제)는 기반 클래스가 처리한다.
- `companyIdxAttribute()` 는 엔티티의 `COMPANY_IDX` 속성명을 돌려준다.
  `null` 을 돌려주면 SUPER 전용 화면이 된다(`COMPANY_IDX` 가 없는 메타·시스템 테이블용).
- `COMPANY_IDX` 가 없는 화면은 `MenuRegistry` 의 `superOnly` 와 `SecurityConfig.SUPER_PATHS` 를 함께 맞춘다.
- 문자열 길이 검증은 `@Size` 가 아니라 `@ByteSize` 를 쓴다. DB 가 BYTE 의미(`NLS_LENGTH_SEMANTICS=BYTE`, `AL32UTF8`)라
  한글 1자가 3바이트이고, `@Size` 로는 검증을 통과한 값이 `ORA-12899` 로 저장에 실패한다.
- 정렬 가능한 컬럼은 `sortableProperties()` 에 선언한다. 목록에 없는 값은 기본 정렬로 되돌아간다.
- `toEntity(form)` 에서 식별자를 폼 값으로 채우지 않는다. 채우면 `save()` 가 MERGE 로 동작해 기존 행을 덮어쓸 수 있다.
- 할당형 PK 테이블(문자열 PK, COMPANY_IDX PK, 복합키)은 `AssignedIdCrudService` 를 상속하고 `assignedId()` 를 구현한다.
  등록은 존재 검사 + `persist` 로만 하므로 이미 있는 키는 "이미 존재하는 값" 오류가 된다.
- CLOB 컬럼이 있는 테이블은 목록 DTO 에서 그 컬럼을 빼고 서비스의 `listExcludedProperties()` 에 같은 속성명을 적는다.
  목록 조회가 SELECT 절에서 그 컬럼을 뺀다(`PartialFetch`). 상세 조회는 영향이 없다.
- 행이 많은 목록은 `countCacheTtl()` 로 총 건수를 캐시할 수 있다. 리포지터리는 `PartialFetch` 를 직접 상속하고,
  검색 폼은 모든 조건을 `extraParams()` 에 넣어야 한다.
- 폼 화면은 `#fields.allErrors()` 로 모든 검증 오류를 표시한다.
- 예시 구현: CRUD 는 `company/`, 조회 전용은 `log/`.

`ArchitectureRulesTest` 가 위 규칙 중 코드로 검사할 수 있는 것을 확인한다 — 메뉴 `superOnly` 와 SUPER 매처의 일치,
화면 경로의 `MenuRegistry` 등록, `basePath()` 와 `@RequestMapping` 의 일치, 전역 테이블 화면의 SUPER 전용,
정렬 속성의 실재, 폼의 `@ByteSize` 사용.

### 운영자 계정

- `/managers` 와 `/managers/super` 의 공통 규칙은 `ManagerAccountService` 에 있다.
- `USER_ID` 전역 유일성은 가입 신청까지 포함해 `ManagerUserIdGuard` 가 지킨다.

## DB 참고 사항

### 스키마 일치

엔티티 컬럼은 DB 스키마와 일치해야 한다(39 테이블 / 354 컬럼). `SchemaConformanceTest` 가 컬럼표
`src/test/resources/schema-columns.txt` 와 대조한다. 이 파일은 저장소에 없으며, 없으면 테스트를 건너뛴다.

### 시퀀스 이름

시퀀스 이름은 임시로 `<TABLE>_SEQ` 다. 배포 전에 대상 DB 에서 실제 이름을 확인해 바꾼다.

```sql
SELECT table_name, column_name, data_default
  FROM user_tab_columns
 WHERE UPPER(data_default) LIKE '%NEXTVAL%'
 ORDER BY table_name;
```

바꿀 곳은 각 엔티티의 `@SequenceGenerator(sequenceName = ...)` 와 `docker/init/01-schema.sql` 이다.
바꾸기 전에는 대상 DB 에 대한 INSERT 가 보장되지 않는다.

### DBA 요청 사항

목록 인덱스와 감사 로그 시퀀스의 캐시·증가폭 변경은 [`docs/dba/2026-10-13-인덱스-시퀀스-요청.md`](docs/dba/2026-10-13-인덱스-시퀀스-요청.md) 에 정리했다.

## 알려진 제약

- `CCFA_AUDIT_LOG.IP` 가 `VARCHAR2(15)` 라 IPv6 주소는 앞 15자만 저장된다.
- 비밀번호는 salt 없는 SHA-256 hex 로 저장한다.
- 고객사 삭제 전 하위 데이터 검사는 검사와 삭제 사이의 동시 삽입을 막지 못한다(스키마에 FK 없음).
- SUPER 의 고객사 재배정과 COMPANY 의 수정이 동시에 일어나면 경쟁이 생길 수 있다.
- 가입 신청에 횟수 제한이 없다. 사내망 전용이 현재의 완화책이다. 아이디 중복 응답으로 계정 존재를 추측할 수 있고,
  승인대기 행을 대량으로 쌓을 수 있다.
- 가입 신청은 감사 로그를 남기지 않는다. `AuditLogger` 는 인증된 사용자가 없으면 기록하지 않는다(승인·거절은 기록된다).
- `/api/svc/**` 와 `/external/**` 는 인증이 없다. FIDO 서버가 토큰을 보내지 않기 때문이다.
  누구나 서버를 등록·OFF 하거나 등록된 URL 로 reload 요청을 보내게 할 수 있다. `FIDO_ADMIN_EXTERNAL_ALLOWED_CIDRS` 가 현재의 완화책이다.
- 감사 로그 무결성 해시는 키 없는 SHA-256 이라 DB 쓰기 권한자가 재계산해 맞출 수 있다(변조 탐지가 아니라 일관성 검사 수준).
- 감사 로그는 비동기로 저장하므로 프로세스가 강제 종료되면 대기열에 남은 기록이 유실될 수 있다.
- `CCFA_SYSTEM_PROP` 화면은 복합키를 경로 한 조각 `{PROP_KEY}@{COMPANY_IDX}` 로 다루므로 `/` 가 들어간 `PROP_KEY` 는 지원하지 않는다(등록 폼에서 거부).
