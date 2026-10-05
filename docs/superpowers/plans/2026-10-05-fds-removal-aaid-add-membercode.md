# 이상 징후 탐지 삭제 · AAID 정책 추가 · 멤버코드 자동 생성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FDS 화면·로직을 어드민 코드에서 걷어내고, `/criteria` 에 SUPER 전용 메타데이터 JSON 기반 AAID 등록을 더하고, 멤버코드를 서버가 랜덤 생성하게 한다.

**Architecture:** Spring Boot MVC + Thymeleaf 어드민. 화면은 `CrudController`/`ReadOnlyController` 기반 클래스와 `CrudService` 훅으로 짠다. AAID 등록은 `ReadOnlyController` 를 유지한 채 두 핸들러만 더하고, 파싱은 별도 컴포넌트 `CriteriaMetadataParser` 가 맡는다. 멤버코드는 `MemberCodeGenerator` 컴포넌트가 만들고 `AppserverService.applyDefaults` 가 채운다.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, NamedParameterJdbcTemplate, Jackson, Thymeleaf + thymeleaf-extras-springsecurity6, JUnit 5, Mockito, AssertJ, MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-05-fds-removal-aaid-add-membercode-design.md`

## Global Constraints

- FDS 삭제는 코드만. `docker/init/*.sql`, `docs/erd/*`, `src/test/resources/erd-columns.txt` 는 손대지 않는다.
- 엔티티 `CcfaFdsPolicy` 는 남긴다(`ErdConformanceTest` 가 ERD 39개 테이블마다 엔티티를 요구).
- AAID 등록은 SUPER 전용. 컨트롤러와 서비스 양쪽에서 막는다.
- 새 AAID 는 모든 고객사(IDX 0 포함)의 `CCFA_COMPANY_AAID` 에 차단 행을 넣어 비활성으로 시작한다.
- 형식 오류 문구: `유효하지 않은 metadata 형식입니다.` / 중복 문구: `이미 등록된 AAID 입니다.`
- `METAHASH` = 원문 UTF-8 바이트 SHA-256 의 base64url(패딩 없음).
- 파서 기본값: `TCDISPLAYCONTENTTYPE` = `text/plain`, `ASSERTIONSCHEMES` = `UAFV1TLV` (값이 없을 때).
- 멤버코드: `SecureRandom`, 문자 집합 `A-Z0-9`, 길이 10, 전체 유일, 최대 5회 시도. 등록·수정 모두 폼 값 무시.
- AAID 수정·삭제는 만들지 않는다.
- 커밋 메시지는 한국어 conventional 형식, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 작업 트리에 이미 있는 `docker/init/03-dummy.sql` 수정은 사용자 작업이다. 절대 `git add` 하지 않는다 — 커밋은 항상 파일을 지정해서 한다.
- 통합 테스트(`integration/*IntegrationTest`)는 Docker(Oracle Testcontainers)가 있어야 돈다. Docker 가 없으면 해당 실패는 환경 문제로 보고하고 넘어간다.

## Review Focus

1. **FIDO2/MDS3 형식 메타데이터 붙여넣기** — `keyProtection: ["hardware"]` 처럼 숫자 자리에 문자열 배열이 오면 500 이 아니라 형식 오류가 폼에 떠야 한다. (Task 2 `fido2StyleArrayFieldIsFormatError`)
2. **AAID 앞뒤 공백** — `" 0012#0001 "` 은 trim 되어 저장되고, 기존 `0012#0001` 과 중복으로 걸려야 한다. (Task 2 `aaidIsTrimmed`, Task 3 `duplicateAaidIsRejected` 가 trim 된 값으로 조회)
3. **COMPANY 운영자가 POST /criteria 를 직접 호출** — 403 이고 서비스 `create` 가 불리지 않아야 한다. (Task 3 `companyCannotPostCreate`)
4. **멤버코드 수정 시 hidden/readonly 값 변조** — 수정 POST 에 `memberCode=HACK` 을 실어도 DB 값이 바뀌지 않고, 중복 검사도 DB 값으로 돈다. (Task 4 `updateIgnoresPostedMemberCodeAndChecksWithStoredCode`)
5. **빈 textarea 제출** — 파서까지 가지 않고 폼 오류로 돌아와야 한다. (Task 3 `blankJsonShowsFormAgain`)

---

### Task 1: 이상 징후 탐지(FDS) 코드 삭제

**Files:**
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyController.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicySearchForm.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/service/FdsPolicyService.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/company/repository/CcfaFdsPolicyRepository.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/common/IpRuleList.java`
- Delete: `src/main/java/com/crosscert/fidoadmin/common/IpRuleListValidator.java`
- Delete: `src/main/resources/templates/company/fds-policy/` (list/detail/form), `src/main/resources/templates/company/fds-monitor/list.html`
- Delete tests: `company/service/FdsPolicyServiceTest.java`, `company/service/FdsMonitorQueryServiceTest.java`, `company/web/FdsPolicyControllerWebTest.java`, `company/web/FdsMonitorControllerWebTest.java`, `company/web/FdsMonitorRowTest.java`, `company/web/FdsPolicyTenantTest.java`, `common/IpRuleListValidatorTest.java` (모두 `src/test/java/com/crosscert/fidoadmin/` 아래)
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java:59-62`
- Modify: `src/main/java/com/crosscert/fidoadmin/company/service/CompanyService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/company/entity/CcfaFdsPolicy.java:12` (주석)
- Modify: `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java:20` (주석)
- Modify: `docs/legacy/admin-java-features.md`
- Test: `src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java`, `src/test/java/com/crosscert/fidoadmin/company/service/CompanyServiceTest.java`, `src/test/java/com/crosscert/fidoadmin/integration/LegacyParityIntegrationTest.java`

**Interfaces:**
- Consumes: 없음
- Produces: `CompanyService` 생성자에서 `CcfaFdsPolicyRepository fdsPolicies` 파라미터가 빠진다. 새 시그니처:
  `CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids, UserinfoRepository users, CcfaManagerRepository managers, CcfaSystemPropRepository props, TenantContext tenant, CriteriaQueryService criteria, ApplicationEventPublisher events)`

- [ ] **Step 1: 테스트를 먼저 새 동작으로 고친다**

`MenuRegistryTest.java`:
- 22행 `.contains("/", "/appids", "/users", "/logs/fido", "/fds-policies", "/criteria")` → `.contains("/", "/appids", "/users", "/logs/fido", "/criteria")`, 그리고 바로 아래 `.doesNotContain(` 인자 목록 맨 앞에 `"/fds-policies", "/fds-monitor",` 를 더한다.
- `companySeesNoFido2Group` 의 기대값 → `.containsExactly("대시보드", "로그", "FIDO 서버 관리", "내 정보");`
- `groupsPreserveOrder` 의 기대값 → `.containsExactly("대시보드", "로그", "FIDO 서버 관리", "FIDO2", "시스템관리", "내 정보");`
- `테넌트_영역_화면은_19개다` → 메서드명 `테넌트_영역_화면은_17개다`, 주석 `// 모니터링(/fds-monitor)이 더해져 18 → 19 다.` → `// 이상 징후 탐지 두 화면(FDS 정책·모니터링)을 지워 19 → 17 이다.`, 단언 `isEqualTo(17)`.
- `titleFor_resolves_longest_prefix_and_falls_back_to_path` 의 `assertThat(registry.titleFor("/fds-monitor")).isEqualTo("모니터링");` → `assertThat(registry.titleFor("/fds-monitor")).isEqualTo("/fds-monitor");`
- `fdsMonitorIsTenantAreaAndNotSuperOnly` 테스트를 아래로 바꾼다:

```java
    /** 이상 징후 탐지는 2026-10-05 에 지웠다. 메뉴에 다시 나타나면 안 된다. */
    @Test void fdsMenusAreGone() {
        assertThat(MenuRegistry.ALL).extracting(MenuItem::href).doesNotContain("/fds-policies", "/fds-monitor");
        assertThat(MenuRegistry.ALL).extracting(MenuItem::group).doesNotContain("이상 징후 탐지");
    }
```

`CompanyServiceTest.java`:
- import `CcfaFdsPolicy`, `CcfaFdsPolicyRepository` 두 줄 삭제.
- 47행 `CcfaFdsPolicyRepository fdsPolicies = mock(CcfaFdsPolicyRepository.class);` 삭제.
- 51행 생성자 호출의 `fdsPolicies, ` 인자 삭제 → `new TenantContext(new SelectedTenant()), criteria, events);`
- `createDisablesAllAaidsAndAddsDefaultFdsPolicy` 를 아래로 교체:

```java
    @SuppressWarnings("unchecked")
    @Test void createDisablesAllAaids() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.disableAllFor(7L)).thenReturn(3);

        service.create(new CcfaCompany());

        verify(criteria).disableAllFor(7L);
        verify(audit).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (3건)");
        verify(audit, never()).log(eq(AuditType.CREATE), startsWith("CCFA_FDS_POLICY"));
        verify(events).publishEvent(new FidoConfigChanged("고객사 생성 7"));
    }
```

- `createKeepsExistingFdsPolicy` 테스트 삭제.
- `deleteRemovesAaidRowsAndFdsPolicy` 를 아래로 교체:

```java
    @SuppressWarnings("unchecked")
    @Test void deleteRemovesAaidRows() {
        when(companies.findById(7L)).thenReturn(Optional.of(company(7L)));
        when(appids.countByCompanyIdx(7L)).thenReturn(0L);
        when(users.countByCompanyIdx(7L)).thenReturn(0L);
        when(managers.countByCompanyIdx(7L)).thenReturn(0L);
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.deleteAllFor(7L)).thenReturn(5);

        service.delete(7L);

        verify(criteria).deleteAllFor(7L);
        verify(audit).log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 7 (5건)");
        verify(audit, never()).log(eq(AuditType.DELETE), startsWith("CCFA_FDS_POLICY"));
    }
```

- `blockedDeleteLeavesAaidAndFdsAlone` → 메서드명 `blockedDeleteLeavesAaidAlone`, `verify(fdsPolicies, never()).deleteById(any());` 줄 삭제.
- 파일 상단 static import 에 `eq`, `startsWith`(`org.mockito.ArgumentMatchers`)가 없으면 더한다.

`LegacyParityIntegrationTest.java` 의 `newCompanyStartsWithAllAaidsBlockedAndDefaultFdsPolicy` 를 아래로 교체:

```java
    @Test void newCompanyStartsWithAllAaidsBlockedAndNoFdsPolicy() {
        loginSuper();
        CcfaCompany c = new CcfaCompany();
        c.setCompanyName("IT 신규 고객사");
        c.setCompanyType("TEST");
        c.setVendorCode("IT001");
        c.setContact("it@test.local");
        c.setEnableType("N");
        c.setMaxAppid(1L);
        c.setMaxAppserver(1L);
        c.setMaxUser(100L);
        Long idx = companies.create(c).getIdx();

        Integer criteria = jdbc.queryForObject("SELECT COUNT(DISTINCT AAID) FROM CRITERIA WHERE AAID IS NOT NULL", Integer.class);
        Integer blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx);
        assertThat(blocked).isEqualTo(criteria);
        // 이상 징후 탐지를 지웠으므로 기본 정책 행을 만들지 않는다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_FDS_POLICY WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();

        companies.delete(idx);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();
    }
```

- [ ] **Step 2: 컴파일이 깨지는지 확인**

Run: `./gradlew compileTestJava`
Expected: FAIL — `CompanyServiceTest` 생성자 인자 수가 맞지 않음.

- [ ] **Step 3: FDS 파일 삭제**

```bash
git rm -q \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyController.java \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicySearchForm.java \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorController.java \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorRow.java \
  src/main/java/com/crosscert/fidoadmin/company/web/FdsMonitorSearchForm.java \
  src/main/java/com/crosscert/fidoadmin/company/service/FdsPolicyService.java \
  src/main/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryService.java \
  src/main/java/com/crosscert/fidoadmin/company/repository/CcfaFdsPolicyRepository.java \
  src/main/java/com/crosscert/fidoadmin/common/IpRuleList.java \
  src/main/java/com/crosscert/fidoadmin/common/IpRuleListValidator.java \
  src/main/resources/templates/company/fds-policy/list.html \
  src/main/resources/templates/company/fds-policy/detail.html \
  src/main/resources/templates/company/fds-policy/form.html \
  src/main/resources/templates/company/fds-monitor/list.html \
  src/test/java/com/crosscert/fidoadmin/company/service/FdsPolicyServiceTest.java \
  src/test/java/com/crosscert/fidoadmin/company/service/FdsMonitorQueryServiceTest.java \
  src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyControllerWebTest.java \
  src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorControllerWebTest.java \
  src/test/java/com/crosscert/fidoadmin/company/web/FdsMonitorRowTest.java \
  src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyTenantTest.java \
  src/test/java/com/crosscert/fidoadmin/common/IpRuleListValidatorTest.java
```

- [ ] **Step 4: MenuRegistry 에서 FDS 메뉴 삭제**

`MenuRegistry.java` 에서 아래 4줄을 지운다:

```java
        // 이전 어드민의 "이상 징후 탐지 → 정책관리 / 모니터링". 모니터링은 현재 컬럼으로 성립하는
        // 반복 주기 조건만 본다(docs/superpowers/specs/2026-09-27-fds-monitor-design.md).
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "모니터링", "/fds-monitor", false, "bi-activity"),
```

(TENANT 경로를 지우면 `areaOf` 가 SYSTEM 으로 판정된다는 `MenuItem` 주석의 우려는 해당 컨트롤러도 함께 지워 404 가 되므로 해당하지 않는다. `hidden` 으로 남기지 않는다.)

- [ ] **Step 5: CompanyService 에서 FDS 연동 삭제**

`CompanyService.java`:
- import `CcfaFdsPolicy`, `CcfaFdsPolicyRepository` 삭제.
- 필드 `private final CcfaFdsPolicyRepository fdsPolicies;` 삭제.
- 생성자 파라미터 `CcfaFdsPolicyRepository fdsPolicies, ` 와 대입 `this.fdsPolicies = fdsPolicies;` 삭제. 결과:

```java
    public CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids,
                          UserinfoRepository users, CcfaManagerRepository managers,
                          CcfaSystemPropRepository props, TenantContext tenant,
                          CriteriaQueryService criteria, ApplicationEventPublisher events) {
        super(repository, audit, tenant);
        this.appids = appids;
        this.users = users;
        this.managers = managers;
        this.props = props;
        this.criteria = criteria;
        this.events = events;
    }
```

- `create` 안의 `// 이전 어드민 fds.insert: ...` 주석부터 `if (!fdsPolicies.existsById(...)) { ... }` 블록 끝까지 삭제.
- `delete` 안의 `if (fdsPolicies.existsById(id)) { ... }` 블록 삭제.

- [ ] **Step 6: 주석 정리**

`CcfaFdsPolicy.java` 12행:

```java
/**
 * CCFA_FDS_POLICY — COMPANY_IDX 가 PK(고객사당 1건). ERD 컬럼 10개.
 *
 * <p>어드민에서 쓰지 않는다. 이상 징후 탐지 화면은 2026-10-05 에 지웠지만 테이블은 운영 DB 에
 * 남아 있어 ERD 대조({@code ErdConformanceTest})를 위해 엔티티만 둔다.
 */
```

`FidoLogTable.java` 20행 ` * <p>FIDO 로그 화면과 FDS 모니터링이 같은 테이블을 읽으므로 한 곳에 둔다.` → ` * <p>일자별 테이블 이름 규칙을 한 곳에 둔다.`

- [ ] **Step 7: 레거시 문서 갱신**

`docs/legacy/admin-java-features.md`:
- 52행 `CompanyController` 행 마지막 칸 `` `/companies` (삭제 시 딸린 데이터 정리, 생성 시 AAID 전체 차단·FDS 기본행 — 설계서 §3.4) `` → `` `/companies` (삭제 시 딸린 데이터 정리, 생성 시 AAID 전체 차단 — 설계서 §3.4. FDS 기본행은 2026-10-05 FDS 삭제로 만들지 않음) ``
- 55행 `FDSController` `/policy` 행 마지막 칸 `` `/fds-policies` `` → `없음(2026-10-05 삭제)`
- 56행 `FDSController` `/monitoring` 행 마지막 칸 `` `/fds-monitor` (반복 주기 조건만 본다) `` → `없음(2026-10-05 삭제)`
- 108~109행 `- 고객사 생성: ... 새 어드민: \`CompanyService.create\` (설계서 §3.4).` 문단 끝에 ` FDS 정책 기본행은 2026-10-05 이후 만들지 않는다.` 를 덧붙인다.
- 110행 `새 어드민: \`CompanyService.delete\` 가 설정·AAID·FDS 정책을 지운다.` → `새 어드민: \`CompanyService.delete\` 가 설정·AAID 를 지운다(FDS 정책 행은 2026-10-05 이후 건드리지 않는다).`
- 149행 `FDSPolicyValidator` 행 마지막 칸 → `없음(2026-10-05 FDS 삭제와 함께 \`@IpRuleList\` 제거)`

- [ ] **Step 8: 남은 참조가 없는지 확인**

Run: `grep -rn -i "fds\|IpRuleList\|이상 징후" src/main src/test --include='*.java' --include='*.html' --include='*.js' --include='*.css'`
Expected: `CcfaFdsPolicy.java` 와 `LegacyParityIntegrationTest.java`(CCFA_FDS_POLICY 0건 단언), `MenuRegistryTest.java`(부재 단언) 만 나온다.

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew test --tests '*MenuRegistryTest' --tests '*CompanyServiceTest' --tests '*ErdConformanceTest' --tests '*EntityBootTest'`
Expected: PASS

Run: `./gradlew test`
Expected: PASS (Docker 가 없으면 `integration` 패키지만 환경 오류로 실패할 수 있다 — 그 경우 보고)

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
  src/main/java/com/crosscert/fidoadmin/company/service/CompanyService.java \
  src/main/java/com/crosscert/fidoadmin/company/entity/CcfaFdsPolicy.java \
  src/main/java/com/crosscert/fidoadmin/log/service/FidoLogTable.java \
  src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java \
  src/test/java/com/crosscert/fidoadmin/company/service/CompanyServiceTest.java \
  src/test/java/com/crosscert/fidoadmin/integration/LegacyParityIntegrationTest.java \
  docs/legacy/admin-java-features.md
git commit -m "refactor: 이상 징후 탐지(FDS) 화면과 고객사 생성·삭제 연동을 지운다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 메타데이터 파서 `CriteriaMetadataParser`

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataException.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataParser.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataParserTest.java`

**Interfaces:**
- Consumes: `Criteria` 엔티티(기존, setter 는 Lombok)
- Produces:
  - `public class CriteriaMetadataException extends RuntimeException { public CriteriaMetadataException(String message) }`
  - `public static final String CriteriaMetadataParser.INVALID = "유효하지 않은 metadata 형식입니다."`
  - `@Component public class CriteriaMetadataParser { public Criteria parse(String json) }` — IDX·CREATETIME·UPDATEDTIME 은 비워 둔다. 실패 시 `CriteriaMetadataException`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CriteriaMetadataParserTest {

    CriteriaMetadataParser parser = new CriteriaMetadataParser();

    static final String FULL = """
        {
          "aaid": "0012#0001",
          "description": "Sample fingerprint",
          "authenticatorVersion": 2,
          "assertionScheme": "UAFV1TLV",
          "authenticationAlgorithm": 1,
          "attestationTypes": [15879, 15880],
          "userVerificationDetails": [[{"userVerification": 2}], [{"userVerification": 4}, {"userVerification": 2}]],
          "keyProtection": 6,
          "matcherProtection": 2,
          "attachmentHint": 1,
          "tcDisplay": 3,
          "tcDisplayContentType": "image/png"
        }
        """;

    @Test void mapsEveryColumn() throws Exception {
        Criteria c = parser.parse(FULL);

        assertThat(c.getIdx()).isNull();
        assertThat(c.getAaid()).isEqualTo("0012#0001");
        assertThat(c.getVendorids()).isEqualTo("0012");
        assertThat(c.getUserverification()).isEqualTo(6L); // 2 | 4 | 2
        assertThat(c.getKeyprotection()).isEqualTo(6L);
        assertThat(c.getMatcherprotection()).isEqualTo(2L);
        assertThat(c.getAttachmenthnumber()).isEqualTo(1L);
        assertThat(c.getTcdisplay()).isEqualTo(3L);
        assertThat(c.getTcdisplaycontenttype()).isEqualTo("image/png");
        assertThat(c.getAuthenticationalgorithms()).isEqualTo("1");
        assertThat(c.getAssertionschemes()).isEqualTo("UAFV1TLV");
        assertThat(c.getAttestationtypes()).isEqualTo("15879,15880");
        assertThat(c.getAuthenticatorversion()).isEqualTo(2L);
        assertThat(c.getJsondata()).isEqualTo(FULL);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(FULL.getBytes(StandardCharsets.UTF_8));
        assertThat(c.getMetahash()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        assertThat(c.getCreatetime()).isNull();
    }

    @Test void pluralAlgorithmsWinOverSingular() {
        Criteria c = parser.parse("""
            {"aaid":"0012#0002","authenticationAlgorithm":1,"authenticationAlgorithms":[1,7]}""");
        assertThat(c.getAuthenticationalgorithms()).isEqualTo("1,7");
    }

    @Test void missingOptionalFieldsBecomeNullOrDdlDefaults() {
        Criteria c = parser.parse("{\"aaid\":\"ABCD#0001\"}");
        assertThat(c.getUserverification()).isNull();
        assertThat(c.getKeyprotection()).isNull();
        assertThat(c.getAuthenticationalgorithms()).isNull();
        assertThat(c.getAttestationtypes()).isNull();
        assertThat(c.getTcdisplaycontenttype()).isEqualTo("text/plain");
        assertThat(c.getAssertionschemes()).isEqualTo("UAFV1TLV");
    }

    @Test void nullAlgorithmStaysNull() {
        assertThat(parser.parse("{\"aaid\":\"0012#0003\",\"authenticationAlgorithm\":null}").getAuthenticationalgorithms()).isNull();
    }

    @Test void aaidWithoutHashHasNoVendor() {
        assertThat(parser.parse("{\"aaid\":\"NOHASH\"}").getVendorids()).isNull();
    }

    @Test void aaidIsTrimmed() {
        assertThat(parser.parse("{\"aaid\":\"  0012#0001 \"}").getAaid()).isEqualTo("0012#0001");
    }

    @Test void brokenJsonIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID);
    }

    @Test void topLevelArrayIsFormatError() {
        assertThatThrownBy(() -> parser.parse("[{\"aaid\":\"0012#0001\"}]"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID);
    }

    @Test void missingOrBlankAaidIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"description\":\"x\"}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"   \"}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":12}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
    }

    /** FIDO2/MDS3 메타데이터는 keyProtection 이 문자열 배열이다. 500 이 아니라 형식 오류여야 한다. */
    @Test void fido2StyleArrayFieldIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"0012#0001\",\"keyProtection\":[\"hardware\"]}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID + " (keyProtection)");
    }

    @Test void fractionalNumberIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"0012#0001\",\"tcDisplay\":1.5}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID + " (tcDisplay)");
    }

    @Test void overlongColumnIsRejectedWithFieldAndLimit() {
        String aaid = "A".repeat(65);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"" + aaid + "\"}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage("aaid 는 64바이트를 넘을 수 없습니다.");
    }

    @Test void nullOrBlankInputIsFormatError() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(CriteriaMetadataException.class);
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(CriteriaMetadataException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*CriteriaMetadataParserTest'`
Expected: FAIL — `CriteriaMetadataParser` 를 찾을 수 없음(컴파일 오류).

- [ ] **Step 3: 구현**

`CriteriaMetadataException.java`:

```java
package com.crosscert.fidoadmin.fido.service;

/** 붙여 넣은 메타데이터를 CRITERIA 로 옮길 수 없을 때. 메시지는 화면에 그대로 보인다. */
public class CriteriaMetadataException extends RuntimeException {
    public CriteriaMetadataException(String message) {
        super(message);
    }
}
```

`CriteriaMetadataParser.java`:

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * FIDO UAF 메타데이터 문(Metadata Statement) JSON → CRITERIA 행.
 *
 * <p>이전 어드민 FidoValidator 의 aaid 모듈이 하던 일을 잇는다. 숫자 자리에 다른 형이 오면
 * (FIDO2/MDS3 메타데이터처럼 keyProtection 이 문자열 배열인 경우) 저장하지 않고 형식 오류로 돌려보낸다.
 * METAHASH 는 원문 UTF-8 바이트의 SHA-256 을 base64url(패딩 없음)로 둔다 — MDS 관례다.
 * 이전 어드민의 해시 방식은 소스에서 확인하지 못했다.
 */
@Component
public class CriteriaMetadataParser {

    public static final String INVALID = "유효하지 않은 metadata 형식입니다.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Criteria parse(String json) {
        if (json == null || json.isBlank()) throw new CriteriaMetadataException(INVALID);
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new CriteriaMetadataException(INVALID);
        }
        if (root == null || !root.isObject()) throw new CriteriaMetadataException(INVALID);

        JsonNode aaidNode = root.get("aaid");
        if (aaidNode == null || !aaidNode.isTextual() || aaidNode.asText().isBlank()) {
            throw new CriteriaMetadataException(INVALID);
        }
        String aaid = aaidNode.asText().trim();
        int hash = aaid.indexOf('#');

        Criteria c = new Criteria();
        c.setAaid(limit("aaid", aaid, 64));
        c.setVendorids(hash < 0 ? null : limit("vendorId", aaid.substring(0, hash), 32));
        c.setUserverification(userVerification(root.get("userVerificationDetails")));
        c.setKeyprotection(number(root, "keyProtection"));
        c.setMatcherprotection(number(root, "matcherProtection"));
        c.setAttachmenthnumber(number(root, "attachmentHint"));
        c.setTcdisplay(number(root, "tcDisplay"));
        c.setTcdisplaycontenttype(limit("tcDisplayContentType", text(root, "tcDisplayContentType", "text/plain"), 128));
        Long singleAlgorithm = number(root, "authenticationAlgorithm");
        String algorithms = root.has("authenticationAlgorithms")
            ? joinNumbers(root, "authenticationAlgorithms")
            : (singleAlgorithm == null ? null : String.valueOf(singleAlgorithm));
        c.setAuthenticationalgorithms(limit("authenticationAlgorithms", algorithms, 64));
        c.setAssertionschemes(limit("assertionScheme", text(root, "assertionScheme", "UAFV1TLV"), 64));
        c.setAttestationtypes(limit("attestationTypes", joinNumbers(root, "attestationTypes"), 64));
        c.setAuthenticatorversion(number(root, "authenticatorVersion"));
        c.setJsondata(json);
        c.setMetahash(sha256(json));
        return c;
    }

    /** userVerificationDetails 는 [[{userVerification: n}, ...], ...]. 모든 n 을 비트 OR 한다. */
    private static Long userVerification(JsonNode details) {
        if (details == null || details.isNull()) return null;
        if (!details.isArray()) throw invalid("userVerificationDetails");
        long bits = 0;
        boolean any = false;
        for (JsonNode combination : details) {
            if (!combination.isArray()) throw invalid("userVerificationDetails");
            for (JsonNode descriptor : combination) {
                JsonNode uv = descriptor.get("userVerification");
                if (uv == null || !uv.canConvertToExactIntegral()) throw invalid("userVerificationDetails");
                bits |= uv.asLong();
                any = true;
            }
        }
        return any ? bits : null;
    }

    private static Long number(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        if (!n.isNumber() || !n.canConvertToExactIntegral()) throw invalid(field);
        return n.asLong();
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return fallback;
        if (!n.isTextual()) throw invalid(field);
        return n.asText();
    }

    private static String joinNumbers(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        if (!n.isArray()) throw invalid(field);
        List<String> parts = new ArrayList<>();
        for (JsonNode item : n) {
            if (!item.isNumber() || !item.canConvertToExactIntegral()) throw invalid(field);
            parts.add(String.valueOf(item.asLong()));
        }
        return parts.isEmpty() ? null : String.join(",", parts);
    }

    private static String limit(String field, String value, int maxBytes) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new CriteriaMetadataException(field + " 는 " + maxBytes + "바이트를 넘을 수 없습니다.");
        }
        return value;
    }

    private static CriteriaMetadataException invalid(String field) {
        return new CriteriaMetadataException(INVALID + " (" + field + ")");
    }

    private static String sha256(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // JDK 에 SHA-256 은 항상 있다
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests '*CriteriaMetadataParserTest'`
Expected: PASS (13 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataException.java \
  src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataParser.java \
  src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaMetadataParserTest.java
git commit -m "feat: UAF 메타데이터 JSON 을 CRITERIA 행으로 옮기는 파서를 더한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: AAID 정책 등록 (서비스 · 컨트롤러 · 화면)

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/repository/CriteriaRepository.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaMetadataForm.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaController.java`
- Create: `src/main/resources/templates/fido/criteria/form.html`
- Modify: `src/main/resources/templates/fido/criteria/list.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/CriteriaControllerWebTest.java`

**Interfaces:**
- Consumes: `CriteriaMetadataParser.parse(String) : Criteria`, `CriteriaMetadataException`, `CriteriaMetadataParser.INVALID` (Task 2)
- Produces:
  - `CriteriaRepository.existsByAaid(String aaid) : boolean`
  - `CriteriaQueryService` 생성자: `(CriteriaRepository repository, AuditLogger audit, TenantContext tenant, NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events, CriteriaMetadataParser parser)`
  - `CriteriaQueryService.create(String json) : Criteria` — SUPER 아니면 `AccessDeniedException`, 형식·중복은 `CriteriaMetadataException`
  - `CriteriaMetadataForm { @NotBlank String jsondata }`
  - `GET /criteria/new`, `POST /criteria`

- [ ] **Step 1: 서비스 테스트 작성**

`CriteriaQueryServiceTest.java`:
- 필드 `CriteriaMetadataParser parser = new CriteriaMetadataParser();` 추가, 서비스 생성을 `new CriteriaQueryService(repo, audit, new TenantContext(new SelectedTenant()), jdbc, events, parser);` 로 바꾼다.
- import 추가: `org.springframework.security.access.AccessDeniedException`.
- 아래 테스트를 클래스 끝에 더한다:

```java
    @Test void superCreatesCriteriaBlocksEveryCompanyAndPublishes() {
        login(0L);
        when(repo.existsByAaid("0012#0009")).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> { Criteria c = inv.getArgument(0); c.setIdx(30L); return c; });
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(4);

        Criteria saved = service.create("{\"aaid\":\"0012#0009\",\"keyProtection\":2}");

        assertThat(saved.getIdx()).isEqualTo(30L);
        assertThat(saved.getKeyprotection()).isEqualTo(2L);
        assertThat(saved.getCreatetime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isEqualTo(saved.getCreatetime());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("INSERT INTO CCFA_COMPANY_AAID").contains("FROM CCFA_COMPANY").contains("NOT EXISTS");
        assertThat(params.getValue().getValue("aaid")).isEqualTo("0012#0009");
        verify(audit).log(AuditType.CREATE, "AAID(정책) 등록 | AAID: 0012#0009 | 차단 고객사 4곳");
        verify(events).publishEvent(new FidoConfigChanged("AAID 등록 0012#0009"));
    }

    /** 중복 검사는 trim 된 AAID 로 한다. 앞뒤 공백으로 같은 AAID 가 두 번 들어가면 안 된다. */
    @Test void duplicateAaidIsRejected() {
        login(0L);
        when(repo.existsByAaid("0012#0001")).thenReturn(true);

        assertThatThrownBy(() -> service.create("{\"aaid\":\" 0012#0001 \"}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage("이미 등록된 AAID 입니다.");

        verify(repo, never()).save(any());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test void companyRoleCannotCreate() {
        login(7L);
        assertThatThrownBy(() -> service.create("{\"aaid\":\"0012#0009\"}"))
            .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).save(any());
    }

    @Test void invalidMetadataSavesNothing() {
        login(0L);
        assertThatThrownBy(() -> service.create("not json"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID);
        verify(repo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*CriteriaQueryServiceTest'`
Expected: FAIL — 생성자 인자 수, `existsByAaid`, `create(String)` 없음(컴파일 오류).

- [ ] **Step 3: 리포지토리·서비스 구현**

`CriteriaRepository.java`:

```java
public interface CriteriaRepository extends AdminRepository<Criteria, Long> {
    boolean existsByAaid(String aaid);
}
```

`CriteriaQueryService.java`:
- import 추가: `java.time.LocalDateTime`, `org.springframework.security.access.AccessDeniedException`.
- 필드·생성자:

```java
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final CriteriaMetadataParser parser;

    public CriteriaQueryService(CriteriaRepository repository, AuditLogger audit,
                                TenantContext tenant, NamedParameterJdbcTemplate jdbc,
                                ApplicationEventPublisher events, CriteriaMetadataParser parser) {
        super(repository, audit, tenant);
        this.jdbc = jdbc;
        this.events = events;
        this.parser = parser;
    }
```

- `changeStatus` 앞에 메서드 추가:

```java
    /**
     * 메타데이터 JSON 으로 새 AAID 정책을 등록한다. SUPER 전용.
     *
     * <p>CRITERIA 는 전역 기준 데이터라 고객사 운영자가 늘릴 수 없다. 이 서비스는
     * {@link #requireSuperForGlobalTable()} 을 로그인 확인으로 풀어 두었으므로 여기서 직접 막는다.
     *
     * <p>새 AAID 는 모든 고객사에서 꺼진 채로 시작한다 — 고객사 생성 때 AAID 를 전부 막는 것과 같은
     * 규약이다. 각 고객사가 목록에서 켜야 FIDO 등록에 쓰인다.
     *
     * <p>CRITERIA 에 유니크 제약이 없어 동시 등록 경쟁은 막지 못한다. SUPER 전용·저빈도 화면이라
     * 애플리케이션 중복 검사로 둔다.
     */
    @Transactional
    public Criteria create(String json) {
        if (!tenant.require().isSuper()) {
            throw new AccessDeniedException("AAID 정책 등록은 최고 관리자 전용입니다");
        }
        Criteria criteria = parser.parse(json);
        CriteriaRepository criteriaRepository = (CriteriaRepository) repository;
        if (criteriaRepository.existsByAaid(criteria.getAaid())) {
            throw new CriteriaMetadataException("이미 등록된 AAID 입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        criteria.setCreatetime(now);
        criteria.setUpdatedtime(now);
        Criteria saved = repository.save(criteria);

        int blocked = jdbc.update("""
            INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
            SELECT c.IDX, :aaid FROM CCFA_COMPANY c
             WHERE NOT EXISTS (
                 SELECT 1 FROM CCFA_COMPANY_AAID b
                  WHERE b.COMPANY_IDX = c.IDX AND b.AAID = :aaid
             )
            """, new MapSqlParameterSource("aaid", saved.getAaid()));

        audit.log(AuditType.CREATE,
            "AAID(정책) 등록 | AAID: " + saved.getAaid() + " | 차단 고객사 " + blocked + "곳");
        events.publishEvent(new FidoConfigChanged("AAID 등록 " + saved.getAaid()));
        return saved;
    }
```

- 클래스 Javadoc 첫 줄 `CRITERIA(AAID 정책) 조회와 고객사별 활성/비활성 토글.` → `CRITERIA(AAID 정책) 조회·등록과 고객사별 활성/비활성 토글.`

- [ ] **Step 4: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests '*CriteriaQueryServiceTest' --tests '*CompanyServiceTest'`
Expected: PASS

- [ ] **Step 5: 웹 테스트 작성**

`CriteriaControllerWebTest.java`:
- import 추가: `static org.mockito.Mockito.never`, `static org.mockito.ArgumentMatchers.anyString`, `static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model`, `com.crosscert.fidoadmin.fido.service.CriteriaMetadataException`, `com.crosscert.fidoadmin.fido.service.CriteriaMetadataParser`.
- `superSeesListWithoutJsonAndWithoutCompanyFilter` 의 `.andExpect(content().string(not(containsString("/criteria/new"))))` → `.andExpect(content().string(containsString("/criteria/new")))` (SUPER 에게 등록 버튼이 보인다).
- 아래 테스트를 더한다:

```java
    @Test void companyDoesNotSeeCreateButton() throws Exception {
        when(service.disabledAaids()).thenReturn(java.util.Set.of());
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        mvc.perform(get("/criteria").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/criteria/new"))));
    }

    @Test void superOpensCreateForm() throws Exception {
        mvc.perform(get("/criteria/new").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString("name=\"jsondata\"")));
    }

    @Test void companyCannotOpenCreateForm() throws Exception {
        mvc.perform(get("/criteria/new").with(user(companyUser)))
            .andExpect(status().isForbidden());
    }

    @Test void companyCannotPostCreate() throws Exception {
        mvc.perform(post("/criteria").with(user(companyUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().isForbidden());
        verify(service, never()).create(anyString());
    }

    @Test void superCreateRedirectsToDetail() throws Exception {
        Criteria saved = criteria();
        saved.setIdx(30L);
        when(service.create("{\"aaid\":\"0012#0009\"}")).thenReturn(saved);
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/criteria/30"))
            .andExpect(flash().attribute("flashSuccess", "등록되었습니다. 모든 고객사에서 비활성 상태로 시작합니다."));
    }

    @Test void parseErrorShowsFormWithMessageAndKeepsInput() throws Exception {
        when(service.create("{bad")).thenThrow(new CriteriaMetadataException(CriteriaMetadataParser.INVALID));
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{bad"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString(CriteriaMetadataParser.INVALID)))
            .andExpect(content().string(containsString("{bad")));
    }

    @Test void blankJsonShowsFormAgain() throws Exception {
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "   "))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"));
        verify(service, never()).create(anyString());
    }
```

- [ ] **Step 6: 실패 확인**

Run: `./gradlew test --tests '*CriteriaControllerWebTest'`
Expected: FAIL — `/criteria/new` 가 `/{id}` 로 매핑되어 400/404, POST `/criteria` 405 등.

- [ ] **Step 7: 폼·컨트롤러·템플릿 구현**

`CriteriaMetadataForm.java`:

```java
package com.crosscert.fidoadmin.fido.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** AAID 정책 등록 폼. 메타데이터 문 JSON 원문 하나만 받는다. 형식 검증은 CriteriaMetadataParser 가 한다. */
@Getter @Setter
public class CriteriaMetadataForm {
    @NotBlank(message = "메타데이터 JSON 을 입력하세요.")
    private String jsondata;
}
```

`CriteriaController.java`:
- import 추가:

```java
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
```

- 클래스 Javadoc 끝에 문단 추가:

```java
 *
 * <p>등록(/new, POST)은 SUPER 전용이다. 수정·삭제 경로가 열리지 않도록 기반을
 * CrudController 로 바꾸지 않고 두 핸들러만 둔다.
```

- `populateListModel` 아래에 추가:

```java
    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal ManagerUserDetails me, Model model) {
        requireSuper(me);
        model.addAttribute("form", new CriteriaMetadataForm());
        return viewDir() + "/form";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal ManagerUserDetails me,
                         @Valid @ModelAttribute("form") CriteriaMetadataForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        requireSuper(me);
        if (binding.hasErrors()) return viewDir() + "/form";
        try {
            Criteria saved = service.create(form.getJsondata());
            redirect.addFlashAttribute("flashSuccess", "등록되었습니다. 모든 고객사에서 비활성 상태로 시작합니다.");
            return "redirect:" + basePath() + "/" + saved.getIdx();
        } catch (CriteriaMetadataException e) {
            binding.rejectValue("jsondata", "invalid", e.getMessage());
            return viewDir() + "/form";
        }
    }

    /** 서비스도 막지만, 폼 화면(GET)은 서비스를 거치지 않으므로 여기서도 막는다. */
    private static void requireSuper(ManagerUserDetails me) {
        if (me == null || !me.isSuper()) {
            throw new AccessDeniedException("AAID 정책 등록은 최고 관리자 전용입니다");
        }
    }
```

(`GET /new` 는 `ReadOnlyController` 의 `GET /{id}` 보다 리터럴 경로라 우선 매칭된다.)

`templates/fido/criteria/form.html` 생성:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>AAID(정책) 등록</title></head>
<body>
<main>
  <h1 class="h4 mb-3">AAID(정책) 등록</h1>
  <form th:action="@{/criteria}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <p class="text-secondary small mb-2">
      FIDO UAF 메타데이터 문(JSON)을 붙여 넣으세요. AAID·인증 방식 등은 JSON 에서 읽어 채웁니다.
      등록한 AAID 는 모든 고객사에서 <strong>비활성</strong>으로 시작합니다.
    </p>
    <label class="form-label" for="jsondata">메타데이터 JSON <span class="text-danger">*</span></label>
    <textarea th:field="*{jsondata}" id="jsondata" rows="16" class="form-control fa-mono"
              th:classappend="${#fields.hasErrors('jsondata')} ? 'is-invalid'"></textarea>
    <div class="invalid-feedback" th:errors="*{jsondata}"></div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">등록</button>
      <a th:href="@{/criteria}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

`templates/fido/criteria/list.html`:
- `<html` 태그에 `xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"` 추가.
- `<h1 class="h4 mb-3">AAID(정책) 보기</h1>` 를 아래로 교체:

```html
  <div class="d-flex align-items-center justify-content-between mb-3">
    <h1 class="h4 mb-0">AAID(정책) 보기</h1>
    <a th:href="@{/criteria/new}" class="btn btn-primary btn-sm" sec:authorize="hasRole('SUPER')">
      <i class="bi bi-plus-lg me-1"></i>AAID 추가
    </a>
  </div>
```

- [ ] **Step 8: 통과 확인**

Run: `./gradlew test --tests '*CriteriaControllerWebTest' --tests '*CriteriaQueryServiceTest' --tests '*LayoutWebTest' --tests '*ListRowLinkTest'`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/repository/CriteriaRepository.java \
  src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryService.java \
  src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaMetadataForm.java \
  src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaController.java \
  src/main/resources/templates/fido/criteria/form.html \
  src/main/resources/templates/fido/criteria/list.html \
  src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryServiceTest.java \
  src/test/java/com/crosscert/fidoadmin/fido/web/CriteriaControllerWebTest.java
git commit -m "feat: SUPER 가 메타데이터 JSON 으로 AAID 정책을 등록하고 모든 고객사에서 비활성으로 시작한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 멤버코드 자동 생성

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/MemberCodeGenerator.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverForm.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverController.java`
- Modify: `src/main/resources/templates/fido/appserver/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/MemberCodeGeneratorTest.java` (Create)
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java`

**Interfaces:**
- Consumes: 없음 (Task 1~3 과 독립)
- Produces:
  - `AppserverRepository.existsByMemberCode(String memberCode) : boolean`
  - `@Component MemberCodeGenerator(AppserverRepository repository)` + 테스트용 `MemberCodeGenerator(AppserverRepository repository, RandomGenerator random)`; `public String generate()`; 상수 `LENGTH = 10`, `MAX_ATTEMPTS = 5`
  - `AppserverService` 생성자: `(AppserverRepository repository, AuditLogger audit, TenantContext tenant, ApplicationEventPublisher events, MemberCodeGenerator memberCodes)`

- [ ] **Step 1: 생성기 테스트 작성**

`MemberCodeGeneratorTest.java`:

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemberCodeGeneratorTest {

    AppserverRepository repo = mock(AppserverRepository.class);

    @Test void producesTenUppercaseAlphanumerics() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo);
        for (int i = 0; i < 50; i++) {
            assertThat(generator.generate()).matches("[A-Z0-9]{10}");
        }
    }

    @Test void retriesWhenCodeAlreadyExists() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo, new Random(1));
        when(repo.existsByMemberCode(anyString())).thenReturn(true, true, false);

        String code = generator.generate();

        assertThat(code).matches("[A-Z0-9]{10}");
        verify(repo, times(3)).existsByMemberCode(anyString());
    }

    @Test void givesUpAfterFiveCollisions() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo, new Random(1));
        when(repo.existsByMemberCode(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
        verify(repo, times(MemberCodeGenerator.MAX_ATTEMPTS)).existsByMemberCode(anyString());
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests '*MemberCodeGeneratorTest'`
Expected: FAIL — `MemberCodeGenerator` 없음(컴파일 오류).

- [ ] **Step 3: 생성기 구현**

`AppserverRepository.java` 에 메서드 추가:

```java
    boolean existsByMemberCode(String memberCode);
```

`MemberCodeGenerator.java`:

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 멤버코드(APPSERVER.MEMBER_CODE)를 만든다. 대문자·숫자 10자, 전체 고객사에서 유일.
 *
 * <p>FIDO 서버가 멤버코드를 캐시하고 연동사가 이 값을 설정에 박아 두므로, 사람이 고르지 않고
 * 한 번 정하면 바꾸지 않는다. DB 유니크 제약이 없어 존재 검사 후 다시 뽑는다.
 */
@Component
public class MemberCodeGenerator {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int LENGTH = 10;
    static final int MAX_ATTEMPTS = 5;

    private final AppserverRepository repository;
    private final RandomGenerator random;

    @Autowired
    public MemberCodeGenerator(AppserverRepository repository) {
        this(repository, new SecureRandom());
    }

    MemberCodeGenerator(AppserverRepository repository, RandomGenerator random) {
        this.repository = repository;
        this.random = random;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!repository.existsByMemberCode(code)) return code;
        }
        throw new IllegalStateException("멤버코드를 만들지 못했습니다. 다시 시도해 주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 생성기 테스트 통과 확인**

Run: `./gradlew test --tests '*MemberCodeGeneratorTest'`
Expected: PASS

- [ ] **Step 5: 서비스·웹 테스트를 새 동작으로 고친다**

`AppserverServiceTest.java`:
- 필드 `MemberCodeGenerator memberCodes = mock(MemberCodeGenerator.class);` 추가, 서비스 생성 → `new AppserverService(repo, audit, tenant, events, memberCodes);`
- import 추가: `static org.mockito.Mockito.never`.
- 아래 테스트 추가:

```java
    @Test void createFillsGeneratedMemberCode() {
        login(1L);
        when(memberCodes.generate()).thenReturn("K7Q2M9XA4D");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberId("kbsvr");

        assertThat(service.create(in).getMemberCode()).isEqualTo("K7Q2M9XA4D");
    }

    @Test void createOverwritesAnyMemberCodeFromCaller() {
        login(1L);
        when(memberCodes.generate()).thenReturn("K7Q2M9XA4D");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberCode("TYPED"); in.setMemberId("kbsvr");

        assertThat(service.create(in).getMemberCode()).isEqualTo("K7Q2M9XA4D");
    }

    @Test void updateNeverGeneratesMemberCode() {
        login(1L);
        Appserver existing = new Appserver(); existing.setIdx(3L); existing.setCompanyIdx(1L); existing.setMemberCode("OLD0000001");
        when(repo.findById(3L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(3L, a -> a.setNote("변경"));

        assertThat(existing.getMemberCode()).isEqualTo("OLD0000001");
        verify(memberCodes, never()).generate();
    }
```

(멤버코드는 사람이 정하지 않으므로 `applyDefaults` 는 비어 있는지와 무관하게 항상 새로 채운다 — 스펙의 "폼으로 들어온 값은 등록·수정 모두 무시"를 서비스 경계에서 보장한다.)

`AppserverControllerWebTest.java`:
- import 추가: `org.mockito.ArgumentCaptor`, `static org.mockito.Mockito.verify`, `static org.mockito.Mockito.never`, `static org.assertj.core.api.Assertions.assertThat`.
- `memberCodeOverByteLimitIsRejectedWithMessage` 와 `createRejectsDuplicateMemberCodeAndId` 를 지운다(등록 폼에 멤버코드가 없다).
- `blankMemberIdShowsFormAgain` 의 `.param("memberCode", "KB01")` 를 지운다.
- `updateExcludesSelfFromDuplicateCheck` 를 아래로 교체:

```java
    /** 수정 폼의 읽기 전용 멤버코드를 변조해도 DB 값이 그대로이고, 중복 검사도 DB 값으로 돈다. */
    @Test void updateIgnoresPostedMemberCodeAndChecksWithStoredCode() throws Exception {
        Appserver stored = server(5L); // memberCode "KB01"
        when(service.get(5L)).thenReturn(stored);
        when(service.existsDuplicate("KB01", "ID01", 5L)).thenReturn(false);
        when(service.update(org.mockito.ArgumentMatchers.eq(5L), any())).thenAnswer(inv -> {
            java.util.function.Consumer<Appserver> mutator = inv.getArgument(1);
            mutator.accept(stored);
            return stored;
        });
        mvc.perform(post("/appservers/5").with(user(companyUser)).with(csrf())
                .param("memberCode", "HACK").param("memberId", "ID01").param("type", "use"))
            .andExpect(status().is3xxRedirection());
        verify(service).existsDuplicate("KB01", "ID01", 5L);
        assertThat(stored.getMemberCode()).isEqualTo("KB01");
        assertThat(stored.getMemberId()).isEqualTo("ID01");
    }

    @Test void updateRejectsDuplicateWithStoredCode() throws Exception {
        when(service.get(5L)).thenReturn(server(5L));
        when(service.existsDuplicate("KB01", "ID01", 5L)).thenReturn(true);
        mvc.perform(post("/appservers/5").with(user(companyUser)).with(csrf())
                .param("memberId", "ID01").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
            .andExpect(content().string(containsString("이미 등록된 코드와 ID값 입니다.")));
    }
```

- `createRedirectsToDetail` 을 아래로 교체:

```java
    @Test void createIgnoresPostedMemberCodeAndRedirects() throws Exception {
        when(service.create(any())).thenReturn(server(9L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "TYPED").param("memberId", "kbsvr01").param("type", "use"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appservers/9"));
        ArgumentCaptor<Appserver> captor = ArgumentCaptor.forClass(Appserver.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().getMemberCode()).isNull();
        verify(service, never()).existsDuplicate(any(), any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test void newFormHasNoMemberCodeInput() throws Exception {
        mvc.perform(get("/appservers/new").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"memberCode\""))))
            .andExpect(content().string(containsString("저장 시 자동 생성됩니다")));
    }

    @Test void editFormShowsMemberCodeReadOnly() throws Exception {
        when(service.get(2L)).thenReturn(server(2L));
        mvc.perform(get("/appservers/2/edit").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("KB01")))
            .andExpect(content().string(containsString("readonly")));
    }
```

- [ ] **Step 6: 실패 확인**

Run: `./gradlew test --tests '*AppserverServiceTest' --tests '*AppserverControllerWebTest'`
Expected: FAIL — 서비스 생성자 인자 수(컴파일 오류).

- [ ] **Step 7: 서비스·폼·컨트롤러·템플릿 구현**

`AppserverService.java`:

```java
    private final ApplicationEventPublisher events;
    private final MemberCodeGenerator memberCodes;

    public AppserverService(AppserverRepository repository, AuditLogger audit, TenantContext tenant,
                        ApplicationEventPublisher events, MemberCodeGenerator memberCodes) {
        super(repository, audit, tenant);
        this.events = events;
        this.memberCodes = memberCodes;
    }
```

`applyDefaults` 교체:

```java
    /**
     * 등록 때만 불린다(CrudService.create). 멤버코드는 사람이 정하지 않는다 — 호출자가 무엇을
     * 넘겼든 새로 만든다. 수정 경로는 이 훅을 거치지 않으므로 한 번 정한 코드는 바뀌지 않는다.
     */
    @Override protected void applyDefaults(Appserver e) {
        if (e.getType() == null || e.getType().isBlank()) e.setType("use");
        e.setMemberCode(memberCodes.generate());
    }
```

`AppserverForm.java`:

```java
/**
 * APPSERVER 입력 폼. MEMBER_ID, TYPE 은 ERD NOT NULL.
 * MEMBER_CODE 는 서버가 만들고 바꾸지 않는다 — 수정 화면에 보여 주기만 하고 엔티티에 옮기지 않는다.
 */
@Getter @Setter
public class AppserverForm {
    /** 표시 전용. applyTo 가 무시한다. */
    private String memberCode;
    @NotBlank @ByteSize(max = 32) private String memberId;
    @NotBlank @ByteSize(max = 10) private String type = "use";
    @ByteSize(max = 128) private String note;
    /** SUPER 만 선택. COMPANY 는 서비스가 자기 고객사로 강제한다. */
    private Long companyIdx;

    public static AppserverForm from(Appserver a) {
        AppserverForm f = new AppserverForm();
        f.memberCode = a.getMemberCode(); f.memberId = a.getMemberId(); f.type = a.getType();
        f.note = a.getNote(); f.companyIdx = a.getCompanyIdx();
        return f;
    }

    public void applyTo(Appserver a) {
        a.setMemberId(memberId); a.setType(type);
        a.setNote(note); a.setCompanyIdx(companyIdx);
    }
}
```

`AppserverController.java` 의 `validate` 교체:

```java
    /**
     * (멤버코드, 멤버키) 중복은 수정 때만 본다. 등록 때는 멤버코드가 아직 없고 생성기가 전체 유일성을 보장한다.
     * 멤버코드는 폼 값이 아니라 저장된 값으로 본다 — 읽기 전용 칸은 변조될 수 있다.
     */
    @Override protected void validate(AppserverForm form, Long id, BindingResult binding) {
        if (id == null) return;
        String storedCode = service.get(id).getMemberCode();
        if (service.existsDuplicate(storedCode, form.getMemberId(), id)) {
            binding.rejectValue("memberId", "duplicate", "이미 등록된 코드와 ID값 입니다.");
        }
    }

    /** 수정 폼이 오류로 다시 그려질 때 변조된 멤버코드가 아니라 저장된 값을 보여 준다. */
    @Override protected void populateFormModel(Model model) {
        Object id = model.getAttribute("id");
        Object form = model.getAttribute("form");
        if (id instanceof Long idx && form instanceof AppserverForm f) {
            f.setMemberCode(service.get(idx).getMemberCode());
        }
    }
```

`templates/fido/appserver/form.html` 의 멤버코드 칸(`<div class="col-md-6">` 첫 블록)을 아래로 교체:

```html
      <div class="col-md-6">
        <label class="form-label">멤버코드</label>
        <input th:if="${isNew}" type="text" class="form-control" value="저장 시 자동 생성됩니다" disabled>
        <input th:unless="${isNew}" type="text" class="form-control fa-mono" th:value="*{memberCode}" readonly>
      </div>
```

(수정 화면의 읽기 전용 칸에 `name` 을 붙이지 않는다 — 보내지도 않는다. 등록 화면 안내 칸은 `disabled` 라 전송되지 않는다.)

- [ ] **Step 8: 통과 확인**

Run: `./gradlew test --tests '*MemberCodeGeneratorTest' --tests '*AppserverServiceTest' --tests '*AppserverControllerWebTest'`
Expected: PASS

- [ ] **Step 9: 전체 테스트**

Run: `./gradlew test`
Expected: PASS (Docker 없으면 `integration` 패키지 환경 실패만 허용 — 보고)

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/service/MemberCodeGenerator.java \
  src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java \
  src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java \
  src/main/java/com/crosscert/fidoadmin/fido/web/AppserverForm.java \
  src/main/java/com/crosscert/fidoadmin/fido/web/AppserverController.java \
  src/main/resources/templates/fido/appserver/form.html \
  src/test/java/com/crosscert/fidoadmin/fido/service/MemberCodeGeneratorTest.java \
  src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java \
  src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java
git commit -m "feat: 멤버코드를 등록 때 서버가 랜덤 생성하고 이후 바꾸지 않는다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
