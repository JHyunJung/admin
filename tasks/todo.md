# 제공 코드 모음 적용 — 미구현분 전체 구현

출처: 사용자 제공 "FIDO Admin — 제공 코드 모음" (2026-09-26)
브랜치: main

## 사전 확인 결과

8개 묶음 중 대부분이 미구현이었다. 확인 방법은 파일 존재 + 식별 문자열 grep.

| 묶음 | 상태 |
|---|---|
| 1. 시퀀스 (Appid) | 시퀀스명만 다름 (`APPID_SEQ` → `SEQ_APPID`) |
| 2. 감사로그 | 파일 3개 전부 없음 (AuditView, AuditChanges, AuditReadInterceptor) |
| 3. 사용자 상세 | 미구현 |
| 4. FIDO 로그 | **구조 변경 필요** (아래 참조) |
| 5. Oracle 문자셋 | 미구현 |
| 6. 메뉴 | 멤버코드 명칭 미반영 |
| 7. AAID 정책 | disabledAaids/changeStatus 등 미구현 |
| 8. 멤버코드 | 템플릿 3종 미반영 |

## 결정 사항 (사용자 확인함)

- **`/criteria` 접근**: COMPANY 역할도 허용. SecurityConfig의 `hasRole("SUPER")`에서
  `/criteria/**`를 뺀다. 고객사별 AAID 토글이 `CCFA_COMPANY_AAID`를 쓰므로
  고객사 맥락이 필수이고, 메뉴도 TENANT 영역으로 옮기는 것과 일관된다.
- **검증**: 컴파일 + 기존 테스트 + 신규 단위 테스트.

## 범위를 벗어나 확인이 필요했던 것

**4번 FIDO 로그**는 제공 조각이 현재 구조와 다르다.
현재: `@Table("FIDO_LOGS")` JPA 엔티티 + `ReadOnlyController` (단일 테이블).
제공: `FIDO_LOGS_YYYYMMDD` 분할 테이블을 JDBC로 조회, URL `/logs/fido/{date}/{id}`.

제공된 조각(검색폼 필드, tableName 헬퍼, 존재확인 SQL, 상세 SQL, 링크 2개)만으로는
동작하지 않는다. 서비스 전체·컨트롤러·목록 템플릿을 새로 써야 한다.
레거시 매퍼 문서(`docs/legacy/mybatis-mappers.md`)의 `log.getList`가 같은 분할
테이블 방식을 쓰므로 방향 자체는 레거시와 일치한다. 제공 조각을 중심으로 나머지를 채운다.

## 태스크

- [x] Task 1: 시퀀스명 정정 (Appid)
- [x] Task 2: Oracle 문자셋 의존성 (build.gradle)
- [x] Task 3: 감사로그 — AuditType 확장, AuditView, AuditChanges
- [x] Task 4: 감사로그 — AuditReadInterceptor, WebMvcConfig 등록
- [x] Task 5: 감사로그 — CrudService 변경 기록, FidoSettingService 변경 기록
- [x] Task 6: AAID 정책 — 서비스 (disabledAaids, changeStatus)
- [x] Task 7: AAID 정책 — 컨트롤러, SecurityConfig, CriteriaRow
- [x] Task 8: AAID 정책 — 템플릿 (list, detail)
- [x] Task 9: 멤버코드 — 메뉴, 템플릿 3종
- [x] Task 10: 사용자 상세 — 기기 목록 링크, CSS
- [x] Task 11: FIDO 로그 — 날짜별 분할 테이블 조회
- [x] Task 12: 신규 테스트 작성
- [x] Task 13: 전체 빌드 + 테스트, 리뷰 섹션 작성

## 리뷰

(아래에 기록)

### 결과 (2026-09-27)

`./gradlew test`: 657건 중 653 통과, skipped 0. 실패 4건은 모두 Oracle Testcontainers 가
필요한 기존 테스트(AuditLogRoundTripTest, LoginLockLoadTest, SelectedTenantWiringTest,
EntityBootTest)로, 작업 전 clean baseline(`git stash`)에서도 같은 4건이 같은 이유로 실패함을
확인했다. 이번 변경과 무관하다.

신규 테스트 5클래스: AuditChangesTest(8), AuditReadInterceptorTest(8),
CriteriaQueryServiceTest(10, 재작성), FidoLogQueryServiceTest(10, 재작성),
MenuRegistryTest(+1).

### 제공 코드와 달리 판단한 것

- **AuditReadInterceptor 의 의존성을 ObjectProvider 로 받는다.** 생성자 주입 그대로 두면
  WebMvcConfig 를 @Import 하는 @WebMvcTest 조각 30여 개가 AuditLogger 빈이 없어 컨텍스트
  자체가 뜨지 않는다(284건 실패). 조회 기록은 부수 작업이라 없으면 건너뛴다.
- **AuditChanges 의 비밀값 판별에 필드명→컬럼명 정규화를 넣었다.** SecretProps.isSecret 은
  컬럼명 규칙(`_PW` 접미)인데 엔티티 필드는 `userPw` 라 그대로는 걸리지 않아 운영자 비밀번호
  해시가 UPDATE 로그에 실린다. 테스트 작성 중 발견해 고쳤다.
- **AuditView.add 를 ReadOnlyController/CrudController.detail 에 넣었다.** "각 상세
  컨트롤러"를 하나씩 손대는 대신 기반 클래스 두 곳 + 자체 상세를 가진 UserController,
  FidoLogController 로 전부 덮는다.
- **FIDO 로그는 서비스·컨트롤러·템플릿을 새로 썼다.** 제공 조각은 날짜별 분할 테이블
  (FIDO_LOGS_YYYYMMDD) 전제였고 기존 구조는 단일 테이블 JPA 였다. 조각을 중심으로 나머지를
  채웠고, 상세 URL 은 `/logs/fido/{date}/{id}` 다. ReadOnlyController 를 상속하지 않는다.
- **/criteria 를 SecurityConfig 의 SUPER 목록에서 뺐다**(사용자 확인). 서비스의
  requireSuperForGlobalTable 재정의만으로는 URL 매처가 여전히 막아 무의미했다.
  메뉴도 TENANT/"FIDO 서버 관리 > AAID(정책)" 으로 옮겼다(제공 코드대로).
- **사용자 상세의 기기 전환 목록은 controller 가 `devices`/`back` 을 실어 준다.** 제공
  템플릿 조각만으로는 모델에 그 값이 없어 동작하지 않았다. credentials 화면 링크에 `back` 을
  실어 왕복이 되게 했다.

### 기존 테스트 정리(lessons.md 2026-09-18 "낡은 셋업 vs 옛 동작 정당화" 기준)

- 옛 동작을 정당화하던 것 → 이름·단언을 다시 씀: `CriteriaQueryServiceTest.companyRoleIsDeniedOnSearchAndGet`
  → `companyRoleCanSearchAndGet`, `CriteriaControllerWebTest.companyRoleIsForbidden` → `companyRoleCanView`,
  `MenuRegistryTest.테넌트_영역_화면은_17개다` → `18개다`.
- 셋업만 낡은 것 → 셋업만 고침: UPDATE 감사 메시지 정확 일치 8건 → `startsWith`(메시지 뒤에
  변경 내역이 붙음), FidoLogControllerWebTest 의 서비스 API 변경.

### 남긴 것

- 커밋하지 않았다(요청 없음). 43 files changed, +1156/−686.
- `orai18n` 은 testRuntimeClasspath 해석에 포함되어 빌드가 통과했다. Windows 에서
  `gradlew.bat dependencyInsight --dependency orai18n` 로 재확인할 수 있다.
- 수정 폼(`/{id}/edit`)도 레코드를 보여 주지만 DATA_VIEW 를 남기지 않는다. 제공 범위가
  "상세 응답"이라 두었다. 필요하면 CrudController.editForm 에 한 줄이다.

---

# 레거시 매퍼 갭 반영 (2026-09-27)

출처: docs/legacy/mybatis-mappers.md 를 현재 어드민과 대조한 갭 분석. 배치 성격
(통계 집계·만료 정리·로그 아카이브)은 레거시 배치가 계속 돈다는 확인을 받아 제외.

- [x] 2. 고객사 생성 시 회사 0 의 SHARE_TYPE='NO' 설정 복사, 삭제 시 정리 (`manager.insertSystemProp`/`deleteSystemProp`)
- [x] 3. AAID 전체 활성/비활성 (`basic.disableCompanyAllAAID` — 중복 insert 는 NOT EXISTS 로 막음)
- [x] 4. 잠금 N분 자동 해제 (`scheduler.updateManagerPwPolicyUnlock` — 배치 대신 로그인 시도 시점 판정, `PW_LOCK_MINUTES` 기본 30)
- [x] 5. VENDOR_CODE 중복 검사 (`manager.selectVendorCode` — CrudController 에 id 를 받는 validate 오버로드 추가)
- [x] 1. FDS 모니터링 화면 — 현재 컬럼으로 재정의(같은 기기 SERIALCODE 의 N초 이내 반복). 스펙
      docs/superpowers/specs/2026-09-27-fds-monitor-design.md, 계획 docs/superpowers/plans/2026-09-27-fds-monitor.md,
      브랜치 feature/fds-monitor(6 커밋, subagent-driven). 702건 중 기존 DB 의존 4건만 실패, skipped 0.
      로컬 Oracle(docker) 기동 검증(2026-09-27): CREATEDTIME 은 TIMESTAMP(6), 창 함수 SQL 이 1.25초 간격 반복 2건을
      잡고 다른 고객사 행은 걸렀다. 후속: 운영 파티션도 TIMESTAMP 인지 ALL_TAB_COLUMNS 로 확인, Oracle 통합 테스트 추가

결과: 674건 중 670 통과, skipped 0. 실패 4건은 기존 DB 의존 테스트.
신규 테스트 17건(CompanyServiceTest 4, CriteriaQueryServiceTest 3, CriteriaControllerWebTest 1,
LoginAttemptServiceTest 5, ManagerUserDetailsServiceTest 2, CompanyControllerWebTest 2).

## 2026-09-27 로컬 더미 데이터 (docker/init/03-dummy.sql)
- [x] 13종 × 10건 = 130건 투입, 기동 중인 로컬 Oracle(fido-admin-oracle)에 적용 완료
- [x] 화면 검증: /appids 12, /appservers 11, /signs 11, /transaction-hashes 11, /transaction-confirmations 11, /logs/exceptions 11, /logs/mailing 11, /criteria 12, /licenses 11, /managers 14, /fido2/metadata 12, /system/error-codes 12, /logs/fido(2021-01-02) 9, /fds-monitor(2021-01-02) 5건(SN-D001×3, SN-D002×2 — 고객사 2 행은 제외됨)
- [x] 발견·수정: 로컬 스키마에 `CCFA_COMPANY_AAID`(레거시, 운영엔 있음) 와 `SEQ_APPID` 가 없어 /criteria 500 → 01-schema.sql 에 추가(로컬 전용)
- 가드: DB_NAME 이 FREE/FREEPDB1 이 아니면 중단. 되돌리기 쿼리는 03-dummy.sql 하단 주석

## 2026-09-27 이전 어드민 목록 규격 맞춤 (AppID 관리 · 멤버코드 관리 · AAID(정책) 보기)
- [x] AppID 목록 컬럼을 이전 순서(번호·APPID·서비스명·장치·설명·기본값·생성일·수정일·상태)로, 장치/기본값/상태는 표시어(Android·iOS / 설정 / 활성·비활성)
- [x] 공통 조각 `fragments/list-footer.html`(파란 건수 배지 + 가운데 « 1 » 페이지네이션), `admin.css` 의 `.fa-search-panel/.btn-search/.fa-result-badge/.fa-list-legacy`
- [x] 메뉴 이름·순서: AppID 관리, 멤버코드 관리, CHALLENGE 보기, AAID(정책) 보기, FIDO 등록자 관리, TransactionHash 보기, TC원문 보기, 서명 보기(신규 화면, 맨 끝)
- [x] 검증: MenuRegistry/Appid/Appserver/Criteria/TenantSelection/AuditReadInterceptor 테스트 통과, 로컬 기동(`--spring.profiles.active=local`) 후 세 화면 스크린샷 확인(.playwright-mcp/appids.png, criteria.png)
- 참고: 전체 스위트의 통합 테스트 29건은 Testcontainers Oracle 이 exit 77(메모리 부족 — 로컬 Oracle 2개 기동 중)로 못 떠서 실패. 코드와 무관, 컨테이너 내려 두고 재실행 필요

## 2026-09-27 검색 필터 드롭다운 즉시 적용 (admin.js)
- [x] GET 검색 폼의 `<select>` 는 고르는 즉시 제출, 텍스트는 검색 버튼/Enter 로만 적용. 예외는 `data-no-auto-submit`, 전체 즉시 제출 폼(`data-auto-submit`)은 기존 블록 유지
- [x] 브라우저 확인: /appids 에서 서비스명 타이핑만으로는 URL 불변, 상태를 비활성으로 고르자 `?servicename=kbpay&status=unuse` 로 즉시 조회
- [x] 고객사 화면 "벤더코드" → "업체 코드" (form/list/detail 라벨, 중복 검사 메시지, 테스트)

## 2026-09-27 FIDO 로그 더미 (어제·오늘 분할 테이블) + ResponseStatusException 404 처리
- [x] 03-dummy.sql 14절: FIDO_LOGS_20260926(10건)·FIDO_LOGS_20260927(30건, 반복 패턴 5건 포함, 고객사 2 각 1건) — 없을 때만 CREATE 하는 PL/SQL 블록. 실제 FIDO 서버가 날마다 만드는 분할 테이블과 같은 모양
- [x] 확인: /logs/fido(오늘) 30건, 어제 10건, servicename=kbstar 18건, /fds-monitor 오늘 5건, 상세 /logs/fido/2026-09-27/401 200
- [x] 발견·수정: 서비스가 던진 ResponseStatusException(404/400)이 catch-all 에 걸려 500 으로 나갔다 → GlobalExceptionHandler 에 전용 핸들러(404→error/404, 403→error/403, 그 외 그 상태 코드로 error/500). FidoLogControllerWebTest 에 404 케이스 추가
- 전체 테스트 704건 중 통합(Testcontainers) 29건만 실패 — Oracle 컨테이너 exit 77(메모리), 코드와 무관. 통합 제외 실패 0

## 2026-09-27 FIDO 로그 목록에 구분·사용자·결과 표시 + 필터
- [x] `FidoLogJson`(키 경로 상수 op/userid/result, 성공 코드 1200, 표시어) — 실제 서버 키가 다르면 여기만 수정
- [x] `FidoLogQueryService`: JSON_VALUE 로 세 값 SELECT, CCFA_ERROR_TABLE 메시지는 스칼라 서브쿼리(PK 없어 JOIN 시 행 불어남 방지), 구분/사용자/결과 필터. 상세도 동일
- [x] 목록 컬럼 번호·일시·구분(배지)·사용자·결과(성공/실패 + 코드·메시지)·서비스명·시리얼, 검색에 구분·결과 드롭다운(즉시 적용)·사용자 추가. 상세에 세 행 추가
- [x] 테스트: FidoLogJsonTest 4, FidoLogQueryServiceTest 9, FidoLogControllerWebTest 5 통과. 로컬 Oracle 더미 30건으로 필터 합 검증(op 22+1+7, outcome 20+10)
- 교훈: 텍스트 블록 조각을 + 로 이어 붙이면 블록마다 들여쓰기가 따로 벗겨져 앞 공백이 사라짐(`FROM 테이블명WHERE` → ORA-03048). SQL 조각은 일반 문자열로
- 전제(확인 필요): 운영 Oracle 12.1+ (JSON_VALUE). 실제 FIDO 서버 JSON 키 이름 확인

## 2026-09-27 qa/prod 프로필 + 환경별 로깅 + 코드 로그 보강
- [x] `application-qa.yml`, `application-prod.yml` (DB 는 환경 변수, prod 풀 20, 세션 쿠키 secure/http-only, 오류 화면 스택 미포함, 레벨 qa DEBUG/INFO · prod INFO/WARN)
- [x] `logback-spring.xml`: local 콘솔 / qa 콘솔+파일 / prod·미지정 파일만 / test 콘솔 WARN. `${LOG_PATH:-./logs}/fido-admin.log` + `-error.log`(WARN↑), 일자+100MB 롤링, 30일, 3GB/1GB 상한. 모든 줄에 `[req= user= tenant=]`
- [x] `RequestLogFilter`(@Component, HIGHEST_PRECEDENCE — 시큐리티 앞, 사용자는 세션 SPRING_SECURITY_CONTEXT 에서): MDC, X-Request-Id 헤더, 4xx/5xx·1초↑ WARN, 정적 자원 제외. 테스트 6건
- [x] 로그 문장: 로그인 성공/로그아웃, 테넌트 선택, CrudService CREATE/UPDATE/DELETE(테이블·ID 만), 계정 잠금 WARN/해제 INFO, 기동 시 프로필·로그 경로
- [x] README 표·기동 예시. 검증: qa 기동 → 콘솔+./logs 파일, prod 기동 → 콘솔 무출력·파일만, 404 요청이 error.log 에 WARN. 통합 제외 전체 테스트 실패 0
- 교훈: logback `<springProfile>` 식은 `|` 와 `&` 를 괄호 없이 못 섞는다 → `prod | (!local & !qa & !test)`. 잘못되면 @WebMvcTest 컨텍스트 전부 실패

## 2026-09-27 모든 목록의 생성일/수정일을 맨 끝 열로
- [x] AppID·멤버코드·AAID(정책): 상태를 날짜 앞으로. CHALLENGE·서명·TransactionHash·TC원문: 둘째 열 "일시"(CREATEDTIME)를 맨 끝 "생성일"로
- 이미 끝이던 화면(시스템 설정·정보·어드민 기준·FIDO 서버·FIDO2·고객사·FDS 정책·라이선스)은 그대로. 로그 화면(FIDO/감사/예외 로그, 모니터링)의 "일시"는 사건 시각이라 첫 열 유지
- 참고: 앞서 맞춘 이전 어드민 규격(상태가 맨 끝)과 다르지만 이번 지시가 우선
- [x] (추가) 빠졌던 화면도 적용: 로그 목록(감사·예외·FIDO 로그, 모니터링)의 "일시"를 맨 끝 열로, 상세 화면(멤버코드·CHALLENGE·TransactionHash·TC원문·감사/예외/FIDO 로그)의 생성일/수정일/일시 행을 맨 아래로. 전체 템플릿 스캔으로 남은 곳 없음 확인
- 제외(생성/수정 시각이 아님): 데모 접근 코드 시작/종료일시(유효 기간), 운영자 최근 접속·잠금 시각, 메일 발송시각

## 2026-09-27 FIDO 서버 설정 화면을 이전 어드민 규격으로
- [x] 섹션 순서 부가기능 → 인증서 → 알림메일(이전 화면에 없던 섹션, 같은 형식). 토글 "사용안함 [스위치] 사용함", 섹션 머리글 스위치(CERT·SMTP), Challenge 슬라이더+sec 배지, TC 저장 기간 드롭다운(영구저장=9999), 등록시 인증서 검증 드롭다운(yes/no), 추가 옵션 체크박스 한 줄, 폭 전체 노란 저장 버튼
- [x] `FidoSettingOptions`: 슬라이더 최대는 저장값까지 확장, 목록 밖 TC 값은 "N일" 로 끼워 넣음 — 열고 저장만 해도 값이 깎이지 않게
- [x] 저장 방식·키·값 표기 불변. 테스트: FidoSettingControllerWebTest 9, FidoSettingOptionsTest 2 통과. 브라우저 저장 왕복 확인(ENABLE/DISABLE, yes, N, 180, 9999 저장), 검증 행은 삭제
- 가정: TC 선택지(30/90/180일, 1/3/5년, 영구)와 슬라이더 10~180초는 이전 어드민 실제 값 미확인
- [x] (후속) 섹션 순서 알림메일 → 부가기능 → 인증서, 섹션 안 항목 3열 그리드(1400px↓ 2열, 900px↓ 1열). "인증 응답시 추가 옵션"은 체크박스 6개라 행 전체 폭. 테스트 순서 검사 갱신
