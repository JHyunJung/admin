# FIDO Admin 1부 — 기반 구현

Plan: docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md
Spec: docs/superpowers/specs/2026-09-16-fido-admin-design.md
Branch: feature/part1-foundation

각 태스크: 구현자 → 스펙 리뷰 → 코드 품질 리뷰 → Codex 리뷰(랜딩 게이트)

- [x] Task 1: Gradle 프로젝트 스캐폴드와 부팅 확인
- [x] Task 2: 로컬 검증용 Docker Oracle 스키마·시드
- [x] Task 3: SHA-256 비밀번호 인코더
- [x] Task 4: 엔티티 39개와 ERD 정합성 테스트
- [x] Task 5: 로그인 사용자 모델, TenantContext, 감사 로그
- [x] Task 6: Spring Security 로그인·잠금·역할
- [x] Task 7: 레이아웃, 메뉴, 오류 페이지, 예외 핸들러
- [x] Task 8: 공통 CRUD 기반
- [x] Task 9: 참조 CRUD 화면 — 고객사
- [x] Task 10: 참조 조회 화면 — 감사 로그
- [x] Task 11: 대시보드
- [x] Task 12: 내 비밀번호 변경
- [x] Task 13: Testcontainers 통합 테스트
- [x] Task 14: README와 1부 최종 검증

## Review (2026-09-17)

1부 기반 14개 태스크 완료. 94개 테스트 통과(실패 0, 스킵 0), 통합 테스트 4건 실제 실행.

### 각 태스크 3중 게이트 통과 결과
| Task | 커밋 | Codex 최초 | 조치 |
|---|---|---|---|
| 1 스캐폴드 | e9a30c7 | PASS | - |
| 2 Docker Oracle | 9950be4 | FAIL 2 | 864a1b4 |
| 3 SHA-256 | b118fa8 | NITS | 24f946c |
| 4 엔티티 39 | c5a918e | PASS | - |
| 5 인증·감사 | 528b209 | FAIL 4 | f74d52c, 9df4319 |
| 6 Security | a33fbb0 | FAIL 4 | a90e03d, cbf0720 |
| 7 레이아웃 | 2f2cc18 | PASS | - |
| 8 CRUD 기반 | d864051 | FAIL 5 | c087484, 48813d1, dcbe54b |
| 9 고객사 CRUD | 5be75d6 | FAIL 3 | 698250b |
| 10/11/12 병렬 | abcba2c, 7d38858, b042b34 | FAIL 2 | 36187f1, f2306f4 |
| 13 통합 테스트 | 90ccfb9 | FAIL 3 | 6bc1fed |
| 14 README·검증 | a3f9c66 | FAIL 2 | b70b514 |

### 최종 실측 검증
- clean test 94/94, bootRun 부팅 성공
- SUPER: 대시보드·고객사·감사로그·비밀번호변경 전부 200
- COMPANY(kbadmin): /companies /licenses /managers /system/props 403, 사이드바 SUPER 메뉴 0건
- 테넌트 격리 실측: 감사로그 11행 vs 20행, 대시보드 562 vs 604
- ?companyIdx=2 변조 시 응답 동일(CSRF 토큰 외 차이 없음)
- 외부 자원 호출 0건, JS alert/confirm/prompt 0건

### 2부 시작 전 처리 필요
- 할당형 PK 화면(CCFA_SYSTEM_INFO, CCFA_ERROR_TABLE, CCFA_FIDOCLIENT,
  FIDO2_DEMO_ACCESS_CODE, CCFA_FDS_POLICY) 만들기 전 create() 를 persist 기반
  삽입 전용 경로로 전환. 현재는 save() 가 MERGE 로 동작해 기존 행을 덮어쓸 수 있다.
- 운영자·사용자 화면은 toListView/toDetailView 를 반드시 구현해 USER_PW,
  PUBKEY, CERTIFICATE 를 제외하거나 앞 16자만 남긴다.

### 수용한 잔여 위험
- IPv6 절단(IP VARCHAR2(15)) — 스키마 무변경 제약, 설계 3.5 에 명시된 동작
- salt 없는 SHA-256 — 설계 3.4/12, 범위 밖
- 고객사 삭제 TOCTOU, 테넌트 재배정 경쟁 — ERD 에 FK 없음, 관리자 소수 전제
- 로그인 실패 타이밍 차이 — 사내망 전제

# FIDO Admin 2부 — 업무 화면

Plan: docs/superpowers/plans/2026-09-17-fido-admin-part2-screens.md
Branch: feature/part2-screens

각 태스크: 구현자 → 스펙 리뷰 → 코드 품질 리뷰 → Codex 리뷰(랜딩 게이트)

- [x] Task 1: 공통 기반 보강 (AssignedIdCrudService, validate 훅, JsonPretty, CRITERIA·FIDO2 SUPER 전용)
- [x] Task 2: FDS 정책
- [x] Task 3: 라이선스
- [x] Task 4: 운영자 (+잠금 해제)
- [x] Task 5: 앱 ID
- [x] Task 6: 앱 서버
- [x] Task 7: 사용자 (조회+상태 변경)
- [x] Task 8: 챌린지
- [x] Task 9: 서명
- [x] Task 10: 거래 해시
- [x] Task 11: 거래 확인
- [x] Task 12: 인증기기 기준
- [x] Task 13: FIDO2 메타데이터
- [x] Task 14: FIDO2 크리덴셜 파라미터
- [x] Task 15: FIDO2 데모 접근코드
- [x] Task 16: FIDO 로그
- [x] Task 17: 예외 로그
- [x] Task 18: 메일/SMS 큐
- [x] Task 19: 2부 최종 검증

## Review (2026-09-17)

- clean test: 총 260개, 실패 0, 스킵 0 (통합 테스트 실제 실행 여부: 예 — RepositoryIntegrationTest 4건, Docker Oracle 대상)
- SUPER 경로 18개: 전부 200 (예외 없음)
- COMPANY 경로: 200 12개 / 403 6개 (표와 일치 — `/licenses` `/managers` `/criteria` `/fido2/metadata` `/fido2/credential-params` `/fido2/demo-access-codes` 403, 나머지 200)
- 테넌트 격리: /users tester 0건, /users user001 1건 이상, /logs/fido SN-0003 0건, ?companyIdx=2 변조(logs/fido, users) 무시(둘 다 0건 유지), /users/3 404
- 브라우저 체크리스트 10항목: curl 로 기능 등가 검증(HTTP 코드·플래시 문구·감사 로그·DB 상태) 완료. 시각 확인은 2026-09-17 Playwright(Chromium, curl 로 받은 세션 쿠키 주입)로 완료: `/users/1` 상태 변경 모달 "상태를 변경하시겠습니까?"/버튼 "변경"(btn-primary) → 확인 후 플래시 "상태가 변경되었습니다." 와 배지 O→X→O, `/managers/2` 잠금 해제 모달 "잠금을 해제하시겠습니까?"/"해제"(btn-primary) → 플래시 "잠금이 해제되었습니다.", `/companies/1` 삭제 모달 "정말 삭제하시겠습니까?"/"삭제"(btn-danger, 취소로 닫힘). 네이티브 alert/confirm/prompt 0건(page.on('dialog') 감시), 콘솔 JS 오류 0건(favicon.ico 404 만 있음), 감사 로그에 `USERINFO STATUS 1 O->X`, `X->O`, `CCFA_MANAGER UNLOCK kbadmin` 기록 확인.
  - (curl 로 완료한 기능 검증 내역)
  - FDS 정책 중복 등록 시 LOCK TABLE 경로 정상 동작(에러 없음, 기존 행 보존)
  - 운영자 등록→5회 실패 잠김→잠금해제→재로그인 성공, 감사 로그 CREATE/UNLOCK 확인
  - 운영자 빈 비밀번호 검증 메시지 확인
  - 데모 접근코드 중복(LOCK TABLE 경로) 및 신규 등록 확인
  - 라이선스 고객사명 표시, CLOB 목록 제외/상세 노출(criteria, fido2/metadata), JSONDATA pretty-print 확인
  - 사용자 상태 변경 O→X→O, 감사 로그 STATUS 2건(kbadmin) 확인
  - 앱 ID 강제 COMPANY_IDX, 사이드바 메뉴 제한, /fido2/metadata 403 확인
- 외부 자원 호출 0건, JS alert/confirm/prompt 0건

### 3부 시작 전 처리 필요
- 브라우저 시각 확인: 2026-09-17 Playwright 로 완료(위 2부 Review 참조). 남은 것 없음.

### 수용한 잔여 위험
- IPv6 절단(IP VARCHAR2(15)) — 스키마 무변경 제약, 설계 3.5 에 명시된 동작
- salt 없는 SHA-256 — 설계 3.4/12, 범위 밖
- 고객사 삭제 TOCTOU, 테넌트 재배정 경쟁 — ERD 에 FK 없음, 관리자 소수 전제
- 로그인 실패 타이밍 차이 — 사내망 전제

# FIDO Admin 3부 — 시스템 화면과 최종 검증

Plan: docs/superpowers/plans/2026-09-17-fido-admin-part3-system.md
Branch: feature/part3-system

- [x] Task 1: 시스템 설정 (복합키)
- [x] Task 2: 시스템 정보
- [x] Task 3: 에러 코드
- [x] Task 4: FIDO 서버
- [x] Task 5: 어드민 기준
- [x] Task 6: 메뉴 정의
- [x] Task 7: 코드 그룹/코드
- [x] Task 8: 필드 정의
- [x] Task 9: 최종 검증과 인도물

## Review (2026-09-17)

- clean test: tests=386 skipped=0 failures=0 errors=0 (통합 테스트 실제 실행 여부: 예 — `AssignedIdInsertIntegrationTest` 2건 신규 + `RepositoryIntegrationTest` 4건, Docker Oracle(Testcontainers, gvenzl/oracle-free:23-slim) 대상. `AssignedIdCrudService.insert()` 의 `LOCK TABLE CCFA_SYSTEM_INFO IN EXCLUSIVE MODE` 가 실제 Oracle 에서 오류·타임아웃 없이 수행됨을 확인)
- SUPER 29개 경로 전부 200 (`SUPER non-200: 0`), COMPANY `/system/*` 8개 전부 403 (`COMPANY non-403: 0`)
- 브라우저 확인 목록 12항목: 브라우저 대신 curl 로 기능 동등 검증 완료(HTTP 코드·플래시 문구·감사 로그·DB 상태 실측). 11항목은 브리핑과 완전히 일치, **1항목 문구 불일치**:
  - 시스템 설정 등록 거부 문구, `PW_FAIL_LIMIT@0` 값 6→5 되돌리기, `VERSION`/`IT_MANUAL` 등록·삭제, 에러 코드 `1200` 거부·`9999` 등록/수정/삭제, FIDO 서버 `FIDO01` 거부·`FIDO03` 상태 기본값 ON, 어드민 기준 JSON 검증·pretty-print, 메뉴 부모 select 자기 제외·`최상위` 존재·하위 메뉴 있는 메뉴 삭제 차단 플래시, 코드 그룹 `hold` 추가·삭제·코드 남은 그룹 삭제 차단, 필드 정의 코드 그룹 select·목록 그룹명, 감사 로그 CREATE/DELETE 4건 확인, kbadmin 사이드바 시스템 그룹 미노출·`/system/menus` 403 — 모두 curl 실측대로 통과.
  - **불일치**: 키에 `/` 를 넣어 등록하면 브리핑은 "키에 '/' 는 쓸 수 없습니다."를 기대했으나, 실제 메시지는 `SystemPropForm`/`SystemInfoForm`의 `@Pattern(regexp = "[A-Za-z0-9._-]+")` 검증 문구인 "키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다."이다. 동작(등록 거부)은 기대대로지만 문구가 다르다. 코드는 수정하지 않았다(범위 밖 지시에 따름).
  - 실제 브라우저(시각) 확인: 2026-09-17 Playwright(Chromium, curl 세션 쿠키 주입)로 완료. 시스템 8개 화면 모두 목록 200(행 1~3건)·`등록` 링크·`/new` 폼 200·첫 행 상세(`/system/props/PW_FAIL_LIMIT@0`, `/system/info/BUILD_DATE`, `/system/error-codes/1200`, `/system/fido-clients/FIDO01`, `/system/criteria/1`, `/system/menus/1`, `/system/options/1`, `/system/fields/1`)·삭제 모달("정말 삭제하시겠습니까?"/"삭제", 코드 그룹은 "그룹을 삭제하시겠습니까? 하위 코드가 있으면 삭제되지 않습니다.") 확인, 취소로 닫힘. 네이티브 dialog 0건, 콘솔 JS 오류 0건(favicon.ico 404 만 있음). 2부의 `/users/1`, `/managers/2`, `/companies/1` 확인도 같은 세션에서 완료(2부 Review 참조).
- 정적 자원 규칙: `grep -rn "alert(\|confirm(\|prompt(" src/main/resources` 출력 없음(종료 코드 1), `grep -rn "https\?://" src/main/resources/templates | grep -v "thymeleaf.org\|w3.org"` 출력 없음(종료 코드 1) — 둘 다 기대대로.
- bootRun: `docker/docker-compose.yml` Oracle 이미 healthy 상태에서 `./gradlew bootRun --args='--spring.profiles.active=local'` 기동, `/tmp/fido-admin-bootrun.log` 에 `Started FidoAdminApplication in 2.732 seconds` 확인. 검증 후 `pkill -f 'FidoAdminApplication'` 로 종료, 포트 8080 반환 확인.
- 남은 위험: 시퀀스 이름 `<TABLE>_SEQ` 임시(README 절차대로 교체 전 운영 INSERT 보장 없음), 1부 Review 의 수용 위험 유지, 시스템 설정 등록 폼의 문자 제약 오류 문구가 설계 문서 문구와 다름(동작은 일치, 문구만 차이 — 위 참고).

## 디자인 테마 적용 (2026-09-17)

사용자 선택: "A(밝은 SaaS 톤)로 가되 사이드바만 B(남색)처럼".

- [x] `static/css/admin.css` 재작성 — Bootstrap CSS 변수 재정의(본문 배경 `#f4f6fb`, 12px 모서리, 파란 액센트 `#2563eb`), 남색 사이드바(`#1e2a44`, 활성 메뉴 왼쪽 액센트 바), 카드형 목록·상세·검색 폼, 연한 배경 배지, 대문자 표 헤더. 외부 자원·마크업 변경 없음(템플릿 무수정).
- [x] `application.yml` 에 `spring.web.resources.chain.strategy.content` 추가 — `max-age: 1d` 캐시 때문에 배포 후 옛 CSS 가 보이는 문제를 콘텐츠 해시 URL(`/css/admin-<md5>.css`, `/js/admin-<md5>.js`, WebJars 포함)로 해결. `@{...}` 링크가 자동 변환되며 원본 경로도 계속 200.
- [x] 검증: `./gradlew test` 389건 통과, bootRun 재기동 후 로그인·대시보드 HTML 에서 해시 경로 확인 및 해당 경로 200, 새 브라우저 컨텍스트(Playwright)에서 목록·상세·대시보드·폼·로그인 스크린샷으로 테마 적용 확인.
- 미적용(선택지로 남김): 메뉴 아이콘, 상세 화면 카드 분할, 대시보드 카드 색, 번들 폰트, favicon.

## 사이드바 메뉴 아이콘 (2026-09-17)

- [x] `org.webjars.npm:bootstrap-icons:1.13.1` WebJar 추가 — 외부 CDN 금지 조건 유지(설계 §정적 자원). `base.html` 에 `@{/webjars/bootstrap-icons/font/bootstrap-icons.min.css}` 링크 추가.
- [x] `MenuItem` 에 `icon` 컴포넌트 추가(Bootstrap Icons 클래스명), `MenuRegistry.ALL` 29개 메뉴에 각각 의미에 맞는 아이콘 지정.
- [x] `sidebar.html` 을 `<i class="bi bi-*">` + 제목 `<span>` 구조로 변경, `admin.css` 에 아이콘 너비 고정(1.1rem)·색(`--fa-navy-muted`, 활성/호버 시 흰색) 규칙 추가.
- [x] 테스트 2건 추가: `MenuRegistryTest.everyMenuHasAnIcon`(모든 메뉴가 `bi-` 접두 아이콘 보유), `LayoutWebTest.sidebarRendersMenuIcons`(아이콘 CSS 링크·렌더된 아이콘 클래스). 전체 391건 통과, 실패 0.
- [x] 브라우저 확인: SUPER 29개·COMPANY 14개 메뉴 모두 아이콘 렌더, 아이콘 폰트 로드 성공(`document.fonts.check`), 활성 메뉴 아이콘 흰색 전환, 콘솔 오류 0건. 아이콘 폰트(woff2, 134KB)도 콘텐츠 해시 경로로 200 서빙 확인.
