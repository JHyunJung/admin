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

## 3차 (2026-11-10, 브랜치 refactor/harden-3)
대상 6건: 3, 32, 38, 47, 51, 52. 방식은 1·2차와 같다. 33·42·44 는 목록 SELECT 원문(SQL_ID)이 바뀌어 "SQL 원문 불변" 원칙 밖이므로 DBA 확인이 필요한 별도 성능 과제로 넘긴다.
- [x] 15. audit · fido · fido2 — 3, 32, 38 (묶음 13. 리뷰 PASS · Codex 문제 없음)
- [x] 16. log · manager — 47, 51, 52 (묶음 14. 리뷰 PASS · Codex 문제 없음)
- [x] 17. 전체 `./gradlew test` 를 2차 결과(986 tests · 0 fail)와 비교 (986 tests · 0 fail · 1 skip — 동일, 로컬 Oracle 기동 상태)

### Review 3차 (2026-11-10)
- 결과: 6건 전부 적용. 묶음 13·14 모두 동작 동일성 리뷰 PASS · Codex 게이트 "문제 없음", 수정 루프·원복 0건. 누적 70건.
- 변경 규모: `src` 7파일 (audit/AuditReadInterceptor, fido/web/AppserverController, fido2 서비스 2개, log/web/FidoLogController, manager 서비스 3개). 새 테스트 없음(32번은 임시 WebTest 로 변경 전·후 동일 출력 확인 뒤 삭제).
- 전체 테스트: 986 tests · 0 fail · 1 skip(`SchemaConformanceTest`).
- 스냅숏: `snap-13.txt`, `snap-14.txt`. snap-12 는 docs 커밋 전 트리라 diff 는 `-- src` 로 한정해 만들었다.
- 커밋: 아직 안 함(브랜치 `refactor/harden-3`).

## 보안 76 (2026-11-11, 브랜치 fix/external-filter-encoded-path)
- [x] 재현: 허용 대역 10.0.0.0/8, 127.0.0.1 에서 원문 `/external/license/..` 403 · `/%65xternal/license/..` 200, `/%61pi/svc/reg/..` 400(컨트롤러 도달)
- [x] 실패 테스트 먼저(인코딩 접두사·`;`·`//`·컨텍스트 경로·잘못된 인코딩·허용 대역 안) → 2건 실패 확인
- [x] 수정: `shouldNotFilter` 가 원문 또는 `UrlPathHelper` 정규화 경로 중 하나라도 서버 간 경로면 거른다(거르는 범위는 넓어지기만 한다)
- [x] 검증: 필터 테스트 8/0, 전체 990 · 0 fail · 1 skip(986 + 4), 실제 앱에서 인코딩 경로 5종 403 · `/login` 200, Codex "문제 없음"
- 동작 변경: 허용 목록이 설정된 경우에만, 대역 밖에서 인코딩·`;`·`//` 로 접두사를 꾸민 요청이 컨트롤러 도달 → 403. 허용 목록이 비어 있으면(기본값) 변화 없음.

## 보안 109·111·107 (2026-11-11, 브랜치 fix/fido-client-registration)
결정(사용자): 109 → 409 거절, 111 → 예약어만 400(한글·긴 이름은 지금처럼 받음), 107 → 링크로컬·와일드카드만 차단(루프백·사설망 허용), 88 → 코드 변경 없이 운영 확인.
- [x] 109: 새 행의 잘린 SERVERCODE 를 다른 SERVERNAME 이 쓰고 있으면 409, 기존 행을 덮지 않는다. 조회는 저장 기준(1024바이트 절단)과 맞춘다(deregister 포함)
- [x] 111: 새 행의 SERVERCODE 가 new·reload(대소문자 무시)·점만이면 400. 폼과 같은 판정 함수 `isReservedPathId`
- [x] 107: reload 대상 호스트를 풀어 링크로컬·와일드카드면 "차단된 주소" 실패. 검사는 전용 실행기(4스레드·대기 64)에서 비동기, 서버별 응답 예산 안. 포기한 요청은 해석·전송하지 않고, 실제 전송 future 까지 취소
- [x] 검증: 통합 테스트 2건은 수정 전 코드에서 200 으로 실패 확인, trickle·대기 작업 테스트도 수정 전 실패 확인. 전체 1001 · 0 fail · 1 skip. Codex 4라운드(1~3라운드 지적 4건 반영) → "문제 없음"
- 남은 한계: 이미 실행 중인 DNS 해석은 중단할 수 없다(스레드 4개 상한으로 묶음). 해석과 접속 사이 DNS 응답 변경(rebinding)은 막지 못한다 — 앞단 CIDR 허용 목록이 맡는다.

## 보안 75·84 (2026-11-11, 브랜치 fix/audit-attribution)
결정(사용자): 75 → 전역 테이블 작업은 행위자 소속(SUPER=0), 84 → LICENSE·HASHVALUE 만 마스크(연락처 유지).
- [x] 75: `AuditLogger.logGlobal` 추가. `CrudService` 의 CREATE·UPDATE·DELETE 는 `companyIdxAttribute()==null` 이면 logGlobal. 전역 서비스의 직접 기록(Option·Company 연쇄, 슈퍼매니저 잠금 해제, reload 수동 전송, AAID 정책 등록)도 logGlobal. Codex 지적으로 가입 승인·거절(SUPER 전용)·자기 비밀번호 변경도 logGlobal
- [x] 그대로 선택 고객사로 남는 것: 고객사별 테이블 CRUD, FidoSetting, Userinfo 상태, AAID 고객사별 상태 토글, 읽기 감사(MENU_VIEW·DATA_VIEW)
- [x] 84: `AuditChanges.secretFieldsOf` 가 CcfaLicense 의 license·hashvalue 를 마스크
- [x] 검증: 새 테스트 4건(logGlobal 귀속·미로그인, 라이선스 마스킹, 비밀번호 변경 귀속), 기대값 20곳 log → logGlobal(의도한 변경). 전체 1005 · 0 fail · 1 skip. Codex 2라운드 "문제 없음"
- DB: 스키마·DDL·엔티티 매핑 변경 없음(마이그레이션 불필요). 수정 후 쌓이는 전역 작업 감사 행의 COMPANY_IDX·COMPANY_NAME 값만 바뀌고 기존 행은 그대로. 감사 로그를 COMPANY_IDX 로 집계하는 외부 소비처가 있으면 SUPER 전역 작업이 0 쪽으로 모인다
- 남은 것: 읽기 감사 DATA_VIEW 는 SUPER 가 X 선택 중 /companies/Y 상세를 열면 "고객사 상세 조회 (ID: Y)" 가 X 로 남는다(값은 없음, 범위 밖)

## 보안 Medium 7건 (2026-11-11, 브랜치 fix/medium-security)
결정(사용자): 85 → 라이선스·앱서버가 남은 고객사는 삭제 차단, 80 → 비밀번호 변경의 현재 비밀번호 오입력을 로그인 실패로 셈(세션 유지), 103 → 아이디의 제어문자만 거절(가입·운영자 등록 폼), 106 → 마스크로 시작하는 변형 값은 폼 오류. 102·101·78 은 기술 결정.
- [x] 102: 운영자 화면에서 승인대기·거절 상태로 **바꾸거나 등록**하는 것을 막는다(기존 "가입 상태에서 바꾸기" 차단의 반대 방향). 다른 폼의 STATUS 허용값은 운영 DB 값 확인 전이라 그대로
- [x] 85: CompanyService.beforeDelete 에 라이선스·앱서버 건수 추가
- [x] 106: 마스크 접두 변형 값은 폼 오류, 앞뒤 공백·개행 붙은 마스크는 미변경
- [x] 101: 운영자 수정·비밀번호 변경이 대상 행을 잠그고 읽는다(SELECT FOR UPDATE)
- [x] 103: 가입·운영자 등록 userId 에 제어문자가 있으면 폼 오류
- [x] 78: 같은 계정 로그인 시도를 행 잠금으로 직렬화하고 실패 집계를 같은 트랜잭션에서 한다
- [x] 80: 현재 비밀번호 오입력을 LoginAttemptService.onFailure 로 센다 + WARN 로그
- [x] 검증: 건별 실패 테스트 먼저(80 은 컴파일 실패로 시작), 전체 1028 · 0 fail · 1 skip(1005 + 23). 78 은 Oracle 통합 테스트로 동시 오입력 12건 → 수정 전 12건 모두 대조, 수정 후 5건 대조 · 7건 잠금. Codex 3라운드(1: 없는 계정 로그인이 rollback-only 로 500 → noRollbackFor, 2: 선행 잠금 조회 오류가 500 → 인증 예외로 감쌈) → "문제 없음"
- 설정 변경: application.yml 에 InitializeUserDetailsBeanManagerConfigurer 로그 레벨 ERROR 1줄(provider 빈을 일부러 쓸 때 Spring Security 가 안내하는 방법). DB 스키마 변경 없음, 85 용 건수 조회 쿼리 2개 추가
- 범위 밖으로 남긴 것: 다른 폼(Company·Appid·Fido2CredentialParams)의 STATUS 허용값(운영 DB 값 확인 필요), 로그·감사 메시지 쪽 개행 정제(103 의 나머지 경로)

## 다음
- 운영 확인(88): 운영·QA 에 `FIDO_ADMIN_EXTERNAL_ALLOWED_CIDRS` 가 설정돼 있는지 — 76 수정으로 이제 실제로 동작한다. 비어 있으면 /api/svc·/external 이 누구에게나 열려 있다
- 별도 성능 과제(DBA 확인 필요): 33, 42, 44 — 목록 SELECT 에서 안 쓰는 큰 컬럼 제외
- 보안: 74 (75·76·84·107·109·111 완료, Medium 78·80·85·101·102·103·106 완료, 88 은 운영 확인) — 비밀번호 저장 형식(KBFIDO 스키마·타 시스템 호환)이라 별도 합의 과제
- 103 나머지(로그 개행 정제) 완료. 남은 103 범위 밖: 다른 폼 STATUS 허용값(운영 DB 값 확인 필요)
- 정리 항목(@ConfigurationProperties·basePath 보일러플레이트·FQN import)은 Kotlin 전환 뒤 실익 없음으로 닫음: @Value 4곳뿐, basePath 는 이미 CrudController/ReadOnlyController 로 모임, 남은 java.util.Set.of 등은 Java 동작(불변·null 거부) 유지를 위한 의도
- 보고만(74~222): 별도 과제. 우선순위 76 → 88·107·109·111 → 75·84 → 74

## 보안 103 나머지 — 로그 개행 정제 (2026-11-12, 브랜치 fix/log-injection)
결정(기술): 저장되는 감사 메시지(CCFA_AUDIT_LOG.MESSAGE)는 바꾸지 않는다(저장 데이터 형식 유지). 텍스트 로그에 들어가는 외부·사용자 값만 한 줄로 만든다. AAID 는 규격상 제어문자가 나올 수 없으므로 JSON 업로드에서 거절한다.
- [x] common/LogText.oneLine: 제어문자(Cc)·U+2028·U+2029 → "_" (LoginFailureHandler.forLog 가 이것을 쓴다)
- [x] FidoClientRegistrationController: serverName·fsurl·serverurl·serverCode 로그 정제(인증 없는 서버 간 API, fsurl 은 방화벽이 검사하지 않는 파라미터)
- [x] AuditLogger·AuditLogWriter 실패 로그의 message 정제
- [x] FidoReloadListener reason·servercode·serverurl·detail 정제, CrudService CREATE/UPDATE/DELETE id 정제
- [x] CriteriaMetadataParser: aaid 안쪽 제어문자면 INVALID
- [x] 검증: 실패 테스트 먼저(새 테스트 11건, 수정 전 6건 실패 확인), 전체 1039 · 0 fail · 1 skip. Codex 2라운드(1: reload 실패 로그의 serverurl·detail 이 기존 oneLine(CR/LF 만)을 거쳐 NEL·U+2028 이 남음 → 출력 지점에 LogText 덧씌움) → "문제 없음"
- 확인: Spring Security 6.5 StrictHttpFirewall 은 경로·헤더의 제어문자는 막지만 파라미터 값은 검사하지 않는다(기본 predicate true) — 파라미터로 들어온 값은 직접 정제해야 한다
- DB 영향 없음: 스키마·저장값·감사 메시지·무결성 해시 그대로. AAID JSON 업로드만 안쪽 제어문자면 "유효하지 않은 metadata 형식입니다. (aaid)" 로 거절
