# TODO

2026-11-24 에 완료 기록을 걷어냈다. 리팩터링·하드닝 1~3차(70건)와 보안 75·76·84·107·109·111, Medium 7건, 103 로그 정제, 상태 배지 원값 표시의 단계별 기록은 `git show a2a48e4:tasks/todo.md` 로 볼 수 있다.

- 기준선: `./gradlew test` 1041 tests · 0 fail · 1 skip (2026-11-13, 로컬 Oracle 기동 상태. skip 1건은 `SchemaConformanceTest` — schema-columns.txt 없음)
- 진단표(번호의 출처): `.superpowers/refactor-harden/20261110-1600/diagnosis.md`

## 남은 일
- [ ] SUPER 관제 화면(`/monitoring`) — 설계 `docs/superpowers/specs/2026-11-25-super-monitoring-design.md`, 계획 `docs/superpowers/plans/2026-11-25-super-monitoring.md` (2026-11-25 계획 작성, 구현 전)
  - [ ] 1 설정·상태 판정·주간 계산
  - [ ] 2 DayProfile
  - [ ] 3 날짜 테이블 조회와 보관
  - [ ] 4 예외 건수·통계 조회
  - [ ] 5 MonitoringService
  - [ ] 6 화면·메뉴·접근 제어
  - [ ] 7 전체 검증과 기록
- [ ] 운영 확인(88): 운영·QA 서버의 `scripts/fido-admin-<프로파일>.env` 에 `FIDO_ADMIN_EXTERNAL_ALLOWED_CIDRS` 를 넣는다. 76 수정으로 이제 실제로 동작하며, 비어 있으면 `/api/svc`·`/external` 이 누구에게나 열려 있다

## 보류 (사용자 결정 2026-11-13)
- DBA 요청서 적용: `docs/dba/2026-10-13-인덱스-시퀀스-요청.md`
- 성능 33·42·44: 목록 SELECT 에서 안 쓰는 큰 컬럼 제외. SELECT 원문(SQL_ID)이 바뀌므로 DBA 확인이 필요하다
- 보안 74: 비밀번호 저장 형식. KBFIDO 스키마·타 시스템 호환이 걸려 별도 합의가 필요하다
- 다른 폼(Company·Appid·Fido2CredentialParams)의 STATUS 허용값: 운영 DB 값 확인이 먼저다
- 진단표의 "보고만" 항목(74~222) 중 위에 없는 것: 별도 과제

## 후속으로 남긴 테스트·주석 공백 (2026-11-10 기록, 이후 처리 여부 미확인)
- 7번 `propValue = null` 경로
- 9번 로그아웃 `/login?logout`
- 57번 `SignupPolicy.selectableCompanies` 단위 테스트
- 55번 폼 기본값 GET
- 21번 통합 테스트의 정렬 회귀 검출력
- 48번 `listDoesNotExposePasswordHash` 는 HTML 본문만 봐서 엔티티를 다시 모델에 실어도 통과한다 — 모델 속성 타입 단언으로 보강
- 66번 `fido-reload-` 스레드 초기화를 고정하는 상시 테스트가 없다
- 53번으로 `SuperManagerService` KDoc 과 `SuperManagerServiceTest.kt` 의 주석이 지워진 줄(`applyTo` 의 companyIdx 쓰기)을 설명하게 됐다 — 주석 정리

## 알고 있는 한계 (고치지 않기로 한 것)
- 107: 이미 실행 중인 DNS 해석은 중단할 수 없다(스레드 4개 상한으로 묶음). 해석과 접속 사이 DNS 응답 변경(rebinding)은 막지 못하며 앞단 CIDR 허용 목록이 맡는다
- 75: 읽기 감사 DATA_VIEW 는 SUPER 가 X 선택 중 `/companies/Y` 상세를 열면 "고객사 상세 조회 (ID: Y)" 가 X 로 남는다(값은 없음)
- 75: 감사 로그를 COMPANY_IDX 로 집계하는 외부 소비처가 있으면 SUPER 전역 작업이 0 쪽으로 모인다
- 16번 `itemsFor` 사전 계산, 64번 `future!!` 제거는 동작이 달라질 수 있어 적용하지 않았다

## 닫은 것
- 로그인 390px 대응: 모바일로 볼 일이 없어 안 한다 (2026-11-13)
- 정리 항목(@ConfigurationProperties·basePath 보일러플레이트·FQN import): Kotlin 전환 뒤 실익 없음
- V2 일러스트 원본 PNG: 원본을 찾지 못했다. `static/img/ill/*.webp` 10장을 기준으로 삼는다 (2026-11-13)
