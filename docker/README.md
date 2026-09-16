# 로컬 검증용 Oracle

**이 디렉터리의 SQL은 로컬 검증 전용이다. 운영 DB에 절대 적용하지 않는다.**

- 기동: `docker compose -f docker/docker-compose.yml up -d` (최초 기동 시 이미지 다운로드 + 초기화에 2~3분)
- 상태: `docker compose -f docker/docker-compose.yml ps` 에서 `healthy` 확인
- 접속: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`, `kbfido / kbfido`
- 초기화 다시 하기: `docker compose -f docker/docker-compose.yml down -v` 후 다시 `up -d`
- 시퀀스 이름은 임시(`<TABLE>_SEQ`). 실제 이름을 받으면 `01-schema.sql`과 엔티티의 `@SequenceGenerator.sequenceName`을 함께 교체한다.
