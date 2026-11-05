# 로컬 검증용 Oracle

**이 디렉터리의 SQL 은 로컬 검증 전용이다. 운영 DB 에 적용하지 않는다.**

| 항목 | 값 |
|---|---|
| 기동 | `docker compose -f docker/docker-compose.yml up -d` (첫 기동은 이미지 다운로드와 초기화에 2~3분) |
| 상태 확인 | `docker compose -f docker/docker-compose.yml ps` 에서 `healthy` |
| 접속 | `jdbc:oracle:thin:@localhost:1521/FREEPDB1`, `kbfido / kbfido` |
| 초기화 다시 하기 | `docker compose -f docker/docker-compose.yml down -v` 후 `up -d` |

초기화 스크립트(`init/`)는 순서대로 실행된다.

- `01-schema.sql` — 테이블·시퀀스·인덱스
- `02-seed.sql` — 로그인 계정·고객사 등 기본 데이터
- `03-dummy.sql` — 화면 확인용 더미 데이터(화면별 10건)

시퀀스 이름은 임시(`<TABLE>_SEQ`)다. 실제 이름으로 바꿀 때는 `01-schema.sql` 과 엔티티의
`@SequenceGenerator(sequenceName = ...)` 를 함께 바꾼다.
