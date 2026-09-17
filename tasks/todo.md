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
- 브라우저 체크리스트 10항목: curl 로 기능 등가 검증(HTTP 코드·플래시 문구·감사 로그·DB 상태) 완료. 시각 확인(Bootstrap 확인 모달 문구·버튼 "삭제/변경/해제", 네이티브 alert/confirm 미출현, 배지·플래시 렌더링)은 **미완료 — 수동 확인 필요**: `/users/1` 상태 변경, `/managers/2` 잠금 해제, `/companies/1` 삭제 버튼 순으로 확인.
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
- 브라우저 시각 확인 **미완료**: Bootstrap 확인 모달 문구·버튼("삭제"/"변경"/"해제"), 네이티브 alert/confirm 미출현, 배지·플래시 렌더링을 실제 브라우저로 확인해야 한다.
  절차: `/users/1` 상태 변경 → `/managers/2` 잠금 해제 → `/companies/1` 삭제 버튼 순으로 확인.

### 수용한 잔여 위험
- IPv6 절단(IP VARCHAR2(15)) — 스키마 무변경 제약, 설계 3.5 에 명시된 동작
- salt 없는 SHA-256 — 설계 3.4/12, 범위 밖
- 고객사 삭제 TOCTOU, 테넌트 재배정 경쟁 — ERD 에 FK 없음, 관리자 소수 전제
- 로그인 실패 타이밍 차이 — 사내망 전제
