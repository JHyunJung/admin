# FIDO Admin

기존 KBFIDO Oracle 스키마를 **변경 없이** 사용하는 관리자 웹. Java 17, Spring Boot 3.5, Thymeleaf, Spring Data JPA.

## 실행

1. Docker Desktop 실행 후 로컬 Oracle 기동 (로컬 검증 전용):
   `docker compose -f docker/docker-compose.yml up -d` — `docker compose -f docker/docker-compose.yml ps` 에서 `healthy` 확인.
2. 애플리케이션: `./gradlew bootRun --args='--spring.profiles.active=local'`
3. `http://localhost:8080` — 계정 `superuser / Admin1234!` (SUPER), `kbadmin / Company1234!` (고객사 운영자)

운영 환경은 `local` 프로파일 대신 환경 변수 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 를 준다. 기본값은 없다.

## 테스트

`./gradlew test` — Docker 가 있으면 Testcontainers Oracle 통합 테스트까지 실행되고, 없으면 건너뛴다.
`EntityBootTest`, `AuditLogRoundTripTest`, `LoginLockLoadTest` 는 로컬 Docker Oracle(`local` 프로파일)이 떠 있어야 통과한다.

> Testcontainers 가 "Could not find a valid Docker environment" 로 실패하면
> `~/.docker-java.properties` 에 `api.version=1.41` 을 넣는다.
> Docker Desktop 의 최소 API 버전과 docker-java 기본값이 어긋나는 경우가 있다.

## 시퀀스 이름 교체

시퀀스 이름은 임시로 `<TABLE>_SEQ` 다. 실제 이름은 운영 DB 에서 아래로 확인한다.

    SELECT table_name, column_name, data_default
      FROM user_tab_columns
     WHERE UPPER(data_default) LIKE '%NEXTVAL%'
     ORDER BY table_name;

교체 위치: 각 엔티티의 `@SequenceGenerator(sequenceName = ...)`, `docker/init/01-schema.sql`.
교체 전까지 운영 DB INSERT 는 보장하지 않는다.

## 규칙

- `docker/init/*.sql` 은 **로컬 검증 전용. 운영 DB 에 적용하지 않는다.**
  스크립트 앞에 `DB_NAME` 이 `FREE` 가 아니면 중단하는 가드가 있지만, 가드를 믿고 실행하지 않는다.
- 엔티티 컬럼은 `docs/erd/kbfido-columns.txt` 와 1:1 (39 테이블 / 354 컬럼).
  `ErdConformanceTest` 가 검증하며, 컬럼을 빼거나 더하면 실패한다.
- 외부 CSS/JS/폰트 호출 없음. 정적 자원은 WebJars 와 `src/main/resources/static`.
- JS `alert/confirm/prompt` 금지. 삭제 확인은 Bootstrap 모달.

### 화면을 추가할 때 (2부 작업자용)

- `CrudController`/`CrudService` 또는 `ReadOnlyController` 를 상속한다.
  테넌트 격리(목록 필터, 상세·수정·삭제 소유 검사, 등록 시 `COMPANY_IDX` 강제)는 기반이 처리한다.
- `companyIdxAttribute()` 는 **반드시** 해당 엔티티의 `COMPANY_IDX` 속성명을 돌려준다.
  `null` 을 돌려주면 그 화면은 SUPER 전용이 된다(`COMPANY_IDX` 가 없는 메타·시스템 테이블용).
- 문자열 길이 검증은 `@Size` 가 아니라 **`@ByteSize`** 를 쓴다.
  DB 가 BYTE 의미(`NLS_LENGTH_SEMANTICS=BYTE`, `AL32UTF8`)라 한글 1자가 3바이트다.
  `@Size` 를 쓰면 검증을 통과한 뒤 `ORA-12899` 로 저장에 실패한다.
- 정렬 가능한 컬럼은 `sortableProperties()` 에 선언한다. 목록에 없는 값은 기본 정렬로 되돌아간다
  (매핑되지 않은 속성이 들어오면 500 이 나기 때문).
- `toEntity(form)` 에서 **식별자를 폼 값으로 채우지 않는다.** 채우면 `save()` 가 INSERT 가 아니라
  MERGE 로 동작해 기존 행을 덮어쓸 수 있다. 할당형 PK 테이블
  (`CCFA_SYSTEM_INFO`, `CCFA_ERROR_TABLE`, `CCFA_FIDOCLIENT`, `FIDO2_DEMO_ACCESS_CODE`, `CCFA_FDS_POLICY`)
  화면을 만들 때는 `persist` 기반 삽입 전용 경로로 바꾸는 것을 먼저 검토한다.
- 폼 화면에는 `#fields.allErrors()` 로 모든 검증 오류를 표시한다.
- 참고 구현: CRUD 는 `company/`, 조회 전용은 `log/audit`.

## 알려진 제약

- `CCFA_AUDIT_LOG.IP` 가 `VARCHAR2(15)` 라 IPv6 주소는 앞 15자만 저장된다(설계 3.5 명시, 스키마 무변경 제약).
- 비밀번호는 기존 시스템 호환을 위해 salt 없는 SHA-256 hex 로 저장한다(설계 3.4 / 12, 범위 밖).
- 고객사 삭제 전 하위 데이터 검사는 검사와 삭제 사이의 동시 삽입을 막지 못한다(ERD 에 FK 없음).
- SUPER 가 고객사를 재배정하는 것과 COMPANY 의 수정이 동시에 일어나면 경쟁이 발생할 수 있다.

## 문서

- 설계: `docs/superpowers/specs/2026-09-16-fido-admin-design.md`
- 구현 계획 1부: `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md`
