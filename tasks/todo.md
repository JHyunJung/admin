# 리팩터링·하드닝 1차 (refactor-harden, 2026-11-10)

목표: 외부 동작(URL·화면·SQL·로그·예외) 그대로, 진단표의 적용 가능 73건 중 1차 56건을 적용한다.
기준선: `./gradlew test` 933 tests, 0 fail, 7 skip (main deb40e4).
진단표·브리프·diff: `.superpowers/refactor-harden/20261110-1600/` (`diagnosis.md`, `group-<n>-*.md`, `group-<n>.diff`)

## 원칙
- 승인된 번호만 고친다. 로그 문구·예외·시그니처·SQL 원문은 건드리지 않는다.
- 묶음은 하나씩 순서대로: 구현 → 동작 동일성 리뷰 → Codex 게이트. FAIL 은 최대 2라운드, 그래도 FAIL 이면 그 이슈만 원복.
- 작업 중에는 커밋하지 않는다(커밋 단위는 사용자가 정한다). 1·2차가 끝난 뒤 사용자 요청으로 묶음별 12커밋으로 나눠 올렸다.

## 단계
- [x] 1. audit — 1, 2, 4, 5, 6 (리뷰 PASS · Codex 문제 없음)
- [x] 2. auth — 7, 8, 9 (리뷰 PASS · Codex 문제 없음)
- [x] 3. common — 12, 13, 14, 16, 17, 20 (리뷰 PASS · Codex 문제 없음. 16번의 `itemsFor` 사전 계산은 제외)
- [x] 4. common/TenantSelectionController + signup — 18, 19, 56, 57 (리뷰 PASS · Codex 문제 없음)
- [x] 5. company · config · dashboard — 21, 22, 23, 24, 25, 26, 27 (리뷰 PASS · Codex 문제 없음)
- [x] 6. fido — 28, 29, 30, 31, 34, 35, 36 (리뷰 PASS · Codex 문제 없음. 34·36 은 주석만)
- [x] 7. fido2 · log · statistics — 37, 39, 40, 41, 43, 45, 46, 58, 59 (리뷰 PASS · Codex 문제 없음)
- [x] 8. entity 테스트 · manager — 11, 49, 50, 55 (리뷰 PASS · Codex 문제 없음)
- [x] 9. system — 60, 61, 62, 63, 64, 65, 67, 68, 69, 72, 73 (리뷰 PASS · Codex 문제 없음. 64번의 `future!!` 제거는 제외)
- [x] 10. 전체 `./gradlew test` 를 기준선과 비교 (966 tests · 0 fail · 1 skip = 기준선 933 + 새 테스트 33. skip 7 → 1 은 로컬 Oracle(localhost:1521)이 떠 있어 `LocalOracle#isUp` 조건 테스트 6건이 이번에는 실행된 것)

## Review (2026-11-10)
- 결과: 1차 56건 전부 적용. 9개 묶음 모두 동작 동일성 리뷰 PASS · Codex 게이트 "문제 없음", 수정 루프·원복 0건.
- 부분 제외 2곳: 16번 `itemsFor` 사전 계산(요청 간 같은 리스트 인스턴스를 공유하게 됨), 64번 `future!!` 제거(주입된 HttpClient 가 null 을 돌려줄 때 결과 문구가 달라질 수 있음).
- 변경 규모: `src` 50파일 +447/−237 (tracked 기준) + 새 테스트 파일 6개. 새 테스트 33건.
- 전체 테스트: 966 tests · 0 fail · 1 skip (기준선 933 + 33). skip 7 → 1 은 로컬 Oracle 이 떠 있어 `LocalOracle#isUp` 조건 테스트 6건이 실행된 것이고, 남은 1건은 `SchemaConformanceTest`(schema-columns.txt 없음).
- 커밋: 묶음 1~9 를 묶음별 1커밋으로 `refactor/harden` 브랜치에 올렸다(2차와 함께 main 머지).
- 후속으로 남긴 테스트 공백: 7번 `propValue = null` 경로, 9번 로그아웃 `/login?logout`, 57번 `SignupPolicy.selectableCompanies` 단위 테스트, 55번 폼 기본값 GET, 21번 통합 테스트의 정렬 회귀 검출력.

## 2차 (2026-11-10 이어서 진행)
대상 8건: 10, 15, 48, 53, 54, 66, 70, 71. 방식은 1차와 같다(구현 → 동작 동일성 리뷰 → Codex 게이트, 묶음 순차, 커밋 안 함).
- [x] 11. JavaStrings 헬퍼 치환 · manager/web — 10, 53, 54 (묶음 10. 리뷰 PASS · Codex 문제 없음)
- [x] 12. common/PartialFetchImpl · signup — 15, 48 (묶음 11. 리뷰 PASS · Codex 문제 없음)
- [x] 13. system — 66, 70, 71 (묶음 12. 리뷰 PASS · Codex 문제 없음)
- [x] 14. 전체 `./gradlew test` 를 1차 결과(966 tests · 0 fail)와 비교 (986 tests · 0 fail · 1 skip = 966 + 새 테스트 20)

### Review 2차 (2026-11-10)
- 결과: 2차 8건 전부 적용. 3개 묶음 모두 동작 동일성 리뷰 PASS · Codex 게이트 "문제 없음", 수정 루프·원복 0건. 1차와 합쳐 64건.
- 변경 규모(2차만): `src` 37파일 +403/−166 (새 파일 `ManagerAccountController.kt`, `PendingSignupRow.kt`, `ReservedPathIds.kt` 포함). 새 테스트 20건.
- 전체 테스트: 986 tests · 0 fail · 1 skip(`SchemaConformanceTest`, schema-columns.txt 없음). 로컬 Oracle(fido-admin-oracle)을 띄운 상태라 `PartialFetchIntegrationTest` 2건도 실행됐다.
- 커밋: 1차·2차를 묶음별 12커밋(`refactor/harden`)으로 나눠 main 에 `--no-ff` 머지했다. 각 커밋은 묶음별 스냅숏 트리 그대로다.
- 후속으로 남긴 것
  - 53번으로 `SuperManagerService` KDoc 과 `SuperManagerServiceTest.kt:254` 주석이 지워진 줄(`applyTo` 의 companyIdx 쓰기)을 설명하게 됐다 — 주석 정리 필요.
  - 48번 `listDoesNotExposePasswordHash` 는 HTML 본문만 봐서, 엔티티를 다시 모델에 실어도 통과한다 — 모델 속성 타입 단언으로 보강 필요.
  - 66번 `fido-reload-` 스레드 초기화를 고정하는 상시 테스트가 없다(구현 중 임시 테스트로만 확인).

## 다음
- 보류: 3, 32, 33, 38, 42, 44, 47, 51, 52 (33·42·44 는 SQL_ID 변경 수용 여부 확인 필요)
- 보고만(74~222): 별도 과제. 우선순위 76 → 88·107·109·111 → 75·84 → 74
