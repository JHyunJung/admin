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

## 상세 화면 카드 분할 (2026-09-17)

- [x] `fragments/detail-section.html` 추가 — 제목 붙은 카드 안에 이름/값 표를 담는 재사용 구획. 쓰는 쪽은 `~{this :: xxxRows}` 로 행 묶음을 넘긴다.
- [x] 항목이 많은 상세 화면 10개를 구획으로 분할:
  - 사용자(15) 기본 정보/인증기기 정보/상태·이력, 고객사(19) 기본 정보/연락처/계약·한도/비고·이력, 운영자(16) 기본 정보/계정 상태/알림·이력,
    라이선스(13) 기본 정보/연락처/라이선스·이력, FDS 정책(10) 기본 정보/AND 조건/OR 조건/이력, 인증기기 기준(17) 식별 정보/인증 정책/원본 데이터·이력,
    서명(11) 기본 정보/서명 데이터, 메일·SMS 큐(10) 기본 정보/메일/SMS, 메뉴 정의(13) 기본 정보/화면 연결/표시 옵션, FIDO2 메타데이터(23) 식별 정보/인증기기 사양/보안 속성/인증서·자원.
  - 항목이 9개 이하인 나머지 17개 화면은 한 카드 그대로 둔다.
- [x] `admin.css` 에 `.fa-section`(구획 간격), `.fa-section-title`(구획 제목) 규칙 추가.
- [x] 테스트 2건 추가: `UserControllerWebTest.detailSplitsRowsIntoTitledSections`(구획 제목과 전 항목 유지), `detailRendersEachRowExactlyOnce`(각 행 정확히 1회 렌더). 전체 393건 통과, 실패 0.
- [x] 함정과 해결: `th:fragment` 를 단 `<tbody>` 를 `<main>` 안에 두면 카드 밖에 평문으로 **중복 출력**된다(브라우저 확인에서 발견, 스크린샷으로 확인). 프래그먼트 정의를 `<main>` 밖으로 옮겨 해결했고, 중복을 잡는 회귀 테스트를 남겼다.
- [x] 검증: 10개 화면 전부 HTTP 200, 구획 수 의도대로(2~4), 중복 0건, 렌더된 `<th>` 항목 수가 변경 전 템플릿과 전부 일치(누락 0). 콘솔 오류 0건(기존 favicon 404 제외).

## favicon (2026-09-17)

- [x] 크로스서트 공식 사이트(https://www.crosscert.com/)와 같은 아이콘 사용 — `favicon.ico`(32x32) 와 `images/favicon.png`(192x192) 를 받아 `static/` 에 두었다. 외부 요청 없이 우리 서버가 서빙한다.
- [x] `layout/base.html`, `login.html`, `error/{403,404,500}.html` 다섯 곳 head 에 `rel="icon"`(ico/png) 과 `rel="apple-touch-icon"` 링크 추가. 다른 정적 자원과 같이 `th:href="@{...}"` 를 써서 콘텐츠 해시 버전닝을 받는다.
- [x] **보안 설정 수정**: `SecurityConfig` 의 permitAll 목록에 `/favicon.ico` 만 있어 PNG 와 해시 경로(`favicon-<md5>.ico`)가 302 로 로그인에 리다이렉트됐다(로그인 화면에 아이콘이 안 나오는 상태). `/favicon.png`, `/favicon-*.ico`, `/favicon-*.png` 를 추가했다.
- [x] 테스트 2건 추가: `LayoutWebTest.pagesLinkFavicon`(링크 존재), `anonymousCanFetchFavicon`(로그인 전 접근 시 302 가 아님 — 위 회귀를 실제로 잡는 것을 임시 되돌리기로 확인). 전체 394건 통과, 실패 0.
- [x] 검증: 네 경로(`/favicon.ico`, `/favicon.png`, 해시 ico/png) 모두 인증 없이 200, 타입·크기 정상. 브라우저 콘솔 오류 0건 — 그동안 남아 있던 `/favicon.ico` 404 가 해소됐다.
- 참고: 아이콘은 크로스서트 상표 이미지이며 사내 관리 도구용으로 저장소에 포함했다.

## 대시보드 카드 색 (2026-09-17)

- [x] 요약 카드 4개를 지표별 색으로 구분 — 위쪽 3px 색 띠와 같은 색 아이콘: 인증(파랑 #2563eb, `bi-shield-check`), 등록(초록 #16a34a, `bi-person-plus`), 해지(주황 #f59e0b, `bi-person-dash`), 거래확인(보라 #7c3aed, `bi-check2-square`). 인증·등록 색은 차트 선 색과 같다.
- [x] 숫자를 1.5rem 굵게 키우고 실패 수치만 빨강으로 남겼다. 카드 색은 띠·아이콘에만 쓰고 숫자는 검게 두어 가독성을 지켰다.
- [x] `admin.css` 에 `.fa-stat` 계열 규칙 추가(카드별 색은 `--fa-stat-color` 변수 하나로 갈아끼운다).
- [x] 테스트 1건 추가: `DashboardControllerWebTest.summaryCardsAreColorCoded`(카드별 클래스·아이콘·수치 유지). 전체 396건 통과, 실패 0.
- [x] 검증: 카드 4개 높이 모두 114px 로 동일, 좁은 화면(430px)에서 2열로 접히며 색 구분 유지, 콘솔 오류 0건.

## 번들 폰트 Pretendard (2026-09-17)

- [x] **설계서 문구 변경**: `docs/superpowers/specs/2026-09-16-fido-admin-design.md` 1.1 고정 제약의 "폰트는 시스템 폰트 스택" → Pretendard 한글 상용 서브셋 번들 + 서브셋 밖 글자는 시스템 폰트가 받음. 외부 호출 금지 원칙은 그대로다.
- [x] WebJar 조사 결과 Maven Central 에 Pretendard·Noto Sans KR WebJar 가 없어(둘 다 404) npm tarball 에서 받아 `static/fonts/` 에 직접 넣었다. 라이선스 OFL-1.1, `fonts/LICENSE.txt` 동봉(OFL 의무).
- [x] 무게 3개(Regular 400 / Medium 500 / SemiBold 600–700)의 **정적 서브셋** woff2 사용, 합계 786KB. 대안 비교: 전체 woff2 무게당 ~780KB(3무게 2.3MB), 가변폰트 2MB, 동적 서브셋 92조각·Regular만 1.1MB → 서브셋이 가장 유리.
- [x] `admin.css` 에 `@font-face` 3개 추가하고 `--bs-body-font-family` 맨 앞에 Pretendard 배치. 시스템 폰트 스택은 뒤에 남겨 서브셋 밖 글자를 받게 했다.
- [x] `SecurityConfig` permitAll 에 `/fonts/**` 추가(favicon 때와 같은 302 문제를 테스트가 먼저 잡았다).
- [x] 테스트 1건 추가: `LayoutWebTest.anonymousCanFetchBundledFont`. 전체 397건 통과, 실패 0.
- [x] 검증:
  - **글리프 커버리지 실측** — 템플릿·자바 소스의 한글 429자를 모두 추출해 서브셋(한글 2,780자)과 대조: **누락 0자**.
  - 서브셋 밖 글자 처리 — 희귀 한자(龘齉齾) 렌더 폭이 0 이 아님(=fallback 이 정상 수신, 두부 현상 없음).
  - 폰트 3개 모두 인증 없이 200(`font/woff2`), 브라우저 네트워크에서 콘텐츠 해시 경로로 수신 확인(CSS 내부 상대경로도 Spring 이 해시로 변환).
  - body·제목·사이드바·표·버튼·`.form-select` 등 전 요소가 Pretendard 로 계산됨(폼 요소 포함), 400/500/600/700 네 굵기 모두 로드. 콘솔 오류 0건.
- 비용: 저장소에 786KB 증가(기존 .git 8.5MB). 첫 방문 시 폰트 전송량은 Bootstrap Icons(134KB) 포함 약 920KB, 이후 캐시된다.

## 운영자 가입 신청·승인 (2026-09-17)

Spec: `docs/superpowers/specs/2026-09-17-fido-admin-signup-design.md`
Plan: `docs/superpowers/plans/2026-09-17-fido-admin-signup.md`

사용자가 스스로 가입을 신청하고 슈퍼 관리자가 승인해야 로그인할 수 있는 흐름. 스키마는 건드리지 않았고
신청 행은 `CCFA_MANAGER` 에 `STATUS = 승인대기`, `COMPANY_IDX = -1`(미배정) 로 저장한다. 로그인 차단은
기존 `ManagerUserDetailsService` 의 상태 검사가 그대로 처리한다 — 새 차단 경로를 만들지 않았다.

- [x] Task 1: (선행 수정) `COMPANY_IDX` 가 null 이면 슈퍼 관리자가 되는 결함 차단
- [x] Task 2: 비밀번호 정책·상태 상수 공통화 (`PasswordPolicy`, `ManagerStatus`, `SignupPolicy`)
- [x] Task 3: 가입 신청 서비스 (`SignupService.apply()`)
- [x] Task 4: 가입 신청 화면 (`/signup`, 로그인 불필요)
- [x] Task 5: 승인·거절 서비스 (권한 상승 차단 4중 가드)
- [x] Task 6: 가입 승인 화면 (`/signups`, SUPER 전용)
- [x] Task 7: 인증 흐름 통합 검증과 문서 갱신

### 설계 검토에서 먼저 잡은 것

- [x] **기존 권한 상승 결함 (설계 3.3)** — 가입 기능과 무관하게 이미 있던 결함이다.
  `ManagerUserDetailsService` 가 `COMPANY_IDX` 를 `long` 으로 언박싱하기 전에 null 을 검사하지 않아,
  `COMPANY_IDX` 가 null 인 행이 0(= SUPER)으로 해석될 수 있었다. 외부 입력이 계정 행을 만들기 **전에**
  고쳐야 하는 문제라 다른 무엇보다 먼저 처리했다. 지금은 거부되며 고객사 조회조차 하지 않는다.
  (`ManagerUserDetailsServiceTest`, `NullCompanyIdxLoginFlowTest`)

### 의도적으로 감수한 것

- [x] **계정 열거(account enumeration) 트레이드오프 — 사용자가 명시적으로 선택했다.**
  아이디 중복 시 "이미 사용 중인 아이디"라고 그대로 알려준다. 이 응답으로 특정 아이디의 존재 여부를
  확인할 수 있다. 모호한 문구로 감추는 대신 신청자가 곧바로 다른 아이디를 고를 수 있는 쪽을 택했다.
  사내망 전용이고 신청 자체가 승인 없이는 아무 권한도 주지 않기 때문이다.
  **재검토 조건: 이 화면이 사내망 밖(인터넷)에 노출되는 순간.** 그때는 응답을 모호하게 바꾸고
  아래의 rate limit 을 함께 넣어야 한다. 둘 중 하나만 해서는 효과가 없다.

### 구현 중 잡은 결함

- [x] **`@ByteSize` vs `@Size`** — 이 스키마는 바이트 의미(`NLS_LENGTH_SEMANTICS=BYTE`, `AL32UTF8`)라
  한글 1자가 3바이트다. 글자 수 기준인 `@Size` 를 쓰면 검증을 통과한 뒤 `ORA-12899` 로 저장에 실패한다.
  즉 한글 입력에서만 터지는 결함이다. 가입 폼의 길이 제한을 전부 `@ByteSize` 로 맞췄다.
  (`SignupControllerWebTest.rejectsNameOverByteLimit` / `rejectsReasonOverByteLimit`,
  `SignupApprovalEscalationIntegrationTest.longKoreanRejectReasonFitsInColumn` 은 실제 Oracle 로 확인한다)

아래 세 건은 **다른 리뷰어들이 모두 승인한 뒤 Codex 리뷰 게이트가 잡아냈다.** 기록해 둘 가치가 있다 —
정상 경로만 보면 셋 다 보이지 않는다.

- [x] **공백 결함(가장 무거웠다)** — 가입은 아이디를 trim 하지 않고 저장하는데 로그인은 trim 한다.
  `" hong"` 으로 신청하면 그대로 저장되고, 로그인은 `"hong"` 을 찾으므로 영영 일치하지 않는다.
  승인까지 정상적으로 끝난 계정이 **영구히 사용 불가**가 되는데, 화면 어디에도 이상 징후가 없다.
  신청 시점에 trim 하도록 고쳤고 공백만으로 이루어진 아이디도 거부한다. 중복 검사도 trim 후 값으로 한다.
  (`SignupControllerWebTest.trimsSurroundingWhitespaceFromUserId`,
  `detectsDuplicateDespiteSurroundingWhitespace`, `rejectsWhitespaceOnlyUserId`)
- [x] **없는 고객사로도 승인됐다** — 승인은 `companyIdx` 가 0(SUPER)이나 -1(미배정)인지는 봤지만
  그 고객사가 **실재하는지**는 확인하지 않았다. 존재하지 않는 IDX 로 승인하면 어느 고객사에도 속하지
  않는 계정이 만들어진다(ERD 에 FK 가 없어 DB 도 막지 못한다). 존재 검사를 추가했다.
  (`SignupApprovalServiceTest.approveRejectsCompanyThatDoesNotExist`,
  `SignupApprovalEscalationIntegrationTest.approveRejectsCompanyThatDoesNotExist`)
- [x] **Enter 키로 확인 모달을 건너뛸 수 있었다** — 거절 사유 입력란에서 Enter 를 치면 폼이 곧바로
  제출되어, 되돌릴 수 없는 처리의 확인 모달이 통째로 생략됐다. 모달을 붙여 놓고도 우회로가 열려 있던 셈이다.
  (`SignupAdminControllerWebTest.rejectFormIsWiredToConfirmModal`)

### 테스트 — 설계서 8장 대조

8장의 모든 항목에 대응하는 테스트가 있다. **단, 동시성 2건은 제외했다(아래 사유).**

| 8장 항목 | 검증하는 테스트 |
| --- | --- |
| `COMPANY_IDX` null 이 SUPER 가 되지 않는가 (3.3) | `ManagerUserDetailsServiceTest.nullCompanyIdxIsRejectedInsteadOfBecomingSuper`, `NullCompanyIdxLoginFlowTest` |
| 신청에 `companyIdx=0`·`status=활성` 을 실어도 무시되는가 | `SignupControllerWebTest.injectedCompanyIdxAndStatusAreIgnored`, `SignupServiceTest.applyForcesPendingStatusAndUnassignedCompany` |
| 승인 시 `companyIdx = 0` 이 거부되는가 | `SignupApprovalServiceTest.approveRejectsSuperCompanyIdx`, `SignupApprovalEscalationIntegrationTest.approveCannotCreateSuperAccount` |
| 이미 `활성` 인 계정에 승인이 적용되지 않는가 | `SignupApprovalServiceTest.approveRejectsAlreadyActiveAccount`, `SignupApprovalEscalationIntegrationTest.approvedAccountCannotBeReassigned` |
| `승인대기` 계정으로 로그인 실패 | `SignupEscalationIntegrationTest.appliedAccountCannotLogInAndIsNotSuper` (실제 Oracle + 실제 인증 코드) |
| 승인 후 로그인 성공, 소속이 지정한 고객사 | `SignupApprovalEscalationIntegrationTest.approvedAccountBecomesCompanyUserNotSuper` |
| `거절` 계정으로 로그인 실패 | `SignupApprovalEscalationIntegrationTest.rejectedAccountCannotBeApproved` |
| 아이디 중복 시 거부 | `SignupControllerWebTest.rejectsDuplicateUserId`, `SignupServiceTest.applyRejectsDuplicateUserId`, `SignupEscalationIntegrationTest.duplicateUserIdIsRejectedOnRealDatabase` |
| 비밀번호 정책 위반 시 거부 | `PasswordPolicyTest`(8건), `SignupControllerWebTest.rejectsWeakPassword` |
| 비밀번호 확인 불일치 시 거부 | `PasswordPolicyTest.rejectsConfirmMismatch`, `SignupControllerWebTest.rejectsConfirmMismatch` |
| 인증 없이 `/signup` 이 열리는가 | `SignupControllerWebTest.formIsPublic` |
| 고객사 계정이 `/signups` 에 접근하면 403 | `SignupAdminControllerWebTest.companyUserIsForbidden` |
| 고객사 사이드바에 가입 승인 메뉴가 없는가 | **Task 7 신규** `LayoutWebTest.companySidebarHidesSignupApprovalMenu` (+ 짝이 되는 `superSidebarShowsSignupApprovalMenu`) |
| 같은 아이디 동시 신청 시 하나만 저장 | **작성하지 않음 — 아래 사유** |
| 같은 신청 동시 승인 시 한 번만 적용 | **작성하지 않음 — 아래 사유** |

- [x] **동시성 2건을 작성하지 않은 이유(정직하게 남긴다).** 이 프로젝트의 통합 테스트는 `@DataJpaTest` 라
  테스트 메서드 전체가 하나의 트랜잭션으로 감싸였다가 롤백된다. 다른 스레드는 그 트랜잭션이 만든 행을
  **볼 수 없다.** 그래서 스레드를 띄워 봐야 경쟁을 재현하지 못하고, 통과하더라도 동시성을 검증한 것이
  아니라 아무것도 검증하지 않은 테스트가 된다. **통과하는데 이유가 틀린 테스트를 남기느니 없는 쪽이 낫다고 판단했다.**
  대신 실제 방어 장치 두 개를 실제 Oracle 로 각각 확인한다:
  `apply()` 의 `LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE` 가 문법 오류·타임아웃 없이 도는 것
  (`SignupEscalationIntegrationTest.duplicateUserIdIsRejectedOnRealDatabase`), 그리고 승인이 행 잠금
  **이후** 상태를 다시 읽어 판단하는 것(`SignupApprovalServiceTest.approveRechecksStatusAfterRowLock`,
  `SignupApprovalEscalationIntegrationTest.approvedAccountCannotBeReassigned` — 후자는 flush + clear 로
  1차 캐시가 아니라 DB 를 읽게 만들어야 의미가 있다). 진짜 동시성 검증이 필요해지면 `@DataJpaTest` 가 아닌
  별도 하네스(커밋되는 트랜잭션 + 실제 커넥션 2개)가 필요하다.

- [x] **Task 7 에서 추가한 테스트 4건** — 8장 대조 중 실제로 비어 있던 두 자리를 채웠다.
  - `LayoutWebTest.companySidebarHidesSignupApprovalMenu` / `superSidebarShowsSignupApprovalMenu` —
    기존 `MenuRegistryTest.signupApprovalMenuIsSuperOnly` 는 레지스트리 **데이터**가 `superOnly` 인지만 본다.
    정작 메뉴가 새는 곳은 렌더된 HTML 이다. 템플릿이 `itemsFor(isSuper)` 대신 `MenuRegistry.ALL` 을 쓰도록
    바뀌면 데이터 테스트는 그대로 통과하면서 메뉴는 노출된다. 실제로 그렇게 바꿔 보니 새 테스트가 실패했고,
    되돌리니 통과했다(= 회귀를 잡는다는 것을 확인했다).
  - `SignupControllerWebTest.loginPageLinksToSignupForm` / `loginPageShowsNoticeAfterApplying` —
    `/signup` 이 살아 있어도 로그인 화면에 링크가 없으면 도달할 길이 없다. 신청 후 돌아오는
    `/login?signup` 의 접수 안내까지 확인해 진입점과 복귀점을 모두 고정했다. 링크를 지워 보고 실패를 확인했다.
  - 나머지 항목은 Task 1~6 에서 이미 덮고 있어 중복해 만들지 않았다.

- [x] 전체 테스트 481건 통과, 실패 0, 건너뜀 0.

### 사람이 판단해야 할 후속 과제 (이번 범위 밖, 그러나 실재한다)

- [ ] **가입 엔드포인트에 rate limit 이 없다.** `/signup` 은 인증 없이 열려 있어 아이디 존재 여부를
  빠르게 훑는 데 쓸 수 있고, 승인대기 행으로 테이블을 채울 수도 있다. 현재 완화책은 **사내망 전용이라는 것뿐**이다.
  위 계정 열거 트레이드오프와 같은 조건에서 함께 재검토해야 한다(인터넷 노출 시).
- [ ] **가입 신청은 감사 기록이 남지 않는다.** `AuditLogger` 가 인증된 주체가 없으면 조용히 아무것도 하지 않기
  때문이다(가입은 익명 호출이다). 승인·거절은 슈퍼 관리자가 로그인한 상태라 정상 기록된다. 신청 이력을
  남기려면 익명 경로용 기록 수단을 따로 만들어야 하며, 이는 감사 로그의 의미를 바꾸는 결정이라 위임하지 않는다.
- [ ] **trim 수정 이전에 만들어진 행은 앞뒤 공백이 붙은 아이디를 가질 수 있다.** 그런 행은 승인해도 로그인되지
  않는다. 운영 DB 에 이미 그런 행이 있는지 확인이 필요하다:
  `SELECT IDX, USER_ID FROM CCFA_MANAGER WHERE USER_ID <> TRIM(USER_ID);`
  데이터 정정이라 코드로 일괄 처리하지 않았다.
- [ ] **`auth/PasswordChangeForm` 이 비밀번호 정규식을 세 번째로 다시 선언하고 있다.** Task 2 에서
  `PasswordPolicy` 로 모았으나 이 폼은 여전히 `@Pattern(regexp = "^(?=.*[A-Za-z])...")` 리터럴을 직접 들고 있다.
  지금은 값이 같아 동작에 차이가 없지만, 정책이 바뀔 때 한 곳만 고치면 조용히 어긋난다. 가입 흐름 밖의
  파일이라 이번 범위에서 건드리지 않았다.
