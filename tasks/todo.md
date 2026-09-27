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
      후속: 출시 전 운영 파티션의 CREATEDTIME 이 TIMESTAMP 인지 ALL_TAB_COLUMNS 로 확인, Oracle 통합 테스트 추가

결과: 674건 중 670 통과, skipped 0. 실패 4건은 기존 DB 의존 테스트.
신규 테스트 17건(CompanyServiceTest 4, CriteriaQueryServiceTest 3, CriteriaControllerWebTest 1,
LoginAttemptServiceTest 5, ManagerUserDetailsServiceTest 2, CompanyControllerWebTest 2).
