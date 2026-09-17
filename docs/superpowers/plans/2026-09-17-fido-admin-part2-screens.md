# FIDO Admin 2부 — 업무 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1부 기반(`CrudController`/`CrudService`/`ReadOnlyController`) 위에 고객사·운영자·FIDO·FIDO2·로그 메뉴의 업무 화면 17개(CRUD 8, 조회 8, 조회+상태변경 1)를 추가한다. 1부의 4개(대시보드, 고객사, 감사 로그, 비밀번호 변경)와 3부의 8개를 더하면 설계 5.2 의 29개가 된다. 시스템 메뉴 8개는 3부(`2026-09-17-fido-admin-part3-system.md`)에서 다룬다.

**Architecture:** 화면마다 `SearchForm` 하위 클래스, `CrudService` 하위 서비스, `CrudController`/`ReadOnlyController` 하위 컨트롤러, Thymeleaf 템플릿(list/detail/form)을 같은 패턴으로 추가한다. 할당형 PK 테이블(CCFA_FDS_POLICY, FIDO2_DEMO_ACCESS_CODE 등)은 `save()`가 MERGE 로 동작해 기존 행을 덮어쓸 수 있으므로, Task 1 에서 존재 검사 + `persist` 기반 삽입 전용 경로 `AssignedIdCrudService` 를 먼저 만든다. 민감 컬럼(USER_PW, PUBKEY, CERTIFICATE)과 CLOB 은 출력 DTO(record) 로 변환해 뷰에 노출하지 않는다.

**Tech Stack:** Java 17, Spring Boot 3.5.3 (Web, Thymeleaf, Security, Data JPA, Validation), Lombok, Bootstrap 5.3.7 (WebJars), JUnit 5 + Mockito + `@WebMvcTest`, Testcontainers oracle-free (통합 테스트).

**Spec:** `docs/superpowers/specs/2026-09-16-fido-admin-design.md` (5.2 메뉴와 화면 표가 이 계획의 근거). 1부 계획: `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md`.

## Global Constraints

- Java 17, Spring Boot 3.5.x, Gradle Wrapper 8.14, Groovy DSL. 새 의존성을 추가하지 않는다(JSON 정리 출력은 `spring-boot-starter-web` 에 포함된 Jackson 사용).
- DB는 Oracle. **ERD 이외의 테이블·컬럼을 추가·수정·삭제하지 않는다.** `ddl-auto: none`, `open-in-view: false`. 엔티티는 1부에서 완성되었으므로 **엔티티 파일은 수정하지 않는다**(`ErdConformanceTest` 가 지킨다).
- 뷰는 Thymeleaf만. 외부 CSS/JS/폰트 호출 금지. JS `alert/confirm/prompt` 금지. 삭제·상태 변경 확인은 `data-confirm-form` + Bootstrap 모달(1부 `admin.js`).
- 테넌트 격리는 기반이 처리한다. `companyIdxAttribute()` 는 **반드시** 그 엔티티의 `COMPANY_IDX` 속성명을 돌려주고, `COMPANY_IDX` 가 없는 테이블만 `null` 을 돌려준다(그 화면은 SUPER 전용이 된다).
- **설계 3.3 적용 결정:** `COMPANY_IDX` 가 없는 CRITERIA, FIDO2_METADATA, FIDO2_CREDENTIAL_PARAMS, FIDO2_DEMO_ACCESS_CODE 화면은 SUPER 전용이다. 설계 5.2 표에는 (S) 표시가 없지만 3.3 규칙과 1부 `CrudService.requireSuperForGlobalTable()` 이 우선한다. Task 1 에서 `MenuRegistry`(superOnly=true)와 `SecurityConfig`(`/criteria/**`, `/fido2/**` → `hasRole("SUPER")`)를 함께 바꾼다.
- 문자열 길이 검증은 `@Size` 가 아니라 **`@ByteSize(max = ERD 길이)`** 를 쓴다(DB 가 BYTE 의미, 한글 1자 3바이트).
- `toEntity(form)` 에서 **식별자를 폼 값으로 채우지 않는다.** 예외는 할당형 PK 테이블이며, 그 화면은 반드시 `AssignedIdCrudService` 를 상속한다.
- 정렬 가능한 컬럼은 `sortableProperties()` 에 선언한다. `idx` 가 없는 엔티티는 `defaultSort()` 와 `sortableProperties()` 를 **반드시** 재정의한다(기본값이 `idx` 라 조회 시 500).
- 민감 컬럼(`USER_PW`, `PUBKEY`, `CERTIFICATE`)은 출력 DTO 에서 제외하거나 앞 16자만 표시. CLOB 컬럼은 목록 DTO 에서 제외하고 상세에서만 보인다(1부 기반이 Specification 페이징이라 인터페이스 프로젝션 대신 DTO 변환으로 처리한다).
- 폼 화면에는 `#fields.allErrors()` 로 모든 검증 오류를 표시한다. 등록·수정·삭제·상태 변경 성공 시 플래시 메시지.
- 목록은 대표 컬럼 6~8개, 상세는 전체 컬럼. 조회 전용 화면에는 등록·수정·삭제 버튼 없음. 검색 폼은 GET 쿼리스트링.
- 패키지 루트 `com.crosscert.fidoadmin`. 템플릿 경로 `templates/<도메인>/<테이블>/list.html, detail.html, form.html`. UI 언어 한국어.
- 커밋 메시지는 한국어 요약 + `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 태스크마다 커밋한다.
- 테스트 실행: 단일 클래스는 `./gradlew test --tests '<FQCN>'`, 전체는 `./gradlew test`. 통합 테스트(`RepositoryIntegrationTest`)는 Docker 가 있을 때만 돈다.

## 공통 작성 규칙 (모든 태스크에 적용)

### 컨트롤러 웹 테스트 골격

모든 `@WebMvcTest` 는 1부 `CompanyControllerWebTest` 와 같은 골격을 쓴다. 인증 핸들러 4개는 `SecurityConfig` 가 요구하므로 항상 `@MockitoBean` 으로 둔다.

```java
@WebMvcTest(controllers = XxxController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class XxxControllerWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean XxxService service;
    @MockitoBean CompanyLookup companies;          // 컨트롤러가 주입받을 때만
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
}
```

- 목록 테스트는 `when(service.defaultSort()).thenReturn(Sort.by("idx"))` 를 반드시 스텁한다(모의 객체의 기본 반환 `null` 이 `toPageable` 에서 NPE). `sortableProperties()` 는 모의 객체가 빈 Set 을 돌려주므로 스텁하지 않아도 된다.
- 등록 리다이렉트 테스트는 `service.create(any())` 와 `service.idOf(any())` 를 함께 스텁한다.
- 서비스 단위 테스트는 1부 `CompanyServiceTest` 처럼 `SecurityContextHolder` 에 `ManagerUserDetails` 를 넣어 로그인 상태를 만들고, `@AfterEach` 에서 `clearContext()` 한다.

### 템플릿 골격

- 목록: `company/company/list.html`(CRUD) 또는 `log/audit/list.html`(조회 전용) 을 그대로 따른다. 테이블 클래스 `table table-sm table-hover fa-table mb-0`, 빈 목록 행 `데이터가 없습니다.`, 하단 `총 N건` + `fragments/pagination :: pagination(${page}, ${basePath})`.
- SUPER 에게만 보이는 고객사 select 는 `sec:authorize="hasRole('SUPER')"` 로 감싸고, 컨트롤러는 `if (TenantContext.isSuper()) model.addAttribute("companies", companies.all())` 로 채운다.
- 상세: `table table-sm fa-detail mb-0`. 긴 문자열은 `class="fa-pre"`, 해시·키는 `class="fa-mono"`. 일시는 `#temporals.format(x, 'yyyy-MM-dd HH:mm:ss')`.
- 폼: `th:object="${form}"`, 상단 `#fields.allErrors()` 블록, 필수 항목 `<span class="text-danger">*</span>`, 저장/취소 버튼. `datetime-local` 입력은 `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")`.
- 삭제 버튼: `<form ... id="deleteForm">` + `<button type="button" data-confirm-form="deleteForm">`.

### 출력 DTO

`web` 패키지에 `record XxxRow(...)`(목록) / `record XxxView(...)`(상세) 를 두고 `toListView`/`toDetailView` 에서 변환한다. 민감 컬럼이 없는 단순 테이블은 기본 구현(엔티티 그대로)을 써도 된다.

## 파일 구조 (2부에서 생성·수정)

```
src/main/java/com/crosscert/fidoadmin/
├── common/AssignedIdCrudService.java          (신규) 할당형 PK 삽입 전용 경로
├── common/CrudService.java                    (수정) insert() 훅 추가
├── common/CrudController.java                 (수정) validate() 훅 추가
├── common/JsonPretty.java                     (신규) JSON 정리 출력
├── common/MenuRegistry.java                   (수정) CRITERIA·FIDO2 항목 superOnly=true
├── config/SecurityConfig.java                 (수정) /criteria/**, /fido2/** SUPER
├── company/repository/CcfaFdsPolicyRepository.java, CcfaLicenseRepository.java
├── company/service/FdsPolicyService.java, LicenseService.java
├── company/web/FdsPolicyController.java, FdsPolicyForm.java, FdsPolicySearchForm.java,
│               LicenseController.java, LicenseForm.java, LicenseSearchForm.java
├── manager/service/ManagerService.java
├── manager/web/ManagerController.java, ManagerForm.java, ManagerSearchForm.java, ManagerRow.java, ManagerView.java
├── fido/repository/AppserverRepository.java, ChallengeRepository.java, SignRepository.java,
│                   TransactionhashRepository.java, TransactionConfirmationRepository.java, CriteriaRepository.java
├── fido/service/AppidService.java, AppserverService.java, UserinfoService.java, ChallengeQueryService.java,
│                SignQueryService.java, TransactionhashQueryService.java, TransactionConfirmationQueryService.java,
│                CriteriaQueryService.java
├── fido/web/AppidController.java, AppidForm.java, AppidSearchForm.java,
│            AppserverController.java, AppserverForm.java, AppserverSearchForm.java,
│            UserController.java, UserSearchForm.java, UserRow.java, UserView.java,
│            ChallengeController.java, ChallengeSearchForm.java,
│            SignController.java, SignSearchForm.java, SignRow.java,
│            TransactionhashController.java, TransactionhashSearchForm.java, TransactionhashRow.java,
│            TransactionConfirmationController.java, TransactionConfirmationSearchForm.java, TransactionConfirmationRow.java,
│            CriteriaController.java, CriteriaSearchForm.java, CriteriaRow.java
├── fido2/repository/Fido2MetadataRepository.java, Fido2CredentialParamsRepository.java, Fido2DemoAccessCodeRepository.java
├── fido2/service/Fido2MetadataService.java, Fido2CredentialParamsService.java, Fido2DemoAccessCodeService.java
├── fido2/web/Fido2MetadataController.java, Fido2MetadataForm.java, Fido2MetadataSearchForm.java, Fido2MetadataRow.java,
│             Fido2CredentialParamsController.java, Fido2CredentialParamsForm.java, Fido2CredentialParamsSearchForm.java,
│             Fido2DemoAccessCodeController.java, Fido2DemoAccessCodeForm.java, Fido2DemoAccessCodeSearchForm.java,
│             Fido2DemoAccessCodeView.java, EpochSeconds.java
├── log/repository/FidoLogsRepository.java, CcfaExceptionsRepository.java, CcfaMailingRepository.java
├── log/service/FidoLogQueryService.java, ExceptionLogQueryService.java, MailingQueryService.java
└── log/web/FidoLogController.java, FidoLogSearchForm.java, FidoLogRow.java, FidoLogView.java,
             ExceptionLogController.java, ExceptionLogSearchForm.java, ExceptionLogRow.java,
             MailingController.java, MailingSearchForm.java
src/main/resources/templates/
├── company/fds-policy/list.html, detail.html, form.html
├── company/license/list.html, detail.html, form.html
├── manager/manager/list.html, detail.html, form.html
├── fido/appid/…, fido/appserver/…(list, detail, form)
├── fido/user/list.html, detail.html
├── fido/challenge/, fido/sign/, fido/transactionhash/, fido/transaction-confirmation/, fido/criteria/ (list, detail)
├── fido2/metadata/, fido2/credential-params/, fido2/demo-access-code/ (list, detail, form)
└── log/fido/, log/exceptions/, log/mailing/ (list, detail)
src/test/java/... 각 태스크의 *ControllerWebTest, *ServiceTest, common/AssignedIdCrudServiceTest, common/JsonPrettyTest
```

---

## Task 1: 공통 기반 보강 — 할당형 PK 삽입 경로, 폼 검증 훅, JSON 정리, 권한 보정

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/AssignedIdCrudService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/common/JsonPretty.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/CrudService.java` (`create()` 의 `repository.save` → `insert()` 훅)
- Modify: `src/main/java/com/crosscert/fidoadmin/common/CrudController.java` (`validate()` 훅)
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java` (CRITERIA·FIDO2 4개 항목 `superOnly=true`)
- Modify: `src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java` (`/criteria/**`, `/fido2/**` 추가)
- Modify: `src/main/resources/static/js/admin.js` (확인 버튼 문구 `data-confirm-ok`)
- Modify: `src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/AssignedIdCrudServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/JsonPrettyTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/CrudControllerValidateHookTest.java`

**Interfaces:**
- Consumes: 1부 `CrudService<E, ID, S>`, `AdminRepository<E, ID>`, `AuditLogger`, `TenantContext`.
- Produces:
  - `CrudService.insert(E): E` — `protected`, 기본 구현은 `repository.save(entity)`. `create()` 는 이제 `insert()` 를 부른다.
  - `AssignedIdCrudService<E, ID, S> extends CrudService<E, ID, S>` — 생성자 `(AdminRepository<E, ID>, AuditLogger, EntityManager)`, 추상 `protected ID assignedId(E entity)`. `insert()` 는 식별자 null 이면 `IllegalArgumentException`, 이미 존재하면 `DataIntegrityViolationException`, 아니면 `em.persist` + `em.flush`.
  - `CrudController.validate(F form, boolean isNew, BindingResult binding)` — `protected`, 기본 빈 구현. `@Valid` 통과 후 서비스 호출 전에 불린다. `binding.rejectValue(...)` 로 오류를 넣으면 폼을 다시 그린다.
  - `JsonPretty.pretty(String raw): String` — JSON 이면 들여쓰기 2칸으로 정리, 아니면(파싱 실패·null·공백) 원문 그대로.
  - `MenuRegistry.ALL` 의 `/criteria`, `/fido2/metadata`, `/fido2/credential-params`, `/fido2/demo-access-codes` 항목은 `superOnly=true`.
  - `admin.js`: `data-confirm-form` 버튼에 `data-confirm-ok="문구"` 를 주면 확인 버튼 문구가 바뀌고 파랑(btn-primary)이 된다. 없으면 "삭제"(빨강) 그대로.

- [ ] **Step 1: AssignedIdCrudService 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/common/AssignedIdCrudServiceTest.java`

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AssignedIdCrudServiceTest {

    interface InfoRepo extends AdminRepository<CcfaSystemInfo, String> {}

    InfoRepo repo = mock(InfoRepo.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);

    AssignedIdCrudService<CcfaSystemInfo, String, SearchForm> service =
        new AssignedIdCrudService<>(repo, audit, em) {
            @Override protected Specification<CcfaSystemInfo> toSpecification(SearchForm f) { return null; }
            @Override protected String companyIdxAttribute() { return null; }
            @Override protected Long companyIdxOf(CcfaSystemInfo e) { return null; }
            @Override protected void setCompanyIdx(CcfaSystemInfo e, Long c) {}
            @Override public String idOf(CcfaSystemInfo e) { return e.getPropKey(); }
            @Override protected String tableName() { return "CCFA_SYSTEM_INFO"; }
            @Override protected String assignedId(CcfaSystemInfo e) { return e.getPropKey(); }
        };

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemInfo info(String key) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue("v"); return i; }

    /** 핵심: 기존 행이 있으면 save() 의 MERGE 로 덮어쓰지 않고 거부한다. */
    @Test void createRejectsExistingKeyWithoutTouchingRow() {
        when(repo.existsById("VERSION")).thenReturn(true);
        assertThatThrownBy(() -> service.create(info("VERSION")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("VERSION");
        verify(em, never()).persist(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void createPersistsNewKeyAndAudits() {
        when(repo.existsById("NEW_KEY")).thenReturn(false);
        CcfaSystemInfo saved = service.create(info("NEW_KEY"));
        assertThat(saved.getPropKey()).isEqualTo("NEW_KEY");
        verify(em).persist(saved);
        verify(em).flush();
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_INFO CREATE NEW_KEY");
    }

    @Test void createRejectsBlankKey() {
        assertThatThrownBy(() -> service.create(info(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(info("  "))).isInstanceOf(IllegalArgumentException.class);
        verify(em, never()).persist(any());
    }

    /** 존재 검사와 INSERT 사이에 다른 세션이 같은 키를 넣은 경우(경쟁) 도 같은 예외로 화면에 전달된다. */
    @Test void persistFailureBecomesDataIntegrityViolation() {
        when(repo.existsById("RACE")).thenReturn(false);
        Mockito.doThrow(new PersistenceException("ORA-00001")).when(em).flush();
        assertThatThrownBy(() -> service.create(info("RACE"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(audit, never()).log(any(), any());
    }

    /** update 는 기존 경로(get → mutator → save) 그대로다. */
    @Test void updateStillUsesSave() {
        when(repo.findById("VERSION")).thenReturn(java.util.Optional.of(info("VERSION")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaSystemInfo out = service.update("VERSION", i -> i.setPropValue("2"));
        assertThat(out.getPropValue()).isEqualTo("2");
        verify(repo).save(any());
        verify(em, never()).persist(any());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.AssignedIdCrudServiceTest'`
Expected: FAIL — `AssignedIdCrudService` 클래스 없음(컴파일 오류).

- [ ] **Step 3: CrudService 에 insert() 훅 추가**

`src/main/java/com/crosscert/fidoadmin/common/CrudService.java` 의 `create()` 를 아래처럼 바꾸고 훅을 추가한다.

```java
    /**
     * 신규 엔티티 저장. 기본은 repository.save(). 할당형 PK 테이블은
     * {@link AssignedIdCrudService} 가 존재 검사 + persist 로 바꾼다
     * (save() 는 식별자가 있으면 MERGE 로 동작해 기존 행을 덮어쓴다).
     */
    protected E insert(E entity) { return repository.save(entity); }

    @Transactional
    public E create(E entity) {
        requireSuperForGlobalTable();
        if (companyIdxAttribute() != null && !TenantContext.isSuper()) {
            setCompanyIdx(entity, TenantContext.companyIdx());
        }
        applyDefaults(entity);
        touchCreated(entity, LocalDateTime.now());
        E saved = insert(entity);
        audit.log(AuditType.CREATE, tableName() + " CREATE " + idOf(saved));
        return saved;
    }
```

- [ ] **Step 4: AssignedIdCrudService 작성**

`src/main/java/com/crosscert/fidoadmin/common/AssignedIdCrudService.java`

```java
package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.audit.AuditLogger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 할당형 PK(문자열 PK, COMPANY_IDX PK, 복합키) 테이블용 CRUD 서비스.
 *
 * Spring Data 의 save() 는 식별자가 채워진 엔티티를 "기존 행" 으로 보고 MERGE 한다.
 * 등록 화면에서 이미 있는 키를 입력하면 오류 없이 기존 행이 덮어써지므로,
 * 등록은 존재 검사 후 EntityManager.persist 로만 수행한다. 수정·삭제는 상위 클래스 그대로다.
 */
public abstract class AssignedIdCrudService<E, ID, S extends SearchForm> extends CrudService<E, ID, S> {

    protected final EntityManager em;

    protected AssignedIdCrudService(AdminRepository<E, ID> repository, AuditLogger audit, EntityManager em) {
        super(repository, audit);
        this.em = em;
    }

    /** 폼에서 채워진 식별자. null/공백이면 등록을 거부한다. */
    protected abstract ID assignedId(E entity);

    @Override
    protected E insert(E entity) {
        ID id = assignedId(entity);
        if (id == null || (id instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(tableName() + " 식별자가 비어 있습니다");
        }
        if (repository.existsById(id)) {
            throw new DataIntegrityViolationException(tableName() + " " + id + " 은(는) 이미 존재합니다");
        }
        try {
            em.persist(entity);
            em.flush(); // 존재 검사와 INSERT 사이의 경쟁은 PK 제약이 잡는다. 여기서 바로 드러나게 한다.
        } catch (PersistenceException e) {
            throw new DataIntegrityViolationException(tableName() + " " + id + " 저장 실패", e);
        }
        return entity;
    }
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.AssignedIdCrudServiceTest' --tests 'com.crosscert.fidoadmin.common.CrudServiceTest'`
Expected: PASS (기존 `CrudServiceTest` 도 그대로 통과).

- [ ] **Step 6: CrudController validate() 훅 실패 테스트 작성**

운영자 등록(Task 4)에서 "등록 시에만 비밀번호 필수" 처럼 `isNew` 에 따라 달라지는 검증이 필요하다. 훅을 먼저 만든다.

`src/test/java/com/crosscert/fidoadmin/common/CrudControllerValidateHookTest.java`

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class CrudControllerValidateHookTest {

    /** BindingResult.rejectValue 는 빈 프로퍼티(getter/setter)를 요구한다. 테스트 코드는 Lombok 을 쓰지 않는다. */
    public static class Form {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @SuppressWarnings("unchecked")
    CrudService<CcfaLicense, Long, SearchForm> service = mock(CrudService.class);

    /** validate() 가 오류를 넣으면 서비스는 호출되지 않고 폼 뷰로 돌아간다. */
    CrudController<CcfaLicense, Long, Form, SearchForm> controller = new CrudController<>() {
        @Override protected CrudService<CcfaLicense, Long, SearchForm> service() { return service; }
        @Override protected String basePath() { return "/x"; }
        @Override protected String viewDir() { return "x/x"; }
        @Override protected SearchForm newSearchForm() { return new SearchForm(); }
        @Override protected Form newForm() { return new Form(); }
        @Override protected Form toForm(CcfaLicense e) { return new Form(); }
        @Override protected CcfaLicense toEntity(Form f) { return new CcfaLicense(); }
        @Override protected void applyForm(Form f, CcfaLicense e) {}
        @Override protected void validate(Form form, boolean isNew, BindingResult binding) {
            if (isNew && form.getName() == null) binding.rejectValue("name", "required", "이름은 필수입니다.");
        }
    };

    @Test void createStopsWhenValidateRejects() {
        Form form = new Form();
        BindingResult binding = new BeanPropertyBindingResult(form, "form");
        String view = controller.create(form, binding, new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("x/x/form");
        assertThat(binding.getFieldError("name").getDefaultMessage()).isEqualTo("이름은 필수입니다.");
        verify(service, never()).create(any());
    }

    @Test void updateSkipsIsNewOnlyRule() {
        Form form = new Form();
        BindingResult binding = new BeanPropertyBindingResult(form, "form");
        String view = controller.update(9L, form, binding, new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("redirect:/x/9");
        verify(service).update(any(), any());
    }
}
```

- [ ] **Step 7: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.CrudControllerValidateHookTest'`
Expected: FAIL — `validate` 메서드 없음.

- [ ] **Step 8: CrudController 에 validate() 훅 추가**

`src/main/java/com/crosscert/fidoadmin/common/CrudController.java` — 훅 선언을 `populateDetailModel` 아래에 추가하고, `create`/`update` 에서 `@Valid` 결과 확인 직전에 호출한다.

```java
    /**
     * Bean Validation 으로 표현하기 어려운 검증(등록 시에만 필수, 두 필드 비교, JSON 형식 등).
     * binding.rejectValue / reject 로 오류를 넣으면 폼을 다시 그린다.
     */
    protected void validate(F form, boolean isNew, BindingResult binding) {}
```

```java
    @PostMapping
    public String create(@Valid @ModelAttribute("form") F form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        validate(form, true, binding);
        if (binding.hasErrors()) return backToForm(model, true);
        // ... 이하 기존 코드 그대로
```

```java
    @PostMapping("/{id}")
    public String update(@PathVariable ID id, @Valid @ModelAttribute("form") F form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("id", id);
        validate(form, false, binding);
        if (binding.hasErrors()) return backToForm(model, false);
        // ... 이하 기존 코드 그대로
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.CrudControllerValidateHookTest' --tests 'com.crosscert.fidoadmin.company.web.CompanyControllerWebTest'`
Expected: PASS.

- [ ] **Step 10: JsonPretty 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/common/JsonPrettyTest.java`

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonPrettyTest {

    @Test void prettyPrintsObject() {
        String out = JsonPretty.pretty("{\"op\":\"Auth\",\"result\":\"1200\"}");
        assertThat(out).isEqualTo("{\n  \"op\" : \"Auth\",\n  \"result\" : \"1200\"\n}");
    }

    @Test void returnsRawWhenNotJson() {
        assertThat(JsonPretty.pretty("not json {")).isEqualTo("not json {");
    }

    @Test void passesThroughNullAndBlank() {
        assertThat(JsonPretty.pretty(null)).isNull();
        assertThat(JsonPretty.pretty("  ")).isEqualTo("  ");
    }

    @Test void isValidJsonDetectsStructure() {
        assertThat(JsonPretty.isValidJson("{\"a\":1}")).isTrue();
        assertThat(JsonPretty.isValidJson("[1,2]")).isTrue();
        assertThat(JsonPretty.isValidJson("{a:1}")).isFalse();
        assertThat(JsonPretty.isValidJson("")).isFalse();
    }
}
```

- [ ] **Step 11: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.JsonPrettyTest'`
Expected: FAIL — `JsonPretty` 없음.

- [ ] **Step 12: JsonPretty 작성**

`src/main/java/com/crosscert/fidoadmin/common/JsonPretty.java`

```java
package com.crosscert.fidoadmin.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** CLOB 에 담긴 JSON(FIDO_LOGS.JSONDATA, CRITERIA.JSONDATA 등)을 상세 화면용으로 정리한다. */
public final class JsonPretty {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonPretty() {}

    /** JSON 이면 들여쓰기해 돌려주고, 아니면 원문 그대로 돌려준다(절대 예외를 던지지 않는다). */
    public static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node == null || !node.isContainerNode()) return raw;
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    /** 객체 또는 배열 형태의 JSON 인지. 폼 검증(JSONDATA 편집)에 쓴다. */
    public static boolean isValidJson(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            JsonNode node = MAPPER.readTree(raw);
            return node != null && node.isContainerNode();
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 13: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.JsonPrettyTest'`
Expected: PASS.

- [ ] **Step 14: MenuRegistry·SecurityConfig 권한 보정과 테스트**

`src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java` 의 `companyDoesNotSeeSuperOnlyMenus` 를 아래로 바꾼다(CRITERIA·FIDO2 는 COMPANY 에게 보이지 않아야 한다).

```java
    @Test void companyDoesNotSeeSuperOnlyMenus() {
        assertThat(registry.itemsFor(false)).noneMatch(MenuItem::superOnly);
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href)
            .contains("/", "/appids", "/users", "/logs/fido", "/fds-policies")
            .doesNotContain("/companies", "/managers", "/system/props",
                "/criteria", "/fido2/metadata", "/fido2/credential-params", "/fido2/demo-access-codes");
    }

    /** COMPANY_IDX 가 없는 테이블의 화면은 SUPER 전용(설계 3.3). 그룹은 유지된다. */
    @Test void companySeesNoFido2Group() {
        var groups = MenuRegistry.groups(registry.itemsFor(false));
        assertThat(groups.keySet()).containsExactly("대시보드", "고객사", "운영자", "FIDO", "로그");
    }
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.MenuRegistryTest'`
Expected: FAIL — `/criteria`, `/fido2/...` 가 COMPANY 목록에 포함됨.

`src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java` 에서 네 항목을 바꾼다.

```java
        new MenuItem("FIDO", "인증기기 기준", "/criteria", true),
        new MenuItem("FIDO2", "메타데이터", "/fido2/metadata", true),
        new MenuItem("FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true),
        new MenuItem("FIDO2", "데모 접근코드", "/fido2/demo-access-codes", true),
```

Javadoc 도 갱신한다: `/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. COMPANY_IDX 가 없는 테이블(CRITERIA, FIDO2_*, 시스템)의 화면은 SUPER 전용이다(설계 3.3). */`

`src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java` 의 SUPER 매처를 바꾼다.

```java
                .requestMatchers("/companies/**", "/licenses/**", "/managers/**", "/system/**",
                                 "/criteria/**", "/fido2/**").hasRole("SUPER")
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.MenuRegistryTest' --tests 'com.crosscert.fidoadmin.common.LayoutWebTest'`
Expected: PASS.

- [ ] **Step 14b: 확인 모달 버튼 문구 지원 (admin.js)**

2부의 상태 변경(Task 7)·잠금 해제(Task 4) 버튼도 같은 확인 모달을 쓴다. 확인 버튼이 항상 "삭제"(빨강) 로 보이지 않도록 버튼별 문구 `data-confirm-ok` 를 지원한다. `src/main/resources/static/js/admin.js` 전체를 아래로 교체한다. 기존 삭제 버튼(속성 없음)은 동작이 바뀌지 않는다.

```javascript
document.addEventListener('DOMContentLoaded', function () {
  var modalEl = document.getElementById('confirmModal');
  if (!modalEl || typeof bootstrap === 'undefined') return;
  var modal = new bootstrap.Modal(modalEl);
  var okBtn = document.getElementById('confirmModalOk');
  var defaultMessage = modalEl.querySelector('.modal-body').textContent;
  var defaultOk = okBtn.textContent;
  var targetFormId = null;
  document.querySelectorAll('[data-confirm-form]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      targetFormId = btn.getAttribute('data-confirm-form');
      modalEl.querySelector('.modal-body').textContent = btn.getAttribute('data-confirm-message') || defaultMessage;
      okBtn.textContent = btn.getAttribute('data-confirm-ok') || defaultOk;
      okBtn.classList.toggle('btn-danger', !btn.hasAttribute('data-confirm-ok'));
      okBtn.classList.toggle('btn-primary', btn.hasAttribute('data-confirm-ok'));
      modal.show();
    });
  });
  okBtn.addEventListener('click', function () {
    var form = targetFormId && document.getElementById(targetFormId);
    if (form) form.submit();
    modal.hide();
  });
});
```

브라우저 확인(Docker Oracle 기동 시): `/companies/1` 의 삭제 버튼 → 모달 문구 "정말 삭제하시겠습니까?", 확인 버튼 "삭제"(빨강) 그대로.

- [ ] **Step 15: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: 1부 94개 + 신규 테스트 전부 PASS (Docker 없으면 통합 테스트는 스킵).

```bash
git add src/main/java/com/crosscert/fidoadmin/common src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java src/main/resources/static/js/admin.js src/test/java/com/crosscert/fidoadmin/common
git commit -m "feat: 할당형 PK 삽입 전용 경로, 폼 검증 훅, JSON 정리, CRITERIA·FIDO2 SUPER 전용

- AssignedIdCrudService: 존재 검사 + persist 로 등록해 save() MERGE 덮어쓰기 차단
- CrudController.validate(): 등록/수정 구분 검증 훅
- JsonPretty: CLOB JSON 상세 출력 정리
- COMPANY_IDX 없는 CRITERIA·FIDO2 화면은 설계 3.3 에 따라 SUPER 전용
- admin.js: 확인 모달 버튼 문구 data-confirm-ok

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 2: FDS 정책 (CCFA_FDS_POLICY) — 할당형 PK CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/repository/CcfaFdsPolicyRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/service/FdsPolicyService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicySearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyController.java`
- Create: `src/main/resources/templates/company/fds-policy/list.html`
- Create: `src/main/resources/templates/company/fds-policy/detail.html`
- Create: `src/main/resources/templates/company/fds-policy/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/company/service/FdsPolicyServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyControllerWebTest.java`

**Interfaces:**
- Consumes: Task 1 `AssignedIdCrudService<E, ID, S>`(생성자 `(AdminRepository<E, ID>, AuditLogger, EntityManager)`, 추상 `ID assignedId(E)`), `CrudController.validate(F, boolean, BindingResult)`, 1부 `CompanyLookup.all()/names()/name(Long)`, `TenantContext.isSuper()/companyIdx()`, 엔티티 `CcfaFdsPolicy`(PK `Long companyIdx`, `idx` 없음).
- Produces:
  - `CcfaFdsPolicyRepository extends AdminRepository<CcfaFdsPolicy, Long>`
  - `FdsPolicyService extends AssignedIdCrudService<CcfaFdsPolicy, Long, FdsPolicySearchForm>` — `companyIdxAttribute()="companyIdx"`, `assignedId(e)=e.getCompanyIdx()`, `idOf(e)=String.valueOf(companyIdx)`, `defaultSort()=companyIdx ASC`, `sortableProperties()={"companyIdx","updatedtime"}`.
  - `FdsPolicyController` — `GET/POST /fds-policies`, 뷰 `company/fds-policy/*`. COMPANY 역할도 접근(자기 고객사 1건).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/service/FdsPolicyServiceTest.java`

```java
package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FdsPolicyServiceTest {

    CcfaFdsPolicyRepository repo = mock(CcfaFdsPolicyRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    FdsPolicyService service = new FdsPolicyService(repo, audit, em);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(7L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaFdsPolicy policy(Long companyIdx) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(companyIdx); p.setAndCountry("KR"); p.setOrCountry("KR");
        return p;
    }

    /** COMPANY 는 폼에 다른 고객사 IDX 를 넣어도 자기 고객사 행만 만들 수 있다(PK 가 COMPANY_IDX). */
    @Test void companyCreateForcesOwnCompanyAsKey() {
        login(1L);
        when(repo.existsById(1L)).thenReturn(false);

        CcfaFdsPolicy saved = service.create(policy(99L));

        assertThat(saved.getCompanyIdx()).isEqualTo(1L);
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(repo).existsById(1L);
        verify(em).persist(saved);
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "CCFA_FDS_POLICY CREATE 1");
    }

    @Test void duplicateCompanyIsRejectedWithoutOverwrite() {
        login(1L);
        when(repo.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(policy(1L))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void superKeepsChosenCompany() {
        login(0L);
        when(repo.existsById(2L)).thenReturn(false);
        assertThat(service.create(policy(2L)).getCompanyIdx()).isEqualTo(2L);
        verify(em).persist(any());
    }

    @Test void updateTouchesUpdatedtimeOnly() {
        login(1L);
        CcfaFdsPolicy existing = policy(1L);
        existing.setCreatedtime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, p -> p.setAndTerm("60"));

        assertThat(existing.getAndTerm()).isEqualTo("60");
        assertThat(existing.getCreatedtime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(existing.getUpdatedtime()).isAfter(existing.getCreatedtime());
        verify(audit).log(AuditType.UPDATE, "CCFA_FDS_POLICY UPDATE 1");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.service.FdsPolicyServiceTest'`
Expected: FAIL — `CcfaFdsPolicyRepository`, `FdsPolicyService` 없음.

- [ ] **Step 3: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/company/repository/CcfaFdsPolicyRepository.java`

```java
package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;

public interface CcfaFdsPolicyRepository extends AdminRepository<CcfaFdsPolicy, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicySearchForm.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;

/** 검색 조건은 고객사(기반 companyIdx)뿐이다. */
public class FdsPolicySearchForm extends SearchForm {
}
```

`src/main/java/com/crosscert/fidoadmin/company/service/FdsPolicyService.java`

```java
package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.company.web.FdsPolicySearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CCFA_FDS_POLICY. PK 가 COMPANY_IDX 라 고객사당 1건이며 채번이 없다(할당형 PK).
 * 등록은 AssignedIdCrudService 의 존재 검사 + persist 경로를 탄다.
 * COMPANY 역할은 기반 create() 가 COMPANY_IDX 를 자기 값으로 강제하므로 자기 행만 만들 수 있다.
 */
@Service
public class FdsPolicyService extends AssignedIdCrudService<CcfaFdsPolicy, Long, FdsPolicySearchForm> {

    public FdsPolicyService(CcfaFdsPolicyRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaFdsPolicy> toSpecification(FdsPolicySearchForm f) { return null; }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaFdsPolicy e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaFdsPolicy e, Long c) { e.setCompanyIdx(c); }
    @Override protected Long assignedId(CcfaFdsPolicy e) { return e.getCompanyIdx(); }
    @Override public String idOf(CcfaFdsPolicy e) { return String.valueOf(e.getCompanyIdx()); }
    @Override protected String tableName() { return "CCFA_FDS_POLICY"; }
    /** idx 가 없는 엔티티: 기본 정렬 "idx" 를 그대로 두면 조회 시 500 이 난다. */
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "companyIdx"); }
    @Override public Set<String> sortableProperties() { return Set.of("companyIdx", "updatedtime"); }

    @Override protected void touchCreated(CcfaFdsPolicy e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaFdsPolicy e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.service.FdsPolicyServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyControllerWebTest.java`

```java
package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.FdsPolicyService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FdsPolicyController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class FdsPolicyControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FdsPolicyService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFdsPolicy policy(long companyIdx) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(companyIdx); p.setAndCountry("KR"); p.setOrCountry("US"); p.setAndTerm("30");
        return p;
    }

    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(policy(1L))));
        mvc.perform(get("/fds-policies").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/list"))
            .andExpect(content().string(containsString("KR")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("companyIdx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/fds-policies").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void blankAndCountryShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "").param("orCountry", "KR"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("AND 국가는 필수입니다.")));
    }

    /** SUPER 는 고객사를 골라야 한다. COMPANY 는 기반이 강제하므로 검사하지 않는다. */
    @Test void superMustChooseCompanyOnCreate() throws Exception {
        mvc.perform(post("/fds-policies").with(user(superUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("고객사를 선택하세요.")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(policy(1L));
        when(service.idOf(any())).thenReturn("1");
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fds-policies/1"));
    }

    @Test void detailRendersAllColumns() throws Exception {
        when(service.get(1L)).thenReturn(policy(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/fds-policies/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/detail"))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(containsString("US")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.FdsPolicyControllerWebTest'`
Expected: FAIL — `FdsPolicyController`, `FdsPolicyForm` 없음.

- [ ] **Step 7: 폼·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_FDS_POLICY 입력 폼. 길이 제한은 ERD 값(바이트). */
@Getter @Setter
public class FdsPolicyForm {
    /** 할당형 PK. SUPER 만 고른다. COMPANY 는 기반 create() 가 자기 값으로 덮어쓴다. */
    private Long companyIdx;
    @ByteSize(max = 4000) private String andIp;
    @ByteSize(max = 32) private String andTerm;
    @ByteSize(max = 16) private String andDevice;
    @NotBlank(message = "AND 국가는 필수입니다.") @ByteSize(max = 16) private String andCountry;
    @ByteSize(max = 4000) private String orIp;
    @ByteSize(max = 32) private String orTerm;
    @NotBlank(message = "OR 국가는 필수입니다.") @ByteSize(max = 16) private String orCountry;

    public static FdsPolicyForm from(CcfaFdsPolicy p) {
        FdsPolicyForm f = new FdsPolicyForm();
        f.companyIdx = p.getCompanyIdx();
        f.andIp = p.getAndIp(); f.andTerm = p.getAndTerm(); f.andDevice = p.getAndDevice(); f.andCountry = p.getAndCountry();
        f.orIp = p.getOrIp(); f.orTerm = p.getOrTerm(); f.orCountry = p.getOrCountry();
        return f;
    }

    /** 식별자(companyIdx)는 여기서 건드리지 않는다. 등록 시에만 컨트롤러 toEntity 가 채운다. */
    public void applyTo(CcfaFdsPolicy p) {
        p.setAndIp(andIp); p.setAndTerm(andTerm); p.setAndDevice(andDevice); p.setAndCountry(andCountry);
        p.setOrIp(orIp); p.setOrTerm(orTerm); p.setOrCountry(orCountry);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyController.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.FdsPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fds-policies")
@RequiredArgsConstructor
public class FdsPolicyController extends CrudController<CcfaFdsPolicy, Long, FdsPolicyForm, FdsPolicySearchForm> {

    private final FdsPolicyService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaFdsPolicy, Long, FdsPolicySearchForm> service() { return service; }
    @Override protected String basePath() { return "/fds-policies"; }
    @Override protected String viewDir() { return "company/fds-policy"; }
    @Override protected FdsPolicySearchForm newSearchForm() { return new FdsPolicySearchForm(); }
    @Override protected FdsPolicyForm newForm() { return new FdsPolicyForm(); }
    @Override protected FdsPolicyForm toForm(CcfaFdsPolicy e) { return FdsPolicyForm.from(e); }

    /**
     * 할당형 PK 이므로 예외적으로 폼의 companyIdx 를 식별자로 채운다.
     * COMPANY 역할은 기반 create() 가 insert() 전에 TenantContext.companyIdx() 로 덮어쓰고,
     * AssignedIdCrudService.insert() 가 존재 검사 후 persist 하므로 기존 행이 덮어써지지 않는다.
     */
    @Override protected CcfaFdsPolicy toEntity(FdsPolicyForm f) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(f.getCompanyIdx());
        f.applyTo(p);
        return p;
    }
    /** 수정에서는 식별자를 바꾸지 않는다. */
    @Override protected void applyForm(FdsPolicyForm f, CcfaFdsPolicy e) { f.applyTo(e); }

    @Override protected void validate(FdsPolicyForm f, boolean isNew, BindingResult binding) {
        if (isNew && TenantContext.isSuper() && f.getCompanyIdx() == null) {
            binding.rejectValue("companyIdx", "required", "고객사를 선택하세요.");
        }
    }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateFormModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(CcfaFdsPolicy e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/company/fds-policy/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FDS 정책</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">FDS 정책</h1>
    <a th:href="@{/fds-policies/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/fds-policies}" class="row g-2 align-items-end mb-3" sec:authorize="hasRole('SUPER')">
    <div class="col-auto">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/fds-policies}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>고객사</th><th>AND 국가</th><th>AND 기간</th><th>AND 기기</th><th>OR 국가</th><th>OR 기간</th><th>수정일시</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/fds-policies/{id}(id=${r.companyIdx})}" th:text="|${companyNames.get(r.companyIdx)} (${r.companyIdx})|">고객사</a></td>
          <td th:text="${r.andCountry}"></td>
          <td th:text="${r.andTerm}"></td>
          <td th:text="${r.andDevice}"></td>
          <td th:text="${r.orCountry}"></td>
          <td th:text="${r.orTerm}"></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="7" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/company/fds-policy/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FDS 정책 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|FDS 정책 — ${companyName}|">FDS 정책</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/fds-policies/{id}/edit(id=${item.companyIdx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/fds-policies/{id}/delete(id=${item.companyIdx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/fds-policies}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>AND IP</th><td class="fa-pre" th:text="${item.andIp}"></td></tr>
        <tr><th>AND 기간</th><td th:text="${item.andTerm}"></td></tr>
        <tr><th>AND 기기</th><td th:text="${item.andDevice}"></td></tr>
        <tr><th>AND 국가</th><td th:text="${item.andCountry}"></td></tr>
        <tr><th>OR IP</th><td class="fa-pre" th:text="${item.orIp}"></td></tr>
        <tr><th>OR 기간</th><td th:text="${item.orTerm}"></td></tr>
        <tr><th>OR 국가</th><td th:text="${item.orCountry}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/company/fds-policy/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? 'FDS 정책 등록' : 'FDS 정책 수정'">FDS 정책</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? 'FDS 정책 등록' : 'FDS 정책 수정'">FDS 정책</h1>
  <form th:action="${isNew} ? @{/fds-policies} : @{/fds-policies/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <!-- 등록: SUPER 만 고객사를 고른다. COMPANY 는 서비스가 자기 고객사로 강제한다. -->
      <div class="col-md-6" th:if="${isNew}" sec:authorize="hasRole('SUPER')">
        <label class="form-label">고객사 <span class="text-danger">*</span></label>
        <select th:field="*{companyIdx}" class="form-select" th:classappend="${#fields.hasErrors('companyIdx')} ? 'is-invalid'">
          <option value="">선택</option>
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
        </select>
        <div class="invalid-feedback" th:errors="*{companyIdx}"></div>
      </div>
      <!-- 수정: 식별자는 바꿀 수 없다. -->
      <div class="col-md-6" th:unless="${isNew}">
        <label class="form-label">고객사</label>
        <input type="hidden" th:field="*{companyIdx}">
        <input class="form-control" readonly th:value="|${companyNames.get(form.companyIdx)} (${form.companyIdx})|">
      </div>
      <div class="col-12"><label class="form-label">AND IP</label><textarea th:field="*{andIp}" rows="2" class="form-control"></textarea></div>
      <div class="col-md-4"><label class="form-label">AND 기간</label><input th:field="*{andTerm}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">AND 기기</label><input th:field="*{andDevice}" class="form-control"></div>
      <div class="col-md-4">
        <label class="form-label">AND 국가 <span class="text-danger">*</span></label>
        <input th:field="*{andCountry}" class="form-control" th:classappend="${#fields.hasErrors('andCountry')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{andCountry}"></div>
      </div>
      <div class="col-12"><label class="form-label">OR IP</label><textarea th:field="*{orIp}" rows="2" class="form-control"></textarea></div>
      <div class="col-md-4"><label class="form-label">OR 기간</label><input th:field="*{orTerm}" class="form-control"></div>
      <div class="col-md-4">
        <label class="form-label">OR 국가 <span class="text-danger">*</span></label>
        <input th:field="*{orCountry}" class="form-control" th:classappend="${#fields.hasErrors('orCountry')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{orCountry}"></div>
      </div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/fds-policies} : @{/fds-policies/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.FdsPolicyControllerWebTest'`
Expected: PASS (6개).

- [ ] **Step 10: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: 전부 PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/company src/main/resources/templates/company/fds-policy src/test/java/com/crosscert/fidoadmin/company
git commit -m "feat: FDS 정책 화면 (CCFA_FDS_POLICY, 고객사당 1건 할당형 PK)

- AssignedIdCrudService 기반 등록: 존재 검사 후 persist
- COMPANY 는 자기 고객사 행만, SUPER 는 고객사 선택
- idx 없는 엔티티라 기본 정렬 companyIdx 로 재정의

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 3: 라이선스 (CCFA_LICENSE) — SUPER 전용 CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/repository/CcfaLicenseRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/service/LicenseService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/LicenseSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/LicenseForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/LicenseController.java`
- Create: `src/main/resources/templates/company/license/list.html`
- Create: `src/main/resources/templates/company/license/detail.html`
- Create: `src/main/resources/templates/company/license/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/company/service/LicenseServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/company/web/LicenseControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `CrudService`, `CrudController`, `Specs.like/eq/all`, `CcfaCompanyRepository.findById(Long)`, `CompanyLookup`, 엔티티 `CcfaLicense`(시퀀스 PK `idx`, `companyIdx`, 비정규화 `companyName`). `SecurityConfig` 가 이미 `/licenses/**` 를 `hasRole("SUPER")` 로 막는다.
- Produces:
  - `CcfaLicenseRepository extends AdminRepository<CcfaLicense, Long>`
  - `LicenseService extends CrudService<CcfaLicense, Long, LicenseSearchForm>` — 생성자 `(CcfaLicenseRepository, AuditLogger, CcfaCompanyRepository)`, `companyIdxAttribute()="companyIdx"`, `sortableProperties()={"idx","serviceName","createdtime"}`, `syncCompanyName(CcfaLicense)`(`applyDefaults`·`touchUpdated` 에서 호출).
  - `LicenseController` — `/licenses`, 뷰 `company/license/*`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/service/LicenseServiceTest.java`

```java
package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LicenseServiceTest {

    CcfaLicenseRepository repo = mock(CcfaLicenseRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    LicenseService service = new LicenseService(repo, mock(AuditLogger.class), companies);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaCompany company(long idx, String name) { CcfaCompany c = new CcfaCompany(); c.setIdx(idx); c.setCompanyName(name); return c; }

    /** COMPANY_NAME 은 비정규화 컬럼이다. 폼이 아니라 CCFA_COMPANY 에서 채운다. */
    @Test void companyNameSyncedOnCreate() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L, "KB국민은행")));
        CcfaLicense l = new CcfaLicense(); l.setCompanyIdx(1L); l.setServiceName("kbstar");

        CcfaLicense saved = service.create(l);

        assertThat(saved.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
    }

    @Test void companyNameSyncedWhenCompanyChangesOnUpdate() {
        CcfaLicense existing = new CcfaLicense(); existing.setIdx(9L); existing.setCompanyIdx(1L); existing.setCompanyName("KB국민은행");
        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(companies.findById(2L)).thenReturn(Optional.of(company(2L, "테스트고객사")));

        service.update(9L, l -> l.setCompanyIdx(2L));

        assertThat(existing.getCompanyName()).isEqualTo("테스트고객사");
        assertThat(existing.getUpdatedtime()).isNotNull();
    }

    @Test void unknownCompanyLeavesNameNull() {
        when(companies.findById(77L)).thenReturn(Optional.empty());
        CcfaLicense l = new CcfaLicense(); l.setCompanyIdx(77L);
        assertThat(service.create(l).getCompanyName()).isNull();
    }

    @Test void sortableIncludesServiceName() {
        assertThat(service.sortableProperties()).contains("idx", "serviceName", "createdtime");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.service.LicenseServiceTest'`
Expected: FAIL — `CcfaLicenseRepository`, `LicenseService` 없음.

- [ ] **Step 3: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/company/repository/CcfaLicenseRepository.java`

```java
package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;

public interface CcfaLicenseRepository extends AdminRepository<CcfaLicense, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/company/web/LicenseSearchForm.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LicenseSearchForm extends SearchForm {
    private String serviceName;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("serviceName", serviceName);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/company/service/LicenseService.java`

```java
package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import com.crosscert.fidoadmin.company.web.LicenseSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_LICENSE. SUPER 전용 URL. COMPANY_NAME 은 비정규화 컬럼이라 CCFA_COMPANY 에서 동기화한다. */
@Service
public class LicenseService extends CrudService<CcfaLicense, Long, LicenseSearchForm> {

    private final CcfaCompanyRepository companies;

    public LicenseService(CcfaLicenseRepository repository, AuditLogger audit, CcfaCompanyRepository companies) {
        super(repository, audit);
        this.companies = companies;
    }

    @Override protected Specification<CcfaLicense> toSpecification(LicenseSearchForm f) {
        return Specs.all(Specs.like("serviceName", f.getServiceName()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaLicense e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaLicense e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaLicense e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_LICENSE"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "serviceName", "createdtime"); }

    @Override protected void applyDefaults(CcfaLicense e) { syncCompanyName(e); }
    @Override protected void touchCreated(CcfaLicense e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaLicense e, LocalDateTime now) { e.setUpdatedtime(now); syncCompanyName(e); }

    /** COMPANY_IDX 로 CCFA_COMPANY 이름을 찾아 COMPANY_NAME 에 복사한다. 없으면 null 로 둔다. */
    void syncCompanyName(CcfaLicense e) {
        if (e.getCompanyIdx() == null) { e.setCompanyName(null); return; }
        e.setCompanyName(companies.findById(e.getCompanyIdx()).map(CcfaCompany::getCompanyName).orElse(null));
    }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.service.LicenseServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/company/web/LicenseControllerWebTest.java`

```java
package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.LicenseService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LicenseController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class LicenseControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean LicenseService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/licenses").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superListRendersRowsWithCompanyName() throws Exception {
        CcfaLicense l = new CcfaLicense(); l.setIdx(1L); l.setCompanyIdx(1L); l.setServiceName("kbstar"); l.setContactName("홍길동");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(l)));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/licenses").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/license/list"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void createWithoutCompanyShowsFormAgain() throws Exception {
        mvc.perform(post("/licenses").with(user(superUser)).with(csrf()).param("serviceName", "kbstar"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/license/form"))
            .andExpect(content().string(containsString("고객사를 선택하세요.")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        CcfaLicense saved = new CcfaLicense(); saved.setIdx(55L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("55");
        mvc.perform(post("/licenses").with(user(superUser)).with(csrf()).param("companyIdx", "1").param("serviceName", "kbstar"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/licenses/55"));
    }

    @Test void detailShowsLicenseText() throws Exception {
        CcfaLicense l = new CcfaLicense(); l.setIdx(1L); l.setCompanyIdx(1L); l.setLicense("LICENSE-SAMPLE-KEY");
        when(service.get(1L)).thenReturn(l);
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/licenses/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("LICENSE-SAMPLE-KEY")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.LicenseControllerWebTest'`
Expected: FAIL — `LicenseController`, `LicenseForm` 없음.

- [ ] **Step 7: 폼·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/company/web/LicenseForm.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_LICENSE 입력 폼. COMPANY_NAME 은 폼에 없다(서비스가 동기화). */
@Getter @Setter
public class LicenseForm {
    @NotNull(message = "고객사를 선택하세요.") private Long companyIdx;
    @ByteSize(max = 128) private String contactName;
    @ByteSize(max = 128) private String contactPhone;
    @ByteSize(max = 256) private String contactEmail;
    @ByteSize(max = 256) private String serviceName;
    @ByteSize(max = 1024) private String etc;
    @ByteSize(max = 2048) private String license;
    @ByteSize(max = 256) private String filePath;
    @ByteSize(max = 256) private String hashvalue;

    public static LicenseForm from(CcfaLicense l) {
        LicenseForm f = new LicenseForm();
        f.companyIdx = l.getCompanyIdx(); f.contactName = l.getContactName(); f.contactPhone = l.getContactPhone();
        f.contactEmail = l.getContactEmail(); f.serviceName = l.getServiceName(); f.etc = l.getEtc();
        f.license = l.getLicense(); f.filePath = l.getFilePath(); f.hashvalue = l.getHashvalue();
        return f;
    }

    public void applyTo(CcfaLicense l) {
        l.setCompanyIdx(companyIdx); l.setContactName(contactName); l.setContactPhone(contactPhone);
        l.setContactEmail(contactEmail); l.setServiceName(serviceName); l.setEtc(etc);
        l.setLicense(license); l.setFilePath(filePath); l.setHashvalue(hashvalue);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/company/web/LicenseController.java`

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/licenses")
@RequiredArgsConstructor
public class LicenseController extends CrudController<CcfaLicense, Long, LicenseForm, LicenseSearchForm> {

    private final LicenseService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaLicense, Long, LicenseSearchForm> service() { return service; }
    @Override protected String basePath() { return "/licenses"; }
    @Override protected String viewDir() { return "company/license"; }
    @Override protected LicenseSearchForm newSearchForm() { return new LicenseSearchForm(); }
    @Override protected LicenseForm newForm() { return new LicenseForm(); }
    @Override protected LicenseForm toForm(CcfaLicense e) { return LicenseForm.from(e); }
    @Override protected CcfaLicense toEntity(LicenseForm f) { CcfaLicense l = new CcfaLicense(); f.applyTo(l); return l; }
    @Override protected void applyForm(LicenseForm f, CcfaLicense e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateFormModel(Model model) { model.addAttribute("companies", companies.all()); }
    @Override protected void populateDetailModel(CcfaLicense e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/company/license/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>라이선스</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">라이선스</h1>
    <a th:href="@{/licenses/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/licenses}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">서비스명</label>
      <input name="serviceName" th:value="${search.serviceName}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/licenses}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>고객사</th><th>서비스명</th><th>담당자</th><th>전화</th><th>이메일</th><th>등록일</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/licenses/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyName}"></td>
          <td th:text="${r.serviceName}"></td>
          <td th:text="${r.contactName}"></td>
          <td th:text="${r.contactPhone}"></td>
          <td th:text="${r.contactEmail}"></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="7" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/company/license/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>라이선스 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|라이선스 #${item.idx}|">라이선스</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/licenses/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/licenses/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/licenses}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>고객사명(저장값)</th><td th:text="${item.companyName}"></td></tr>
        <tr><th>담당자</th><td th:text="${item.contactName}"></td></tr>
        <tr><th>전화</th><td th:text="${item.contactPhone}"></td></tr>
        <tr><th>이메일</th><td th:text="${item.contactEmail}"></td></tr>
        <tr><th>서비스명</th><td th:text="${item.serviceName}"></td></tr>
        <tr><th>비고</th><td class="fa-pre" th:text="${item.etc}"></td></tr>
        <tr><th>라이선스</th><td class="fa-pre fa-mono" th:text="${item.license}"></td></tr>
        <tr><th>파일 경로</th><td th:text="${item.filePath}"></td></tr>
        <tr><th>해시</th><td class="fa-mono" th:text="${item.hashvalue}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/company/license/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '라이선스 등록' : '라이선스 수정'">라이선스</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '라이선스 등록' : '라이선스 수정'">라이선스</h1>
  <form th:action="${isNew} ? @{/licenses} : @{/licenses/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">고객사 <span class="text-danger">*</span></label>
        <select th:field="*{companyIdx}" class="form-select" th:classappend="${#fields.hasErrors('companyIdx')} ? 'is-invalid'">
          <option value="">선택</option>
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
        </select>
        <div class="invalid-feedback" th:errors="*{companyIdx}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">서비스명</label><input th:field="*{serviceName}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">담당자</label><input th:field="*{contactName}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">전화</label><input th:field="*{contactPhone}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">이메일</label><input th:field="*{contactEmail}" class="form-control"></div>
      <div class="col-12"><label class="form-label">라이선스</label><textarea th:field="*{license}" rows="4" class="form-control fa-mono"></textarea></div>
      <div class="col-md-6"><label class="form-label">파일 경로</label><input th:field="*{filePath}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">해시</label><input th:field="*{hashvalue}" class="form-control"></div>
      <div class="col-12"><label class="form-label">비고</label><textarea th:field="*{etc}" rows="2" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/licenses} : @{/licenses/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.LicenseControllerWebTest'`
Expected: PASS (5개).

- [ ] **Step 10: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: 전부 PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/company src/main/resources/templates/company/license src/test/java/com/crosscert/fidoadmin/company
git commit -m "feat: 라이선스 화면 (CCFA_LICENSE, SUPER 전용)

- COMPANY_NAME 비정규화 컬럼은 CCFA_COMPANY 에서 동기화
- 고객사·서비스명 검색

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 4: 운영자 (CCFA_MANAGER + CCFA_MANAGER_PW_POLICY) — SUPER 전용 CRUD, 비밀번호, 잠금 해제

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/manager/service/ManagerService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerView.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerController.java`
- Create: `src/main/resources/templates/manager/manager/list.html`
- Create: `src/main/resources/templates/manager/manager/detail.html`
- Create: `src/main/resources/templates/manager/manager/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/manager/service/ManagerServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/manager/web/ManagerControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `CcfaManagerRepository.findByUserId(String)`, `CcfaManagerPwPolicyRepository.findFirstByUserIdOrderByIdxDesc(String)`, `LoginAttemptService.unlock(String)`, `PasswordEncoder`(SecurityConfig 빈, SHA-256), `AuditType.STATUS`, Task 1 `CrudService.insert(E)` 훅과 `CrudController.validate(F, boolean, BindingResult)`. `SecurityConfig` 가 이미 `/managers/**` 를 SUPER 로 막는다.
- Produces:
  - `ManagerService extends CrudService<CcfaManager, Long, ManagerSearchForm>` — 생성자 `(CcfaManagerRepository, AuditLogger, CcfaManagerPwPolicyRepository, LoginAttemptService)`, `companyIdxAttribute()="companyIdx"`, `sortableProperties()={"idx","userId","userNm","lastAccess"}`, `Optional<CcfaManagerPwPolicy> lockState(String userId)`, `void unlock(Long id)`.
  - `record ManagerRow(Long idx, String userId, String userNm, String userEmail, Long companyIdx, String status, String login, LocalDateTime lastAccess)`, `record ManagerView(...)`(USER_PW 제외 전체 컬럼).
  - `ManagerController` — `/managers`, 추가 엔드포인트 `POST /managers/{id}/unlock`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/manager/service/ManagerServiceTest.java`

```java
package com.crosscert.fidoadmin.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ManagerServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    AuditLogger audit = mock(AuditLogger.class);
    ManagerService service = new ManagerService(managers, audit, policies, loginAttempts);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaManager manager(Long idx, String userId) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId(userId); m.setUserPw("hash"); m.setCompanyIdx(1L);
        return m;
    }

    /** USER_ID 에 유니크 제약이 없어(ERD) 코드에서 중복을 막는다. 로그인이 USER_ID 로 조회하기 때문. */
    @Test void duplicateUserIdIsRejectedBeforeSave() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(2L, "kbadmin")));
        assertThatThrownBy(() -> service.create(manager(null, "kbadmin")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("kbadmin");
        verify(managers, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void defaultsFilledOnCreate() {
        when(managers.findByUserId("newop")).thenReturn(Optional.empty());
        when(managers.save(any())).thenAnswer(inv -> { CcfaManager m = inv.getArgument(0); m.setIdx(10L); return m; });

        CcfaManager saved = service.create(manager(null, "newop"));

        assertThat(saved.getStatus()).isEqualTo("활성");
        assertThat(saved.getLogin()).isEqualTo("OFF-LINE");
        assertThat(saved.getAlramType()).isEqualTo("none");
        assertThat(saved.getAlramLevel()).isEqualTo("0");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_MANAGER CREATE 10");
    }

    @Test void unlockDelegatesAndAuditsStatus() {
        when(managers.findById(2L)).thenReturn(Optional.of(manager(2L, "kbadmin")));

        service.unlock(2L);

        verify(loginAttempts).unlock("kbadmin");
        verify(audit).log(AuditType.STATUS, "CCFA_MANAGER UNLOCK kbadmin");
    }

    @Test void selfDeleteIsBlocked() {
        when(managers.findById(1L)).thenReturn(Optional.of(manager(1L, "superuser")));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("자기 자신");
        verify(managers, never()).delete(any(CcfaManager.class));
    }

    @Test void lockStateReadsLatestPolicyRow() {
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setUserId("kbadmin"); p.setAccountLock("Y"); p.setPwFailCnt(5L);
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        assertThat(service.lockState("kbadmin")).get().extracting(CcfaManagerPwPolicy::getAccountLock).isEqualTo("Y");
        assertThat(service.lockState("nobody")).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.manager.service.ManagerServiceTest'`
Expected: FAIL — `ManagerService` 없음.

- [ ] **Step 3: 검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/manager/web/ManagerSearchForm.java`

```java
package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ManagerSearchForm extends SearchForm {
    private String userId;
    private String userNm;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId); m.put("userNm", userNm); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/manager/service/ManagerService.java`

```java
package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CCFA_MANAGER. SUPER 전용 URL. USER_PW 는 컨트롤러가 SHA-256 으로 인코딩해 넘긴다.
 * 잠금 상태는 CCFA_MANAGER_PW_POLICY 에 있고, 해제는 1부 LoginAttemptService.unlock 이 처리한다.
 */
@Service
public class ManagerService extends CrudService<CcfaManager, Long, ManagerSearchForm> {

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final LoginAttemptService loginAttempts;

    public ManagerService(CcfaManagerRepository managers, AuditLogger audit,
                          CcfaManagerPwPolicyRepository policies, LoginAttemptService loginAttempts) {
        super(managers, audit);
        this.managers = managers;
        this.policies = policies;
        this.loginAttempts = loginAttempts;
    }

    @Override protected Specification<CcfaManager> toSpecification(ManagerSearchForm f) {
        return Specs.all(
            Specs.like("userId", f.getUserId()),
            Specs.like("userNm", f.getUserNm()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaManager e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaManager e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaManager e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MANAGER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userId", "userNm", "lastAccess"); }

    @Override protected void applyDefaults(CcfaManager e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("활성");
        if (e.getLogin() == null) e.setLogin("OFF-LINE");
        if (e.getAlramType() == null || e.getAlramType().isBlank()) e.setAlramType("none");
        if (e.getAlramLevel() == null || e.getAlramLevel().isBlank()) e.setAlramLevel("0");
    }
    @Override protected void touchCreated(CcfaManager e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaManager e, LocalDateTime now) { e.setUpdatedtime(now); }

    /** ERD 에 USER_ID 유니크 제약이 없다. 로그인이 USER_ID 로 조회하므로 중복을 코드에서 막는다. */
    @Override protected CcfaManager insert(CcfaManager e) {
        if (managers.findByUserId(e.getUserId()).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + e.getUserId() + " 은(는) 이미 존재합니다");
        }
        return super.insert(e);
    }

    @Override protected void beforeDelete(CcfaManager e) {
        if (e.getIdx() != null && e.getIdx().equals(TenantContext.require().getIdx())) {
            throw new IllegalStateException("자기 자신은 삭제할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<CcfaManagerPwPolicy> lockState(String userId) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId);
    }

    /** 잠금 해제: PW_POLICY 초기화 + BLOCK_TIME 제거. 감사 로그 STATUS. */
    @Transactional
    public void unlock(Long id) {
        CcfaManager m = get(id);
        loginAttempts.unlock(m.getUserId());
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.getUserId());
    }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.manager.service.ManagerServiceTest'`
Expected: PASS (5개).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/manager/web/ManagerControllerWebTest.java`

```java
package com.crosscert.fidoadmin.manager.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ManagerController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class ManagerControllerWebTest {

    static final String PW_HASH = "a".repeat(64);

    @Autowired MockMvc mvc;
    @MockitoBean ManagerService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaManager manager(long idx, String userId) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId(userId); m.setUserPw(PW_HASH);
        m.setUserNm("KB운영자"); m.setCompanyIdx(1L); m.setStatus("활성"); m.setLogin("OFF-LINE");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/managers").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 민감 컬럼 USER_PW 는 목록·상세 어디에도 실리지 않는다(설계 2.2). */
    @Test void listNeverExposesPasswordHash() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(manager(2L, "kbadmin"))));
        mvc.perform(get("/managers").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/list"))
            .andExpect(content().string(containsString("kbadmin")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void detailNeverExposesPasswordHashAndShowsLockState() throws Exception {
        when(service.get(2L)).thenReturn(manager(2L, "kbadmin"));
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setAccountLock("Y"); p.setPwFailCnt(5L);
        when(service.lockState("kbadmin")).thenReturn(Optional.of(p));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/managers/2").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/detail"))
            .andExpect(content().string(containsString("잠김")))
            .andExpect(content().string(containsString("잠금 해제")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void createWithoutPasswordShowsFormWithMessage() throws Exception {
        mvc.perform(post("/managers").with(user(superUser)).with(csrf())
                .param("userId", "newop").param("companyIdx", "1").param("status", "활성"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("비밀번호는 필수입니다.")));
    }

    @Test void passwordMismatchShowsMessage() throws Exception {
        mvc.perform(post("/managers").with(user(superUser)).with(csrf())
                .param("userId", "newop").param("companyIdx", "1").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Other1234!"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("비밀번호 확인이 일치하지 않습니다.")));
    }

    /** 등록 시 USER_PW 는 SHA-256 hex 로 인코딩되어 서비스로 간다(평문이 아니다). */
    @Test void createEncodesPasswordAndRedirects() throws Exception {
        CcfaManager saved = manager(10L, "newop");
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("10");
        mvc.perform(post("/managers").with(user(superUser)).with(csrf())
                .param("userId", "newop").param("companyIdx", "1").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Secret1234!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/10"));
        ArgumentCaptor<CcfaManager> captor = ArgumentCaptor.forClass(CcfaManager.class);
        verify(service).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserPw())
            .isEqualTo(com.crosscert.fidoadmin.auth.Sha256PasswordEncoder.sha256Hex("Secret1234!"));
    }

    @Test void unlockRedirectsToDetailWithFlash() throws Exception {
        mvc.perform(post("/managers/2/unlock").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/2"))
            .andExpect(flash().attribute("flashSuccess", "잠금이 해제되었습니다."));
        verify(service).unlock(2L);
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/managers/2/unlock").with(user(superUser))).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.manager.web.ManagerControllerWebTest'`
Expected: FAIL — `ManagerController`, `ManagerForm` 없음.

- [ ] **Step 7: 폼·DTO·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/manager/web/ManagerForm.java`

```java
package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_MANAGER 입력 폼. 비밀번호는 평문으로 받아 컨트롤러가 인코딩한다.
 * 등록 시 필수·수정 시 선택은 컨트롤러 validate() 가 본다(Bean Validation 으로는 구분할 수 없다).
 */
@Getter @Setter
public class ManagerForm {
    @NotBlank(message = "ID 는 필수입니다.") @ByteSize(max = 64) private String userId;
    @ByteSize(max = 128) private String password;
    private String passwordConfirm;
    @ByteSize(max = 50) private String userNm;
    @ByteSize(max = 256) private String userEmail;
    @ByteSize(max = 20) private String userPhone;
    @NotNull(message = "고객사를 선택하세요.") private Long companyIdx;
    @NotBlank(message = "상태는 필수입니다.") @ByteSize(max = 20) private String status = "활성";
    @ByteSize(max = 2048) private String etc;
    @ByteSize(max = 32) private String alramType = "none";
    @ByteSize(max = 20) private String alramLevel = "0";

    /** USER_PW 는 절대 폼으로 옮기지 않는다. */
    public static ManagerForm from(CcfaManager m) {
        ManagerForm f = new ManagerForm();
        f.userId = m.getUserId(); f.userNm = m.getUserNm(); f.userEmail = m.getUserEmail(); f.userPhone = m.getUserPhone();
        f.companyIdx = m.getCompanyIdx(); f.status = m.getStatus(); f.etc = m.getEtc();
        f.alramType = m.getAlramType(); f.alramLevel = m.getAlramLevel();
        return f;
    }

    /** USER_ID 는 등록 시에만 채운다(수정 화면에서는 readonly, 로그인 키라 바꾸지 않는다). USER_PW 는 컨트롤러가 채운다. */
    public void applyTo(CcfaManager m) {
        if (m.getUserId() == null) m.setUserId(userId == null ? null : userId.trim());
        m.setUserNm(userNm); m.setUserEmail(userEmail); m.setUserPhone(userPhone);
        m.setCompanyIdx(companyIdx); m.setStatus(status); m.setEtc(etc);
        m.setAlramType(alramType); m.setAlramLevel(alramLevel);
    }

    public boolean hasPassword() { return password != null && !password.isBlank(); }
}
```

`src/main/java/com/crosscert/fidoadmin/manager/web/ManagerRow.java`

```java
package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;

/** 목록 행. USER_PW 제외. */
public record ManagerRow(Long idx, String userId, String userNm, String userEmail, Long companyIdx,
                         String status, String login, LocalDateTime lastAccess) {
    public static ManagerRow of(CcfaManager m) {
        return new ManagerRow(m.getIdx(), m.getUserId(), m.getUserNm(), m.getUserEmail(), m.getCompanyIdx(),
            m.getStatus(), m.getLogin(), m.getLastAccess());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/manager/web/ManagerView.java`

```java
package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;

/** 상세. ERD 16개 컬럼 중 USER_PW 만 제외한다. */
public record ManagerView(Long idx, String userId, String userNm, String userEmail, String userPhone, Long companyIdx,
                          String status, String login, LocalDateTime blockTime, LocalDateTime lastAccess, String etc,
                          String alramType, String alramLevel, LocalDateTime createdtime, LocalDateTime updatedtime) {
    public static ManagerView of(CcfaManager m) {
        return new ManagerView(m.getIdx(), m.getUserId(), m.getUserNm(), m.getUserEmail(), m.getUserPhone(), m.getCompanyIdx(),
            m.getStatus(), m.getLogin(), m.getBlockTime(), m.getLastAccess(), m.getEtc(),
            m.getAlramType(), m.getAlramLevel(), m.getCreatedtime(), m.getUpdatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/manager/web/ManagerController.java`

```java
package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/managers")
@RequiredArgsConstructor
public class ManagerController extends CrudController<CcfaManager, Long, ManagerForm, ManagerSearchForm> {

    private final ManagerService service;
    private final CompanyLookup companies;
    private final PasswordEncoder encoder;

    @Override protected CrudService<CcfaManager, Long, ManagerSearchForm> service() { return service; }
    @Override protected String basePath() { return "/managers"; }
    @Override protected String viewDir() { return "manager/manager"; }
    @Override protected ManagerSearchForm newSearchForm() { return new ManagerSearchForm(); }
    @Override protected ManagerForm newForm() { return new ManagerForm(); }
    @Override protected ManagerForm toForm(CcfaManager e) { return ManagerForm.from(e); }

    @Override protected CcfaManager toEntity(ManagerForm f) {
        CcfaManager m = new CcfaManager();
        f.applyTo(m);
        m.setUserPw(encoder.encode(f.getPassword()));
        return m;
    }
    /** 수정: 비밀번호를 입력했을 때만 바꾼다. */
    @Override protected void applyForm(ManagerForm f, CcfaManager e) {
        f.applyTo(e);
        if (f.hasPassword()) e.setUserPw(encoder.encode(f.getPassword()));
    }

    @Override protected void validate(ManagerForm f, boolean isNew, BindingResult binding) {
        if (isNew && !f.hasPassword()) {
            binding.rejectValue("password", "required", "비밀번호는 필수입니다.");
        }
        if (f.hasPassword() && !f.getPassword().equals(f.getPasswordConfirm())) {
            binding.rejectValue("passwordConfirm", "mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }
    }

    @Override protected Object toListView(CcfaManager e) { return ManagerRow.of(e); }
    @Override protected Object toDetailView(CcfaManager e) { return ManagerView.of(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        model.addAttribute("companies", companies.all());
    }
    @Override protected void populateFormModel(Model model) { model.addAttribute("companies", companies.all()); }
    @Override protected void populateDetailModel(CcfaManager e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
        model.addAttribute("lockState", service.lockState(e.getUserId()).orElse(null));
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        service.unlock(id);
        redirect.addFlashAttribute("flashSuccess", "잠금이 해제되었습니다.");
        return "redirect:/managers/" + id;
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/manager/manager/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>운영자</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">운영자</h1>
    <a th:href="@{/managers/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/managers}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">ID</label><input name="userId" th:value="${search.userId}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">이름</label><input name="userNm" th:value="${search.userNm}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="활성" th:selected="${search.status == '활성'}">활성</option>
        <option value="비활성" th:selected="${search.status == '비활성'}">비활성</option>
      </select>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/managers}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>ID</th><th>이름</th><th>이메일</th><th>고객사</th><th>상태</th><th>로그인</th><th>최근 접속</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/managers/{id}(id=${r.idx})}" th:text="${r.userId}">id</a></td>
          <td th:text="${r.userNm}"></td>
          <td th:text="${r.userEmail}"></td>
          <td th:text="${companyNames.get(r.companyIdx)}"></td>
          <td><span class="badge" th:classappend="${r.status == '활성'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${r.login}"></td>
          <td th:text="${#temporals.format(r.lastAccess, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/manager/manager/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>운영자 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|운영자 ${item.userId}|">운영자</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/managers/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/managers/{id}/unlock(id=${item.idx})}" method="post" id="unlockForm" class="m-0">
        <button type="button" class="btn btn-outline-warning btn-sm" data-confirm-form="unlockForm" data-confirm-ok="해제"
                data-confirm-message="잠금을 해제하시겠습니까?">잠금 해제</button>
      </form>
      <form th:action="@{/managers/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/managers}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>ID</th><td th:text="${item.userId}"></td></tr>
        <tr><th>이름</th><td th:text="${item.userNm}"></td></tr>
        <tr><th>이메일</th><td th:text="${item.userEmail}"></td></tr>
        <tr><th>전화</th><td th:text="${item.userPhone}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>상태</th><td th:text="${item.status}"></td></tr>
        <tr><th>로그인</th><td th:text="${item.login}"></td></tr>
        <tr>
          <th>잠금 상태</th>
          <td>
            <span th:if="${lockState == null}">정책 행 없음 (잠기지 않음)</span>
            <span th:if="${lockState != null and lockState.accountLock == 'Y'}" class="badge text-bg-danger"
                  th:text="|잠김 (실패 ${lockState.pwFailCnt}회)|">잠김</span>
            <span th:if="${lockState != null and lockState.accountLock != 'Y'}"
                  th:text="|정상 (실패 ${lockState.pwFailCnt}회)|">정상</span>
          </td>
        </tr>
        <tr><th>잠금 시각</th><td th:text="${#temporals.format(item.blockTime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>최근 접속</th><td th:text="${#temporals.format(item.lastAccess, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>비고</th><td class="fa-pre" th:text="${item.etc}"></td></tr>
        <tr><th>알림 유형</th><td th:text="${item.alramType}"></td></tr>
        <tr><th>알림 레벨</th><td th:text="${item.alramLevel}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/manager/manager/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '운영자 등록' : '운영자 수정'">운영자</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '운영자 등록' : '운영자 수정'">운영자</h1>
  <form th:action="${isNew} ? @{/managers} : @{/managers/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;" autocomplete="off">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">ID <span class="text-danger">*</span></label>
        <input th:field="*{userId}" class="form-control" th:readonly="${!isNew}" th:classappend="${#fields.hasErrors('userId')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{userId}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">고객사 <span class="text-danger">*</span></label>
        <select th:field="*{companyIdx}" class="form-select" th:classappend="${#fields.hasErrors('companyIdx')} ? 'is-invalid'">
          <option value="">선택</option>
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
        </select>
        <div class="invalid-feedback" th:errors="*{companyIdx}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label" th:text="${isNew} ? '비밀번호 *' : '비밀번호 (변경 시에만 입력)'">비밀번호</label>
        <input type="password" th:field="*{password}" class="form-control" autocomplete="new-password"
               th:classappend="${#fields.hasErrors('password')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{password}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">비밀번호 확인</label>
        <input type="password" th:field="*{passwordConfirm}" class="form-control" autocomplete="new-password"
               th:classappend="${#fields.hasErrors('passwordConfirm')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{passwordConfirm}"></div>
      </div>
      <div class="col-md-4"><label class="form-label">이름</label><input th:field="*{userNm}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">이메일</label><input th:field="*{userEmail}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">전화</label><input th:field="*{userPhone}" class="form-control"></div>
      <div class="col-md-4">
        <label class="form-label">상태 <span class="text-danger">*</span></label>
        <select th:field="*{status}" class="form-select"><option value="활성">활성</option><option value="비활성">비활성</option></select>
      </div>
      <div class="col-md-4"><label class="form-label">알림 유형</label><input th:field="*{alramType}" class="form-control"></div>
      <div class="col-md-4"><label class="form-label">알림 레벨</label><input th:field="*{alramLevel}" class="form-control"></div>
      <div class="col-12"><label class="form-label">비고</label><textarea th:field="*{etc}" rows="3" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/managers} : @{/managers/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.manager.web.ManagerControllerWebTest'`
Expected: PASS (8개).

- [ ] **Step 10: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: 전부 PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/manager src/main/resources/templates/manager src/test/java/com/crosscert/fidoadmin/manager
git commit -m "feat: 운영자 화면 (CCFA_MANAGER, SUPER 전용)

- USER_PW 는 SHA-256 인코딩 저장, 목록·상세 DTO 에서 제외
- 등록 시 비밀번호 필수·확인 일치 검증(validate 훅)
- USER_ID 중복 등록 차단, 자기 자신 삭제 차단
- PW_POLICY 잠금 상태 표시와 잠금 해제(감사 STATUS)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 5: 앱 ID (APPID) CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/AppidService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppidController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppidForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppidSearchForm.java`
- Create: `src/main/resources/templates/fido/appid/list.html`
- Create: `src/main/resources/templates/fido/appid/detail.html`
- Create: `src/main/resources/templates/fido/appid/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/AppidServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/AppidControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Appid` 엔티티(idx, companyIdx, appid, memo, status, device, deviceDefault, servicename, createdtime, updatedtime), `AppidRepository extends AdminRepository<Appid, Long>`(이미 존재, `countByCompanyIdx`), `CrudService`, `CrudController`, `Specs`, `ByteSize`, `CompanyLookup.all()/names()/name(Long)`, `TenantContext.isSuper()`.
- Produces: `AppidService extends CrudService<Appid, Long, AppidSearchForm>` — 생성자 `(AppidRepository, AuditLogger)`, `companyIdxAttribute()="companyIdx"`, `tableName()="APPID"`, `sortableProperties()={"idx","appid","servicename","createdtime"}`, `applyDefaults`: status 비면 `"use"`, deviceDefault 비면 `"F"`. `AppidController` — `@RequestMapping("/appids")`, viewDir `fido/appid`. 뒤 태스크가 참조하는 것은 없다(고객사 삭제 차단은 1부 `CompanyService` 가 이미 `AppidRepository.countByCompanyIdx` 를 쓴다).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/AppidServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.web.AppidSearchForm;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AppidServiceTest {

    AppidRepository repo = mock(AppidRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    AppidService service = new AppidService(repo, audit);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void defaultsAndTimestampsFilledOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { Appid a = inv.getArgument(0); a.setIdx(10L); return a; });
        Appid in = new Appid(); in.setAppid("https://kbstar.com/facets.json"); in.setCompanyIdx(1L);

        Appid out = service.create(in);

        assertThat(out.getStatus()).isEqualTo("use");
        assertThat(out.getDeviceDefault()).isEqualTo("F");
        assertThat(out.getCreatedtime()).isNotNull();
        assertThat(out.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "APPID CREATE 10");
    }

    @Test void explicitValuesAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appid in = new Appid(); in.setAppid("x"); in.setStatus("unuse"); in.setDeviceDefault("T");
        Appid out = service.create(in);
        assertThat(out.getStatus()).isEqualTo("unuse");
        assertThat(out.getDeviceDefault()).isEqualTo("T");
    }

    /** companyIdxAttribute 배선 확인: COMPANY 역할 등록은 폼의 고객사 값을 무시하고 자기 고객사로 강제된다. */
    @Test void companyRoleCreateForcesOwnTenant() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appid in = new Appid(); in.setAppid("x"); in.setCompanyIdx(999L);
        assertThat(service.create(in).getCompanyIdx()).isEqualTo(1L);
    }

    @Test void searchGoesThroughSpecificationWithGivenPageable() {
        login(1L);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        AppidSearchForm f = new AppidSearchForm(); f.setAppid("kb"); f.setStatus("use");
        Pageable p = PageRequest.of(0, 20, Sort.by("idx"));

        service.search(f, p);

        verify(repo).findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(p));
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "appid", "servicename", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.AppidServiceTest'`
Expected: FAIL — `AppidService`, `AppidSearchForm` 없음(컴파일 오류).

- [ ] **Step 3: 검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/AppidSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AppidSearchForm extends SearchForm {
    private String appid;
    private String servicename;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appid", appid); m.put("servicename", servicename); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/AppidService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.web.AppidSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** APPID. COMPANY_IDX 로 테넌트 격리. ERD 기본값 STATUS='use', DEVICE_DEFAULT='F'. */
@Service
public class AppidService extends CrudService<Appid, Long, AppidSearchForm> {

    public AppidService(AppidRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Appid> toSpecification(AppidSearchForm f) {
        return Specs.all(
            Specs.like("appid", f.getAppid()),
            Specs.like("servicename", f.getServicename()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Appid e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Appid e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Appid e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "APPID"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "appid", "servicename", "createdtime"); }

    @Override protected void applyDefaults(Appid e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("use");
        if (e.getDeviceDefault() == null || e.getDeviceDefault().isBlank()) e.setDeviceDefault("F");
    }
    @Override protected void touchCreated(Appid e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(Appid e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.AppidServiceTest'`
Expected: PASS (5개).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/AppidControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.service.AppidService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AppidController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class AppidControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AppidService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Appid appid(long idx) {
        Appid a = new Appid(); a.setIdx(idx); a.setCompanyIdx(1L); a.setAppid("https://kbstar.com/facets.json");
        a.setServicename("kbstar"); a.setStatus("use"); a.setDevice("android"); a.setDeviceDefault("T");
        return a;
    }

    private CcfaCompany kb() { CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행"); return c; }

    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(appid(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/appids").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/list"))
            .andExpect(content().string(containsString("https://kbstar.com/facets.json")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(companies.all()).thenReturn(List.of(kb()));
        mvc.perform(get("/appids").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void blankAppidShowsFormAgain() throws Exception {
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Appid saved = appid(5L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("5");
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "https://kbstar.com/facets.json").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appids/5"));
    }

    @Test void detailRendersAllColumns() throws Exception {
        when(service.get(3L)).thenReturn(appid(3L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/appids/3").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/detail"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(containsString("KB국민은행")));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/appids").with(user(companyUser)).param("appid", "x")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.AppidControllerWebTest'`
Expected: FAIL — `AppidController` 없음.

- [ ] **Step 7: 폼·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/AppidForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** APPID 입력 폼. 길이 제한은 ERD 값(바이트). 식별자(IDX)는 폼에 두지 않는다. */
@Getter @Setter
public class AppidForm {
    @NotBlank @ByteSize(max = 128) private String appid;
    @ByteSize(max = 512) private String memo;
    @NotBlank @ByteSize(max = 12) private String status = "use";
    @ByteSize(max = 128) private String device;
    @NotBlank @ByteSize(max = 1) private String deviceDefault = "F";
    @ByteSize(max = 512) private String servicename;
    /** SUPER 만 선택. COMPANY 는 서비스가 자기 고객사로 강제한다. */
    private Long companyIdx;

    public static AppidForm from(Appid a) {
        AppidForm f = new AppidForm();
        f.appid = a.getAppid(); f.memo = a.getMemo(); f.status = a.getStatus(); f.device = a.getDevice();
        f.deviceDefault = a.getDeviceDefault(); f.servicename = a.getServicename(); f.companyIdx = a.getCompanyIdx();
        return f;
    }

    public void applyTo(Appid a) {
        a.setAppid(appid); a.setMemo(memo); a.setStatus(status); a.setDevice(device);
        a.setDeviceDefault(deviceDefault); a.setServicename(servicename); a.setCompanyIdx(companyIdx);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/AppidController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.service.AppidService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/appids")
@RequiredArgsConstructor
public class AppidController extends CrudController<Appid, Long, AppidForm, AppidSearchForm> {

    private final AppidService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Appid, Long, AppidSearchForm> service() { return service; }
    @Override protected String basePath() { return "/appids"; }
    @Override protected String viewDir() { return "fido/appid"; }
    @Override protected AppidSearchForm newSearchForm() { return new AppidSearchForm(); }
    @Override protected AppidForm newForm() { return new AppidForm(); }
    @Override protected AppidForm toForm(Appid e) { return AppidForm.from(e); }
    @Override protected Appid toEntity(AppidForm f) { Appid a = new Appid(); f.applyTo(a); return a; }
    @Override protected void applyForm(AppidForm f, Appid e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(Appid e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
    @Override protected void populateFormModel(Model model) {
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/fido/appid/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>앱 ID</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">앱 ID</h1>
    <a th:href="@{/appids/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/appids}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">APPID</label><input name="appid" th:value="${search.appid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">서비스명</label><input name="servicename" th:value="${search.servicename}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="use" th:selected="${search.status == 'use'}">use</option>
        <option value="unuse" th:selected="${search.status == 'unuse'}">unuse</option>
      </select>
    </div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/appids}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>APPID</th><th>서비스명</th><th>상태</th><th>기기</th><th>기본기기</th><th>고객사</th><th>등록일</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/appids/{id}(id=${r.idx})}" th:text="${r.appid}">appid</a></td>
          <td th:text="${r.servicename}"></td>
          <td><span class="badge" th:classappend="${r.status == 'use'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${r.device}"></td>
          <td th:text="${r.deviceDefault}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/appid/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>앱 ID 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|앱 ID #${item.idx}|">앱 ID</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/appids/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/appids/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/appids}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>APPID</th><td class="fa-pre" th:text="${item.appid}"></td></tr>
        <tr><th>메모</th><td class="fa-pre" th:text="${item.memo}"></td></tr>
        <tr><th>상태</th><td th:text="${item.status}"></td></tr>
        <tr><th>기기</th><td th:text="${item.device}"></td></tr>
        <tr><th>기본기기</th><td th:text="${item.deviceDefault}"></td></tr>
        <tr><th>서비스명</th><td th:text="${item.servicename}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/appid/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '앱 ID 등록' : '앱 ID 수정'">앱 ID</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '앱 ID 등록' : '앱 ID 수정'">앱 ID</h1>
  <form th:action="${isNew} ? @{/appids} : @{/appids/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6" sec:authorize="hasRole('SUPER')">
        <label class="form-label">고객사</label>
        <select th:field="*{companyIdx}" class="form-select">
          <option value="">선택</option>
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
        </select>
      </div>
      <div class="col-12">
        <label class="form-label">APPID <span class="text-danger">*</span></label>
        <input th:field="*{appid}" class="form-control" th:classappend="${#fields.hasErrors('appid')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{appid}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">서비스명</label><input th:field="*{servicename}" class="form-control"></div>
      <div class="col-md-3">
        <label class="form-label">상태 <span class="text-danger">*</span></label>
        <select th:field="*{status}" class="form-select"><option value="use">use</option><option value="unuse">unuse</option></select>
      </div>
      <div class="col-md-3">
        <label class="form-label">기본기기 <span class="text-danger">*</span></label>
        <select th:field="*{deviceDefault}" class="form-select"><option value="F">F</option><option value="T">T</option></select>
      </div>
      <div class="col-md-6"><label class="form-label">기기</label><input th:field="*{device}" class="form-control"></div>
      <div class="col-12"><label class="form-label">메모</label><textarea th:field="*{memo}" rows="3" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/appids} : @{/appids/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.AppidControllerWebTest'`
Expected: PASS (6개).

- [ ] **Step 10: 로컬 확인과 커밋**

Docker Oracle 이 떠 있으면 `./gradlew bootRun --args='--spring.profiles.active=local'` 후 `kbadmin` 으로 `/appids` 목록(시드 2건, 고객사 2 의 1건은 보이지 않아야 함) → 등록 → 상세 → 수정 → 삭제를 확인한다. `superuser` 로는 3건 전부와 고객사 select 가 보여야 한다.

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/service/AppidService.java src/main/java/com/crosscert/fidoadmin/fido/web/Appid*.java src/main/resources/templates/fido/appid src/test/java/com/crosscert/fidoadmin/fido/service/AppidServiceTest.java src/test/java/com/crosscert/fidoadmin/fido/web/AppidControllerWebTest.java
git commit -m "feat: 앱 ID(APPID) CRUD 화면

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 6: 앱 서버 (APPSERVER) CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverSearchForm.java`
- Create: `src/main/resources/templates/fido/appserver/list.html`
- Create: `src/main/resources/templates/fido/appserver/detail.html`
- Create: `src/main/resources/templates/fido/appserver/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Appserver` 엔티티(idx, companyIdx, memberCode, memberId, type, note, createdtime, updatedtime), `AdminRepository`, `CrudService`, `CrudController`, `Specs`, `ByteSize`, `CompanyLookup`, `TenantContext`.
- Produces: `AppserverRepository extends AdminRepository<Appserver, Long>`; `AppserverService extends CrudService<Appserver, Long, AppserverSearchForm>` — 생성자 `(AppserverRepository, AuditLogger)`, `companyIdxAttribute()="companyIdx"`, `tableName()="APPSERVER"`, `sortableProperties()={"idx","memberCode","memberId","createdtime"}`, `applyDefaults`: type 비면 `"use"`. `AppserverController` — `@RequestMapping("/appservers")`, viewDir `fido/appserver`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AppserverServiceTest {

    AppserverRepository repo = mock(AppserverRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    AppserverService service = new AppserverService(repo, audit);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void typeDefaultsToUseAndTimestampsSet() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { Appserver a = inv.getArgument(0); a.setIdx(7L); return a; });
        Appserver in = new Appserver(); in.setMemberCode("KB01"); in.setMemberId("kbsvr");

        Appserver out = service.create(in);

        assertThat(out.getType()).isEqualTo("use");
        assertThat(out.getCreatedtime()).isNotNull();
        assertThat(out.getUpdatedtime()).isEqualTo(out.getCreatedtime());
        verify(audit).log(AuditType.CREATE, "APPSERVER CREATE 7");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        login(1L);
        Appserver existing = new Appserver(); existing.setIdx(3L); existing.setCompanyIdx(1L);
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        existing.setCreatedtime(created); existing.setUpdatedtime(created);
        when(repo.findById(3L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(3L, a -> a.setNote("변경"));

        assertThat(existing.getCreatedtime()).isEqualTo(created);
        assertThat(existing.getUpdatedtime()).isAfter(created);
        verify(audit).log(AuditType.UPDATE, "APPSERVER UPDATE 3");
    }

    @Test void companyRoleCreateForcesOwnTenant() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberCode("X"); in.setMemberId("Y"); in.setCompanyIdx(999L);
        assertThat(service.create(in).getCompanyIdx()).isEqualTo(1L);
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "memberCode", "memberId", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.AppserverServiceTest'`
Expected: FAIL — `AppserverRepository`, `AppserverService` 없음.

- [ ] **Step 3: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Appserver;

public interface AppserverRepository extends AdminRepository<Appserver, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/AppserverSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AppserverSearchForm extends SearchForm {
    private String memberCode;
    private String memberId;
    private String type;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memberCode", memberCode); m.put("memberId", memberId); m.put("type", type);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import com.crosscert.fidoadmin.fido.web.AppserverSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** APPSERVER. COMPANY_IDX 로 테넌트 격리. ERD 기본값 TYPE='use'. */
@Service
public class AppserverService extends CrudService<Appserver, Long, AppserverSearchForm> {

    public AppserverService(AppserverRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Appserver> toSpecification(AppserverSearchForm f) {
        return Specs.all(
            Specs.like("memberCode", f.getMemberCode()),
            Specs.like("memberId", f.getMemberId()),
            Specs.eq("type", f.getType()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Appserver e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Appserver e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Appserver e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "APPSERVER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "memberCode", "memberId", "createdtime"); }

    @Override protected void applyDefaults(Appserver e) {
        if (e.getType() == null || e.getType().isBlank()) e.setType("use");
    }
    @Override protected void touchCreated(Appserver e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(Appserver e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.AppserverServiceTest'`
Expected: PASS (4개).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AppserverController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class AppserverControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AppserverService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Appserver server(long idx) {
        Appserver a = new Appserver(); a.setIdx(idx); a.setCompanyIdx(1L); a.setMemberCode("KB01");
        a.setMemberId("kbsvr01"); a.setType("use"); a.setNote("스타뱅킹 서버");
        return a;
    }

    @Test void listRendersMemberCode() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(server(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/appservers").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/list"))
            .andExpect(content().string(containsString("KB01")))
            .andExpect(content().string(containsString("kbsvr01")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void blankMemberIdShowsFormAgain() throws Exception {
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "KB01").param("memberId", "").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"));
    }

    /** MEMBER_CODE 는 VARCHAR2(32). 33바이트는 저장 전에 폼에서 거부되어야 한다. */
    @Test void memberCodeOverByteLimitIsRejectedWithMessage() throws Exception {
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "a".repeat(33)).param("memberId", "id").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(server(9L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "KB01").param("memberId", "kbsvr01").param("type", "use"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appservers/9"));
    }

    @Test void detailRendersColumns() throws Exception {
        when(service.get(2L)).thenReturn(server(2L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/appservers/2").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("스타뱅킹 서버")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.AppserverControllerWebTest'`
Expected: FAIL — `AppserverController` 없음.

- [ ] **Step 7: 폼·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/AppserverForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** APPSERVER 입력 폼. MEMBER_CODE, MEMBER_ID, TYPE 은 ERD NOT NULL. */
@Getter @Setter
public class AppserverForm {
    @NotBlank @ByteSize(max = 32) private String memberCode;
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
        a.setMemberCode(memberCode); a.setMemberId(memberId); a.setType(type);
        a.setNote(note); a.setCompanyIdx(companyIdx);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/AppserverController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/appservers")
@RequiredArgsConstructor
public class AppserverController extends CrudController<Appserver, Long, AppserverForm, AppserverSearchForm> {

    private final AppserverService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Appserver, Long, AppserverSearchForm> service() { return service; }
    @Override protected String basePath() { return "/appservers"; }
    @Override protected String viewDir() { return "fido/appserver"; }
    @Override protected AppserverSearchForm newSearchForm() { return new AppserverSearchForm(); }
    @Override protected AppserverForm newForm() { return new AppserverForm(); }
    @Override protected AppserverForm toForm(Appserver e) { return AppserverForm.from(e); }
    @Override protected Appserver toEntity(AppserverForm f) { Appserver a = new Appserver(); f.applyTo(a); return a; }
    @Override protected void applyForm(AppserverForm f, Appserver e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(Appserver e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
    @Override protected void populateFormModel(Model model) {
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/fido/appserver/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>앱 서버</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">앱 서버</h1>
    <a th:href="@{/appservers/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/appservers}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">코드</label><input name="memberCode" th:value="${search.memberCode}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">ID</label><input name="memberId" th:value="${search.memberId}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">유형</label>
      <select name="type" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="use" th:selected="${search.type == 'use'}">use</option>
        <option value="unuse" th:selected="${search.type == 'unuse'}">unuse</option>
      </select>
    </div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/appservers}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>코드</th><th>ID</th><th>유형</th><th>비고</th><th>고객사</th><th>등록일</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/appservers/{id}(id=${r.idx})}" th:text="${r.memberCode}">code</a></td>
          <td th:text="${r.memberId}"></td>
          <td><span class="badge" th:classappend="${r.type == 'use'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.type}"></span></td>
          <td th:text="${#strings.abbreviate(r.note, 40)}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="7" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/appserver/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>앱 서버 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|앱 서버 #${item.idx}|">앱 서버</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/appservers/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/appservers/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/appservers}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>코드</th><td th:text="${item.memberCode}"></td></tr>
        <tr><th>ID</th><td th:text="${item.memberId}"></td></tr>
        <tr><th>유형</th><td th:text="${item.type}"></td></tr>
        <tr><th>비고</th><td class="fa-pre" th:text="${item.note}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/appserver/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '앱 서버 등록' : '앱 서버 수정'">앱 서버</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '앱 서버 등록' : '앱 서버 수정'">앱 서버</h1>
  <form th:action="${isNew} ? @{/appservers} : @{/appservers/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6" sec:authorize="hasRole('SUPER')">
        <label class="form-label">고객사</label>
        <select th:field="*{companyIdx}" class="form-select">
          <option value="">선택</option>
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
        </select>
      </div>
      <div class="col-md-6">
        <label class="form-label">코드 <span class="text-danger">*</span></label>
        <input th:field="*{memberCode}" class="form-control" th:classappend="${#fields.hasErrors('memberCode')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{memberCode}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">ID <span class="text-danger">*</span></label>
        <input th:field="*{memberId}" class="form-control" th:classappend="${#fields.hasErrors('memberId')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{memberId}"></div>
      </div>
      <div class="col-md-3">
        <label class="form-label">유형 <span class="text-danger">*</span></label>
        <select th:field="*{type}" class="form-select"><option value="use">use</option><option value="unuse">unuse</option></select>
      </div>
      <div class="col-12"><label class="form-label">비고</label><input th:field="*{note}" class="form-control"></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/appservers} : @{/appservers/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.AppserverControllerWebTest'`
Expected: PASS (5개).

- [ ] **Step 10: 로컬 확인과 커밋**

Docker Oracle 이 떠 있으면 `kbadmin` 으로 `/appservers` 목록(시드 2건) → 등록 → 수정 → 삭제를 확인한다.

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java src/main/java/com/crosscert/fidoadmin/fido/web/Appserver*.java src/main/resources/templates/fido/appserver src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java
git commit -m "feat: 앱 서버(APPSERVER) CRUD 화면

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 7: 사용자 (USERINFO) 조회 + 상태 변경

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/UserinfoService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/UserController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/UserSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/UserRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/UserView.java`
- Create: `src/main/resources/templates/fido/user/list.html`
- Create: `src/main/resources/templates/fido/user/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/UserinfoServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/UserControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Userinfo` 엔티티(idx, companyIdx, bioType, servicename, userid, aaid, authenticatorversion, keyid, pubkey, certificate, signcounter, uvs, status, regtime, createdtime — PUBKEY/CERTIFICATE 민감), `UserinfoRepository extends AdminRepository<Userinfo, Long>`(이미 존재), `CrudService`, `ReadOnlyController`, `AuditType.STATUS`, `TenantMismatchException`, `CompanyLookup`, `TenantContext`.
- Produces: `UserinfoService extends CrudService<Userinfo, Long, UserSearchForm>` — 생성자 `(UserinfoRepository, AuditLogger)`, `companyIdxAttribute()="companyIdx"`, `tableName()="USERINFO"`, `sortableProperties()={"idx","userid","servicename","regtime"}`, 추가 API `@Transactional public Userinfo changeStatus(Long id, String status)` — 허용값 `"O"`, `"X"`, 그 외 `IllegalArgumentException("허용되지 않는 상태입니다: " + status)`; 테넌트 검사(`get`)를 거친 뒤 저장하고 `audit.log(AuditType.STATUS, "USERINFO STATUS <idx> <old>-><new>")`. `UserController` — `@RequestMapping("/users")`, viewDir `fido/user`, `POST /users/{id}/status?status=O|X`. `UserView.head16(String)` — 앞 16자.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/UserinfoServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.TenantMismatchException;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserinfoServiceTest {

    UserinfoRepository repo = mock(UserinfoRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    UserinfoService service = new UserinfoService(repo, audit);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private Userinfo user(long idx, long companyIdx, String status) {
        Userinfo u = new Userinfo(); u.setIdx(idx); u.setCompanyIdx(companyIdx); u.setUserid("user001");
        u.setStatus(status); u.setPubkey("PUBKEY"); u.setCertificate("CERT");
        return u;
    }

    @Test void changeStatusSavesAndAuditsOldToNew() {
        login(1L);
        Userinfo existing = user(5L, 1L, "O");
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Userinfo out = service.changeStatus(5L, "X");

        assertThat(out.getStatus()).isEqualTo("X");
        verify(repo).save(existing);
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 5 O->X");
    }

    @Test void changeStatusRejectsUnknownValueBeforeTouchingRepository() {
        login(1L);
        assertThatThrownBy(() -> service.changeStatus(5L, "Z"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Z");
        verify(repo, never()).findById(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void changeStatusRejectsNull() {
        login(1L);
        assertThatThrownBy(() -> service.changeStatus(5L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 상태 변경도 테넌트 검사를 거친다. 다른 고객사의 사용자는 404(TenantMismatchException). */
    @Test void companyRoleCannotChangeOtherTenantStatus() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "O")));
        assertThatThrownBy(() -> service.changeStatus(9L, "X")).isInstanceOf(TenantMismatchException.class);
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void superCanChangeAnyTenantStatus() {
        login(0L);
        when(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "X")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.changeStatus(9L, "O").getStatus()).isEqualTo("O");
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 9 X->O");
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "userid", "servicename", "regtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.UserinfoServiceTest'`
Expected: FAIL — `UserinfoService`, `UserSearchForm` 없음.

- [ ] **Step 3: 검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/UserSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserSearchForm extends SearchForm {
    private String userid;
    private String servicename;
    private String aaid;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("servicename", servicename); m.put("aaid", aaid); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/UserinfoService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.fido.web.UserSearchForm;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USERINFO 조회 + 상태 변경. 등록·수정·삭제는 컨트롤러에서 노출하지 않는다(설계 1.2 "조회 + 상태 변경").
 * 상태는 'O'(정상) / 'X'(해지) 만 허용한다.
 */
@Service
public class UserinfoService extends CrudService<Userinfo, Long, UserSearchForm> {

    public static final Set<String> STATUSES = Set.of("O", "X");

    public UserinfoService(UserinfoRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Userinfo> toSpecification(UserSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("servicename", f.getServicename()),
            Specs.like("aaid", f.getAaid()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Userinfo e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Userinfo e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Userinfo e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "USERINFO"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userid", "servicename", "regtime"); }

    /** 상태 변경. get() 이 테넌트 검사를 한다. 감사 로그 TYPE=STATUS. */
    @Transactional
    public Userinfo changeStatus(Long id, String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("허용되지 않는 상태입니다: " + status);
        }
        Userinfo u = get(id);
        String old = u.getStatus();
        u.setStatus(status);
        Userinfo saved = repository.save(u);
        audit.log(AuditType.STATUS, "USERINFO STATUS " + idOf(saved) + " " + old + "->" + status);
        return saved;
    }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.UserinfoServiceTest'`
Expected: PASS (6개).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/UserControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class UserControllerWebTest {

    static final String FULL_PUBKEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-FULL";
    static final String FULL_CERT = "MIIB-CERT-SAMPLE-FULL-VALUE-DO-NOT-SHOW";

    @Autowired MockMvc mvc;
    @MockitoBean UserinfoService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Userinfo user(long idx) {
        Userinfo u = new Userinfo(); u.setIdx(idx); u.setCompanyIdx(1L); u.setServicename("kbstar"); u.setUserid("user001");
        u.setAaid("0012#0001"); u.setStatus("O"); u.setSigncounter(12L); u.setKeyid("keyid-001");
        u.setPubkey(FULL_PUBKEY); u.setCertificate(FULL_CERT); u.setRegtime(LocalDateTime.of(2026, 9, 1, 10, 0));
        return u;
    }

    @Test void listNeverExposesPubkeyOrCertificate() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(user(1L))));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/users").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/list"))
            .andExpect(content().string(containsString("user001")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(not(containsString("MFkwEwYHKoZIzj0C"))))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }

    @Test void detailShowsOnlyFirst16CharsOfSensitiveColumns() throws Exception {
        when(service.get(1L)).thenReturn(user(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/detail"))
            .andExpect(content().string(containsString("MFkwEwYHKoZIzj0C")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(containsString("MIIB-CERT-SAMPLE")))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(containsString("/users/1/status")));
    }

    @Test void detailHasNoEditOrDeleteLinks() throws Exception {
        when(service.get(1L)).thenReturn(user(1L));
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(content().string(not(containsString("/users/1/edit"))))
            .andExpect(content().string(not(containsString("/users/1/delete"))));
    }

    @Test void statusChangeRedirectsWithFlash() throws Exception {
        when(service.changeStatus(1L, "X")).thenReturn(user(1L));
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "X"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashSuccess", "상태가 변경되었습니다."));
        verify(service).changeStatus(eq(1L), eq("X"));
    }

    @Test void invalidStatusRedirectsWithError() throws Exception {
        when(service.changeStatus(1L, "Z")).thenThrow(new IllegalArgumentException("허용되지 않는 상태입니다: Z"));
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "Z"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashError", "허용되지 않는 상태입니다: Z"));
    }

    @Test void statusChangeWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/users/1/status").with(user(companyUser)).param("status", "X")).andExpect(status().isForbidden());
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/users").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void head16CutsAndPassesNull() {
        org.assertj.core.api.Assertions.assertThat(UserView.head16(FULL_PUBKEY)).isEqualTo("MFkwEwYHKoZIzj0C");
        org.assertj.core.api.Assertions.assertThat(UserView.head16("short")).isEqualTo("short");
        org.assertj.core.api.Assertions.assertThat(UserView.head16(null)).isNull();
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.UserControllerWebTest'`
Expected: FAIL — `UserController`, `UserView` 없음.

- [ ] **Step 7: 출력 DTO·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/UserRow.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Userinfo;
import java.time.LocalDateTime;

/** USERINFO 목록 행. PUBKEY/CERTIFICATE 는 싣지 않는다. */
public record UserRow(Long idx, Long companyIdx, String servicename, String userid, String aaid,
                      String status, Long signcounter, LocalDateTime regtime) {

    public static UserRow from(Userinfo u) {
        return new UserRow(u.getIdx(), u.getCompanyIdx(), u.getServicename(), u.getUserid(), u.getAaid(),
            u.getStatus(), u.getSigncounter(), u.getRegtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/UserView.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Userinfo;
import java.time.LocalDateTime;

/** USERINFO 상세. PUBKEY/CERTIFICATE 는 앞 16자만(설계 2.2). 전체 값은 어떤 경로로도 뷰에 가지 않는다. */
public record UserView(Long idx, Long companyIdx, Long bioType, String servicename, String userid, String aaid,
                       Long authenticatorversion, String keyid, String pubkeyHead, String certificateHead,
                       Long signcounter, String uvs, String status, LocalDateTime regtime, LocalDateTime createdtime) {

    public static UserView from(Userinfo u) {
        return new UserView(u.getIdx(), u.getCompanyIdx(), u.getBioType(), u.getServicename(), u.getUserid(), u.getAaid(),
            u.getAuthenticatorversion(), u.getKeyid(), head16(u.getPubkey()), head16(u.getCertificate()),
            u.getSigncounter(), u.getUvs(), u.getStatus(), u.getRegtime(), u.getCreatedtime());
    }

    /** 앞 16자. null 은 null. */
    public static String head16(String s) {
        if (s == null) return null;
        return s.length() <= 16 ? s : s.substring(0, 16);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/UserController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 사용자 조회 + 상태 변경('O'/'X'). 등록·수정·삭제 없음. */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController extends ReadOnlyController<Userinfo, Long, UserSearchForm> {

    private final UserinfoService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Userinfo, Long, UserSearchForm> service() { return service; }
    @Override protected String basePath() { return "/users"; }
    @Override protected String viewDir() { return "fido/user"; }
    @Override protected Object toListView(Userinfo e) { return UserRow.from(e); }
    @Override protected Object toDetailView(Userinfo e) { return UserView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(Userinfo e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
        model.addAttribute("statuses", UserinfoService.STATUSES.stream().sorted().toList());
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            service.changeStatus(id, status);
            redirect.addFlashAttribute("flashSuccess", "상태가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/users/" + id;
    }
}
```

- [ ] **Step 8: 템플릿 작성**

확인 모달의 확인 버튼 문구는 Task 1 에서 `admin.js` 에 추가한 `data-confirm-ok` 속성으로 바꾼다(상태 변경 버튼이 "삭제" 로 보이지 않도록). 이 태스크에서 `admin.js` 는 건드리지 않는다.

`src/main/resources/templates/fido/user/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>사용자</title></head>
<body>
<main>
  <h1 class="h4 mb-3">사용자</h1>

  <form method="get" th:action="@{/users}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">USERID</label><input name="userid" th:value="${search.userid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">서비스명</label><input name="servicename" th:value="${search.servicename}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">AAID</label><input name="aaid" th:value="${search.aaid}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="O" th:selected="${search.status == 'O'}">O (정상)</option>
        <option value="X" th:selected="${search.status == 'X'}">X (해지)</option>
      </select>
    </div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/users}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>USERID</th><th>서비스명</th><th>AAID</th><th>상태</th><th>서명횟수</th><th>고객사</th><th>등록일시</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/users/{id}(id=${r.idx})}" th:text="${r.userid}">userid</a></td>
          <td th:text="${r.servicename}"></td>
          <td class="fa-mono" th:text="${r.aaid}"></td>
          <td><span class="badge" th:classappend="${r.status == 'O'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${r.signcounter}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${#temporals.format(r.regtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/user/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>사용자 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|사용자 #${item.idx}|">사용자</h1>
    <a th:href="@{/users}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>USERID</th><td th:text="${item.userid}"></td></tr>
        <tr><th>서비스명</th><td th:text="${item.servicename}"></td></tr>
        <tr><th>AAID</th><td class="fa-mono" th:text="${item.aaid}"></td></tr>
        <tr><th>생체 유형</th><td th:text="${item.bioType}"></td></tr>
        <tr><th>인증기기 버전</th><td th:text="${item.authenticatorversion}"></td></tr>
        <tr><th>KEYID</th><td class="fa-mono fa-pre" th:text="${item.keyid}"></td></tr>
        <tr><th>PUBKEY (앞 16자)</th><td class="fa-mono" th:text="${item.pubkeyHead != null} ? |${item.pubkeyHead}…| : ''"></td></tr>
        <tr><th>CERTIFICATE (앞 16자)</th><td class="fa-mono" th:text="${item.certificateHead != null} ? |${item.certificateHead}…| : ''"></td></tr>
        <tr><th>서명횟수</th><td th:text="${item.signcounter}"></td></tr>
        <tr><th>UVS</th><td th:text="${item.uvs}"></td></tr>
        <tr>
          <th>상태</th>
          <td>
            <form th:action="@{/users/{id}/status(id=${item.idx})}" method="post" id="statusForm"
                  class="d-flex gap-2 align-items-center m-0">
              <span class="badge" th:classappend="${item.status == 'O'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${item.status}"></span>
              <select name="status" class="form-select form-select-sm w-auto">
                <option th:each="s : ${statuses}" th:value="${s}" th:text="${s == 'O'} ? 'O (정상)' : 'X (해지)'" th:selected="${item.status == s}"></option>
              </select>
              <button type="button" class="btn btn-outline-warning btn-sm" data-confirm-form="statusForm"
                      data-confirm-message="상태를 변경하시겠습니까?" data-confirm-ok="변경">상태 변경</button>
            </form>
          </td>
        </tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.regtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>생성일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.UserControllerWebTest'`
Expected: PASS (8개).

- [ ] **Step 10: 전체 테스트, 로컬 확인, 커밋**

Run: `./gradlew test`
Expected: 전부 PASS. Docker Oracle 이 떠 있으면 브라우저에서 (1) `/companies/1` 삭제 버튼 → 모달 문구 "정말 삭제하시겠습니까?" 와 확인 버튼 "삭제"(빨강), (2) `/users/1` 상태 변경 버튼 → 모달 문구 "상태를 변경하시겠습니까?" 와 확인 버튼 "변경"(파랑), 확인 후 배지가 바뀌고 플래시 "상태가 변경되었습니다." 가 뜨는지 확인한다. `/logs/audit` 에 `TYPE=STATUS`, 메시지 `USERINFO STATUS 1 O->X` 가 남아야 한다.

```bash
git add src/main/java/com/crosscert/fidoadmin/fido/service/UserinfoService.java src/main/java/com/crosscert/fidoadmin/fido/web/User*.java src/main/resources/templates/fido/user src/test/java/com/crosscert/fidoadmin/fido/service/UserinfoServiceTest.java src/test/java/com/crosscert/fidoadmin/fido/web/UserControllerWebTest.java
git commit -m "feat: 사용자(USERINFO) 조회·상태 변경 화면, PUBKEY/CERTIFICATE 앞 16자만 표시

- 상태 'O'/'X' 만 허용, 감사 로그 TYPE=STATUS 로 old->new 기록

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 8: 조회 화면 — 챌린지 (CHALLENGE)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/ChallengeRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/ChallengeSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/ChallengeQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/ChallengeController.java`
- Create: `src/main/resources/templates/fido/challenge/list.html`
- Create: `src/main/resources/templates/fido/challenge/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/ChallengeQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/ChallengeControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Challenge` 엔티티(`idx, companyIdx, userid, servicename, challengecode, createtime, logidx, uv`), `AdminRepository<E, ID>`, `CrudService<E, ID, S>`, `ReadOnlyController<E, ID, S>`, `SearchForm`, `Specs`, `CompanyLookup.all()/names()/name(Long)`, `TenantContext.isSuper()`.
- Produces: `ChallengeRepository extends AdminRepository<Challenge, Long>`, `ChallengeQueryService extends CrudService<Challenge, Long, ChallengeSearchForm>` (`companyIdxAttribute() = "companyIdx"`, `defaultSort() = createtime DESC, idx DESC`, `sortableProperties() = {"idx", "createtime"}`), `ChallengeController` (`GET /challenges`, `GET /challenges/{id}`, 뷰 `fido/challenge/list`, `fido/challenge/detail`). 목록 모델에 `companyNames: Map<Long,String>`, SUPER 이면 `companies: List<CcfaCompany>`; 상세 모델에 `companyName: String`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/ChallengeQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.repository.ChallengeRepository;
import com.crosscert.fidoadmin.fido.web.ChallengeSearchForm;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ChallengeQueryServiceTest {

    ChallengeRepository repo = mock(ChallengeRepository.class);
    ChallengeQueryService service = new ChallengeQueryService(repo, mock(AuditLogger.class));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** 로그류는 최근 것이 먼저다. idx 를 뒤에 붙여 같은 시각의 순서를 고정한다. */
    @Test void defaultSortIsCreatetimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Challenge c = new Challenge(); c.setIdx(42L);
        assertThat(service.idOf(c)).isEqualTo("42");
    }

    /** COMPANY 로그인으로 검색하면 Specification 이 만들어져 repository 에 전달된다(테넌트 필터는 기반 테스트가 검증). */
    @Test void searchPassesSpecificationToRepository() {
        login(1L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        ChallengeSearchForm f = new ChallengeSearchForm();
        f.setUserid("user001");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
    }
}
```

- [ ] **Step 2: 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/ChallengeControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.service.ChallengeQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChallengeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class ChallengeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean ChallengeQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Challenge challenge() {
        Challenge c = new Challenge();
        c.setIdx(7L); c.setCompanyIdx(1L); c.setUserid("user001"); c.setServicename("kbstar");
        c.setChallengecode("chal-0001"); c.setCreatetime(LocalDateTime.of(2026, 9, 17, 10, 0)); c.setLogidx(-1L); c.setUv("fingerprint");
        return c;
    }

    @Test void companyUserSeesListWithoutCompanyFilterAndWithoutWriteButtons() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(challenge())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/challenges").param("userid", "user001").param("servicename", "kbstar").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/challenge/list"))
            .andExpect(content().string(containsString("chal-0001")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/challenges/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<ChallengeSearchForm> captor = ArgumentCaptor.forClass(ChallengeSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getServicename()).isEqualTo("kbstar");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/challenges").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }

    @Test void detailRendersAllColumns() throws Exception {
        when(service.get(7L)).thenReturn(challenge());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/challenges/7").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/challenge/detail"))
            .andExpect(content().string(containsString("chal-0001")))
            .andExpect(content().string(containsString("fingerprint")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.ChallengeQueryServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.ChallengeControllerWebTest'`
Expected: FAIL — `ChallengeRepository`, `ChallengeQueryService`, `ChallengeSearchForm`, `ChallengeController` 없음(컴파일 오류).

- [ ] **Step 4: 리포지토리·검색 폼·조회 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/ChallengeRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Challenge;

public interface ChallengeRepository extends AdminRepository<Challenge, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/ChallengeSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CHALLENGE 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). */
@Getter @Setter
public class ChallengeSearchForm extends SearchForm {
    private String userid;
    private String servicename;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("servicename", servicename);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/ChallengeQueryService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.repository.ChallengeRepository;
import com.crosscert.fidoadmin.fido.web.ChallengeSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CHALLENGE 조회 전용. create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class ChallengeQueryService extends CrudService<Challenge, Long, ChallengeSearchForm> {

    public ChallengeQueryService(ChallengeRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Challenge> toSpecification(ChallengeSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("servicename", f.getServicename()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Challenge e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Challenge e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Challenge e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CHALLENGE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
```

- [ ] **Step 5: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.ChallengeQueryServiceTest'`
Expected: PASS (웹 테스트는 아직 컨트롤러가 없어 실패).

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/ChallengeController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.service.ChallengeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 챌린지 조회. CLOB·민감 컬럼이 없어 엔티티를 그대로 뷰에 넘긴다. */
@Controller
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController extends ReadOnlyController<Challenge, Long, ChallengeSearchForm> {

    private final ChallengeQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Challenge, Long, ChallengeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/challenges"; }
    @Override protected String viewDir() { return "fido/challenge"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(Challenge entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
```

- [ ] **Step 7: 템플릿 작성**

`src/main/resources/templates/fido/challenge/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>챌린지</title></head>
<body>
<main>
  <h1 class="h4 mb-3">챌린지</h1>
  <form method="get" th:action="@{/challenges}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">USERID</label><input name="userid" th:value="${search.userid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">서비스명</label><input name="servicename" th:value="${search.servicename}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/challenges}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>USERID</th><th>서비스명</th><th>챌린지코드</th><th>UV</th><th>LOGIDX</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/challenges/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.userid}"></td>
          <td th:text="${r.servicename}"></td>
          <td class="fa-mono" th:text="${#strings.abbreviate(r.challengecode, 24)}"></td>
          <td th:text="${r.uv}"></td>
          <td th:text="${r.logidx}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/challenge/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>챌린지 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|챌린지 #${item.idx}|"></h1>
    <a th:href="@{/challenges}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>USERID</th><td th:text="${item.userid}"></td></tr>
      <tr><th>서비스명</th><td th:text="${item.servicename}"></td></tr>
      <tr><th>챌린지코드</th><td class="fa-mono" th:text="${item.challengecode}"></td></tr>
      <tr><th>생성일시</th><td th:text="${#temporals.format(item.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>LOGIDX</th><td th:text="${item.logidx}"></td></tr>
      <tr><th>UV</th><td th:text="${item.uv}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 8: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.ChallengeControllerWebTest'`
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/main/resources/templates/fido/challenge src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 챌린지 조회 화면

- CHALLENGE 목록(USERID·서비스명·기간)·상세, 테넌트 필터는 기반 위임
- 조회 전용이라 등록·수정·삭제 없음

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 9: 조회 화면 — 서명 (SIGN)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/SignRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/SignSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/SignRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/SignQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/SignController.java`
- Create: `src/main/resources/templates/fido/sign/list.html`
- Create: `src/main/resources/templates/fido/sign/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/SignQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/SignControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Sign` 엔티티(`idx, companyIdx, userid, assertion, dn, plaintext(CLOB), data, signature, memo, del, createtime`), 기반 클래스, `CompanyLookup`.
- Produces: `SignRepository extends AdminRepository<Sign, Long>`, `SignQueryService` (`companyIdxAttribute() = "companyIdx"`, `defaultSort() = createtime DESC, idx DESC`, `sortableProperties() = {"idx", "createtime"}`), `record SignRow(Long idx, Long companyIdx, String userid, String dn, String memo, String del, LocalDateTime createtime, String assertionHead)` + `static SignRow of(Sign)`, `SignController` (`GET /signs`, `GET /signs/{id}`, 뷰 `fido/sign/list`, `fido/sign/detail`). 목록은 `SignRow`(CLOB·서명값 제외), 상세는 엔티티 전체.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/SignQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.repository.SignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SignQueryServiceTest {

    SignQueryService service = new SignQueryService(mock(SignRepository.class), mock(AuditLogger.class));

    @Test void defaultSortIsCreatetimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Sign s = new Sign(); s.setIdx(3L);
        assertThat(service.idOf(s)).isEqualTo("3");
    }
}
```

- [ ] **Step 2: 웹 실패 테스트 작성**

핵심은 목록에 CLOB(PLAINTEXT)·서명값이 실리지 않고 상세에만 보이는 것이다. `th:text` 는 따옴표를 이스케이프하므로 마커 문자열에는 특수문자를 쓰지 않는다.

`src/test/java/com/crosscert/fidoadmin/fido/web/SignControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.service.SignQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class SignControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SignQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static final String PLAINTEXT_MARKER = "PLAINTEXT-MARKER-XYZ";
    static final String SIGNATURE_MARKER = "SIGNATURE-MARKER-XYZ";

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Sign sign() {
        Sign s = new Sign();
        s.setIdx(5L); s.setCompanyIdx(1L); s.setUserid("user001"); s.setAssertion("assertion-sample-001-" + "a".repeat(40));
        s.setDn("CN=user001"); s.setPlaintext(PLAINTEXT_MARKER); s.setData("data-001"); s.setSignature(SIGNATURE_MARKER);
        s.setMemo("이체"); s.setDel("N"); s.setCreatetime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return s;
    }

    /** 목록에는 CLOB(PLAINTEXT)·서명값이 실리지 않는다. ASSERTION 은 앞 32자만. */
    @Test void listHidesPlaintextAndSignature() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(sign())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/signs").param("userid", "user001").param("del", "N").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/list"))
            .andExpect(content().string(containsString("CN=user001")))
            .andExpect(content().string(containsString("assertion-sample-001-aaaaaaaaaaa")))
            .andExpect(content().string(not(containsString("a".repeat(40)))))
            .andExpect(content().string(not(containsString(PLAINTEXT_MARKER))))
            .andExpect(content().string(not(containsString(SIGNATURE_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/signs/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<SignSearchForm> captor = ArgumentCaptor.forClass(SignSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getDel()).isEqualTo("N");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/signs").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    /** 상세에서만 PLAINTEXT·서명값 전체가 보인다. */
    @Test void detailShowsPlaintextAndSignature() throws Exception {
        when(service.get(5L)).thenReturn(sign());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/signs/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/sign/detail"))
            .andExpect(content().string(containsString(PLAINTEXT_MARKER)))
            .andExpect(content().string(containsString(SIGNATURE_MARKER)))
            .andExpect(content().string(containsString("a".repeat(40))))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.SignQueryServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.SignControllerWebTest'`
Expected: FAIL — `SignRepository`, `SignQueryService`, `SignSearchForm`, `SignRow`, `SignController` 없음.

- [ ] **Step 4: 리포지토리·검색 폼·목록 DTO·조회 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/SignRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Sign;

public interface SignRepository extends AdminRepository<Sign, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/SignSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** SIGN 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). del 은 'Y'/'N'/빈값(전체). */
@Getter @Setter
public class SignSearchForm extends SearchForm {
    private String userid;
    private String del;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("del", del);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/SignRow.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Sign;
import java.time.LocalDateTime;

/** SIGN 목록 행. CLOB(PLAINTEXT)·DATA·SIGNATURE 는 싣지 않고 ASSERTION 은 앞 32자만 보여준다. */
public record SignRow(Long idx, Long companyIdx, String userid, String dn, String memo, String del,
                      LocalDateTime createtime, String assertionHead) {

    static final int HEAD = 32;

    public static SignRow of(Sign s) {
        String a = s.getAssertion();
        String head = a == null ? null : a.length() <= HEAD ? a : a.substring(0, HEAD) + "…";
        return new SignRow(s.getIdx(), s.getCompanyIdx(), s.getUserid(), s.getDn(), s.getMemo(), s.getDel(),
            s.getCreatetime(), head);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/SignQueryService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.repository.SignRepository;
import com.crosscert.fidoadmin.fido.web.SignSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** SIGN 조회 전용. */
@Service
public class SignQueryService extends CrudService<Sign, Long, SignSearchForm> {

    public SignQueryService(SignRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Sign> toSpecification(SignSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.eq("del", f.getDel()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Sign e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Sign e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Sign e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "SIGN"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
```

- [ ] **Step 5: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.SignQueryServiceTest'`
Expected: PASS.

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/SignController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.service.SignQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 서명 조회. 목록은 SignRow(CLOB 제외), 상세는 엔티티 전체(PLAINTEXT 포함). */
@Controller
@RequestMapping("/signs")
@RequiredArgsConstructor
public class SignController extends ReadOnlyController<Sign, Long, SignSearchForm> {

    private final SignQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Sign, Long, SignSearchForm> service() { return service; }
    @Override protected String basePath() { return "/signs"; }
    @Override protected String viewDir() { return "fido/sign"; }
    @Override protected Object toListView(Sign entity) { return SignRow.of(entity); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(Sign entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
```

- [ ] **Step 7: 템플릿 작성**

`src/main/resources/templates/fido/sign/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>서명</title></head>
<body>
<main>
  <h1 class="h4 mb-3">서명</h1>
  <form method="get" th:action="@{/signs}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">USERID</label><input name="userid" th:value="${search.userid}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">DEL</label>
      <select name="del" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="Y" th:selected="${search.del == 'Y'}">Y</option>
        <option value="N" th:selected="${search.del == 'N'}">N</option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/signs}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>USERID</th><th>DN</th><th>ASSERTION</th><th>메모</th><th>DEL</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/signs/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.userid}"></td>
          <td th:text="${r.dn}"></td>
          <td class="fa-mono" th:text="${r.assertionHead}"></td>
          <td th:text="${r.memo}"></td>
          <td th:text="${r.del}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/sign/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>서명 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|서명 #${item.idx}|"></h1>
    <a th:href="@{/signs}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>USERID</th><td th:text="${item.userid}"></td></tr>
      <tr><th>DN</th><td th:text="${item.dn}"></td></tr>
      <tr><th>메모</th><td th:text="${item.memo}"></td></tr>
      <tr><th>DEL</th><td th:text="${item.del}"></td></tr>
      <tr><th>생성일시</th><td th:text="${#temporals.format(item.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>PLAINTEXT (원문)</th><td class="fa-pre" th:text="${item.plaintext}"></td></tr>
      <tr><th>ASSERTION</th><td class="fa-pre fa-mono" th:text="${item.assertion}"></td></tr>
      <tr><th>DATA</th><td class="fa-pre fa-mono" th:text="${item.data}"></td></tr>
      <tr><th>SIGNATURE</th><td class="fa-pre fa-mono" th:text="${item.signature}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 8: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.SignControllerWebTest'`
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/main/resources/templates/fido/sign src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 서명 조회 화면

- SIGN 목록(USERID·DEL·기간)은 SignRow 로 CLOB·서명값 제외, ASSERTION 앞 32자
- PLAINTEXT·DATA·SIGNATURE 는 상세에서만 표시

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 10: 조회 화면 — 거래 해시 (TRANSACTIONHASH)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/TransactionhashRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/TransactionhashQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashController.java`
- Create: `src/main/resources/templates/fido/transactionhash/list.html`
- Create: `src/main/resources/templates/fido/transactionhash/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/TransactionhashQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/TransactionhashControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Transactionhash` 엔티티(`idx, companyIdx, userid, content(CLOB), contenthash, createtime`), 기반 클래스, `CompanyLookup`.
- Produces: `TransactionhashRepository extends AdminRepository<Transactionhash, Long>`, `TransactionhashQueryService` (`companyIdxAttribute() = "companyIdx"`, `defaultSort() = createtime DESC, idx DESC`, `sortableProperties() = {"idx", "createtime"}`), `record TransactionhashRow(Long idx, Long companyIdx, String userid, String contenthash, LocalDateTime createtime)` + `static of(Transactionhash)`, `TransactionhashController` (`GET /transaction-hashes`, `GET /transaction-hashes/{id}`, 뷰 `fido/transactionhash/list`, `fido/transactionhash/detail`).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/TransactionhashQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class TransactionhashQueryServiceTest {

    TransactionhashQueryService service =
        new TransactionhashQueryService(mock(TransactionhashRepository.class), mock(AuditLogger.class));

    @Test void defaultSortIsCreatetimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Transactionhash t = new Transactionhash(); t.setIdx(11L);
        assertThat(service.idOf(t)).isEqualTo("11");
    }
}
```

- [ ] **Step 2: 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/TransactionhashControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.service.TransactionhashQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransactionhashController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class TransactionhashControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TransactionhashQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static final String CONTENT_MARKER = "CONTENT-MARKER-XYZ";

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Transactionhash hash() {
        Transactionhash t = new Transactionhash();
        t.setIdx(3L); t.setCompanyIdx(1L); t.setUserid("user001"); t.setContent(CONTENT_MARKER);
        t.setContenthash("a1b2c3"); t.setCreatetime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return t;
    }

    @Test void listHidesContentClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(hash())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/transaction-hashes").param("userid", "user001").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transactionhash/list"))
            .andExpect(content().string(containsString("a1b2c3")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString(CONTENT_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/transaction-hashes/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<TransactionhashSearchForm> captor = ArgumentCaptor.forClass(TransactionhashSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/transaction-hashes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsContent() throws Exception {
        when(service.get(3L)).thenReturn(hash());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/transaction-hashes/3").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transactionhash/detail"))
            .andExpect(content().string(containsString(CONTENT_MARKER)))
            .andExpect(content().string(containsString("a1b2c3")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.TransactionhashQueryServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.TransactionhashControllerWebTest'`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 4: 리포지토리·검색 폼·목록 DTO·조회 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/TransactionhashRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;

public interface TransactionhashRepository extends AdminRepository<Transactionhash, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** TRANSACTIONHASH 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). */
@Getter @Setter
public class TransactionhashSearchForm extends SearchForm {
    private String userid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashRow.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import java.time.LocalDateTime;

/** TRANSACTIONHASH 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
public record TransactionhashRow(Long idx, Long companyIdx, String userid, String contenthash, LocalDateTime createtime) {

    public static TransactionhashRow of(Transactionhash t) {
        return new TransactionhashRow(t.getIdx(), t.getCompanyIdx(), t.getUserid(), t.getContenthash(), t.getCreatetime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/TransactionhashQueryService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository;
import com.crosscert.fidoadmin.fido.web.TransactionhashSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** TRANSACTIONHASH 조회 전용. */
@Service
public class TransactionhashQueryService extends CrudService<Transactionhash, Long, TransactionhashSearchForm> {

    public TransactionhashQueryService(TransactionhashRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Transactionhash> toSpecification(TransactionhashSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Transactionhash e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Transactionhash e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Transactionhash e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "TRANSACTIONHASH"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
```

- [ ] **Step 5: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.TransactionhashQueryServiceTest'`
Expected: PASS.

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionhashController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.service.TransactionhashQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 거래 해시 조회. 목록은 TransactionhashRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-hashes")
@RequiredArgsConstructor
public class TransactionhashController extends ReadOnlyController<Transactionhash, Long, TransactionhashSearchForm> {

    private final TransactionhashQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Transactionhash, Long, TransactionhashSearchForm> service() { return service; }
    @Override protected String basePath() { return "/transaction-hashes"; }
    @Override protected String viewDir() { return "fido/transactionhash"; }
    @Override protected Object toListView(Transactionhash entity) { return TransactionhashRow.of(entity); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(Transactionhash entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
```

- [ ] **Step 7: 템플릿 작성**

`src/main/resources/templates/fido/transactionhash/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>거래 해시</title></head>
<body>
<main>
  <h1 class="h4 mb-3">거래 해시</h1>
  <form method="get" th:action="@{/transaction-hashes}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">USERID</label><input name="userid" th:value="${search.userid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/transaction-hashes}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>USERID</th><th>CONTENTHASH</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/transaction-hashes/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.userid}"></td>
          <td class="fa-mono" th:text="${r.contenthash}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="5" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/transactionhash/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>거래 해시 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|거래 해시 #${item.idx}|"></h1>
    <a th:href="@{/transaction-hashes}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>USERID</th><td th:text="${item.userid}"></td></tr>
      <tr><th>CONTENTHASH</th><td class="fa-mono" th:text="${item.contenthash}"></td></tr>
      <tr><th>생성일시</th><td th:text="${#temporals.format(item.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>CONTENT</th><td class="fa-pre" th:text="${item.content}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 8: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.TransactionhashControllerWebTest'`
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/main/resources/templates/fido/transactionhash src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 거래 해시 조회 화면

- TRANSACTIONHASH 목록(USERID·기간)은 TransactionhashRow 로 CLOB 제외
- CONTENT 는 상세에서만 표시

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 11: 조회 화면 — 거래 확인 (TRANSACTION_CONFIRMATION)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/TransactionConfirmationRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/TransactionConfirmationQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationController.java`
- Create: `src/main/resources/templates/fido/transaction-confirmation/list.html`
- Create: `src/main/resources/templates/fido/transaction-confirmation/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/TransactionConfirmationQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `TransactionConfirmation` 엔티티(`idx, companyIdx, userid, aaid, contenttype, content(CLOB), createdtime`), 기반 클래스, `CompanyLookup`.
- Produces: `TransactionConfirmationRepository extends AdminRepository<TransactionConfirmation, Long>`, `TransactionConfirmationQueryService` (`companyIdxAttribute() = "companyIdx"`, `defaultSort() = createdtime DESC, idx DESC`, `sortableProperties() = {"idx", "createdtime"}`), `record TransactionConfirmationRow(Long idx, Long companyIdx, String userid, String aaid, String contenttype, LocalDateTime createdtime)` + `static of(TransactionConfirmation)`, `TransactionConfirmationController` (`GET /transaction-confirmations`, `GET /transaction-confirmations/{id}`, 뷰 `fido/transaction-confirmation/list`, `fido/transaction-confirmation/detail`).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/TransactionConfirmationQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.repository.TransactionConfirmationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class TransactionConfirmationQueryServiceTest {

    TransactionConfirmationQueryService service =
        new TransactionConfirmationQueryService(mock(TransactionConfirmationRepository.class), mock(AuditLogger.class));

    /** 이 테이블의 시각 컬럼은 CREATEDTIME(다른 FIDO 테이블은 CREATETIME). 이름을 틀리면 조회 시 500. */
    @Test void defaultSortIsCreatedtimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime");
    }

    @Test void idOfIsIdx() {
        TransactionConfirmation t = new TransactionConfirmation(); t.setIdx(8L);
        assertThat(service.idOf(t)).isEqualTo("8");
    }
}
```

- [ ] **Step 2: 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransactionConfirmationController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class TransactionConfirmationControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TransactionConfirmationQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static final String CONTENT_MARKER = "TC-CONTENT-MARKER-XYZ";

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private TransactionConfirmation tc() {
        TransactionConfirmation t = new TransactionConfirmation();
        t.setIdx(4L); t.setCompanyIdx(1L); t.setUserid("user001"); t.setAaid("0012#0001");
        t.setContenttype("text/plain"); t.setContent(CONTENT_MARKER); t.setCreatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return t;
    }

    @Test void listHidesContentClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(tc())));
        when(companies.names()).thenReturn(Map.of(1L, "KB국민은행"));
        mvc.perform(get("/transaction-confirmations").param("userid", "user001").param("aaid", "0012").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("text/plain")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString(CONTENT_MARKER))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/transaction-confirmations/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<TransactionConfirmationSearchForm> captor = ArgumentCaptor.forClass(TransactionConfirmationSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserid()).isEqualTo("user001");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAaid()).isEqualTo("0012");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/transaction-confirmations").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsContent() throws Exception {
        when(service.get(4L)).thenReturn(tc());
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/transaction-confirmations/4").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/transaction-confirmation/detail"))
            .andExpect(content().string(containsString(CONTENT_MARKER)))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.TransactionConfirmationControllerWebTest'`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 4: 리포지토리·검색 폼·목록 DTO·조회 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/TransactionConfirmationRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;

public interface TransactionConfirmationRepository extends AdminRepository<TransactionConfirmation, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** TRANSACTION_CONFIRMATION 검색 조건. 기간은 기반의 fromDate/toDate(CREATEDTIME 기준). */
@Getter @Setter
public class TransactionConfirmationSearchForm extends SearchForm {
    private String userid;
    private String aaid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("aaid", aaid);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationRow.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import java.time.LocalDateTime;

/** TRANSACTION_CONFIRMATION 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
public record TransactionConfirmationRow(Long idx, Long companyIdx, String userid, String aaid, String contenttype,
                                         LocalDateTime createdtime) {

    public static TransactionConfirmationRow of(TransactionConfirmation t) {
        return new TransactionConfirmationRow(t.getIdx(), t.getCompanyIdx(), t.getUserid(), t.getAaid(),
            t.getContenttype(), t.getCreatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/TransactionConfirmationQueryService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.repository.TransactionConfirmationRepository;
import com.crosscert.fidoadmin.fido.web.TransactionConfirmationSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** TRANSACTION_CONFIRMATION 조회 전용. 시각 컬럼은 CREATEDTIME. */
@Service
public class TransactionConfirmationQueryService
        extends CrudService<TransactionConfirmation, Long, TransactionConfirmationSearchForm> {

    public TransactionConfirmationQueryService(TransactionConfirmationRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<TransactionConfirmation> toSpecification(TransactionConfirmationSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("aaid", f.getAaid()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(TransactionConfirmation e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(TransactionConfirmation e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(TransactionConfirmation e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "TRANSACTION_CONFIRMATION"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createdtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime"); }
}
```

- [ ] **Step 5: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryServiceTest'`
Expected: PASS.

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/TransactionConfirmationController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 거래 확인(TC) 조회. 목록은 TransactionConfirmationRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-confirmations")
@RequiredArgsConstructor
public class TransactionConfirmationController
        extends ReadOnlyController<TransactionConfirmation, Long, TransactionConfirmationSearchForm> {

    private final TransactionConfirmationQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<TransactionConfirmation, Long, TransactionConfirmationSearchForm> service() { return service; }
    @Override protected String basePath() { return "/transaction-confirmations"; }
    @Override protected String viewDir() { return "fido/transaction-confirmation"; }
    @Override protected Object toListView(TransactionConfirmation entity) { return TransactionConfirmationRow.of(entity); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(TransactionConfirmation entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
```

- [ ] **Step 7: 템플릿 작성**

`src/main/resources/templates/fido/transaction-confirmation/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>거래 확인</title></head>
<body>
<main>
  <h1 class="h4 mb-3">거래 확인</h1>
  <form method="get" th:action="@{/transaction-confirmations}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">USERID</label><input name="userid" th:value="${search.userid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">AAID</label><input name="aaid" th:value="${search.aaid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/transaction-confirmations}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>USERID</th><th>AAID</th><th>CONTENTTYPE</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/transaction-confirmations/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.userid}"></td>
          <td th:text="${r.aaid}"></td>
          <td th:text="${r.contenttype}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="6" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/transaction-confirmation/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>거래 확인 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|거래 확인 #${item.idx}|"></h1>
    <a th:href="@{/transaction-confirmations}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>USERID</th><td th:text="${item.userid}"></td></tr>
      <tr><th>AAID</th><td th:text="${item.aaid}"></td></tr>
      <tr><th>CONTENTTYPE</th><td th:text="${item.contenttype}"></td></tr>
      <tr><th>생성일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>CONTENT</th><td class="fa-pre" th:text="${item.content}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 8: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.TransactionConfirmationControllerWebTest'`
Expected: PASS.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/main/resources/templates/fido/transaction-confirmation src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 거래 확인 조회 화면

- TRANSACTION_CONFIRMATION 목록(USERID·AAID·기간)은 Row DTO 로 CLOB 제외
- CONTENT 는 상세에서만 표시, 시각 컬럼은 CREATEDTIME

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 12: 조회 화면 — 인증기기 기준 (CRITERIA, SUPER 전용)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido/repository/CriteriaRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaView.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaController.java`
- Create: `src/main/resources/templates/fido/criteria/list.html`
- Create: `src/main/resources/templates/fido/criteria/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/web/CriteriaControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `Criteria` 엔티티(`idx, aaid, vendorids, userverification, keyprotection, matcherprotection, attachmenthnumber, tcdisplay, tcdisplaycontenttype, authenticationalgorithms, assertionschemes, attestationtypes, authenticatorversion, metahash, jsondata(CLOB), createtime, updatedtime` — 17개, COMPANY_IDX 없음), 기반 클래스, Task 1 의 `JsonPretty.pretty(String)`, Task 1 에서 바뀐 `SecurityConfig`(`/criteria/**` → SUPER)와 `MenuRegistry`(`/criteria` superOnly).
- Produces: `CriteriaRepository extends AdminRepository<Criteria, Long>`, `CriteriaQueryService` (`companyIdxAttribute() = null` → 기반이 SUPER 전용으로 막음, `defaultSort() = idx DESC`, `sortableProperties() = {"idx", "aaid", "updatedtime"}`), `record CriteriaRow(Long idx, String aaid, String vendorids, Long userverification, Long keyprotection, Long matcherprotection, Long authenticatorversion, LocalDateTime updatedtime)` + `static of(Criteria)`, `record CriteriaView(...17개 필드, jsondata 대신 String jsondataPretty)` + `static of(Criteria)`, `CriteriaController` (`GET /criteria`, `GET /criteria/{id}`, 뷰 `fido/criteria/list`, `fido/criteria/detail`). 이 화면은 고객사 select·`companyNames` 가 없다.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CriteriaQueryServiceTest {

    CriteriaRepository repo = mock(CriteriaRepository.class);
    CriteriaQueryService service = new CriteriaQueryService(repo, mock(AuditLogger.class));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** COMPANY_IDX 가 없는 테이블이라 SUPER 전용(설계 3.3). URL 매처가 빠져도 서비스가 막는다. */
    @Test void companyRoleIsDeniedOnSearchAndGet() {
        login(1L);
        assertThatThrownBy(() -> service.search(new CriteriaSearchForm(), PageRequest.of(0, 20, service.defaultSort())))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).findAll(any(Specification.class), any(PageRequest.class));
        verify(repo, never()).findById(any());
    }

    @Test void superRoleCanSearchAndGet() {
        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        Criteria c = new Criteria(); c.setIdx(1L); c.setAaid("0012#0001");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        CriteriaSearchForm f = new CriteriaSearchForm(); f.setAaid("0012");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        assertThat(service.get(1L).getAaid()).isEqualTo("0012#0001");
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test void defaultSortIsIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void sortablePropertiesIncludeAaidAndUpdatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "aaid", "updatedtime");
    }
}
```

- [ ] **Step 2: 웹 실패 테스트 작성**

`th:text` 는 큰따옴표를 `&quot;` 로 이스케이프하므로 정리된 JSON 은 그 형태로 확인한다.

`src/test/java/com/crosscert/fidoadmin/fido/web/CriteriaControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CriteriaController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class CriteriaControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean CriteriaQueryService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Criteria criteria() {
        Criteria c = new Criteria();
        c.setIdx(1L); c.setAaid("0012#0001"); c.setVendorids("0012"); c.setUserverification(2L); c.setKeyprotection(2L);
        c.setMatcherprotection(2L); c.setAttachmenthnumber(1L); c.setTcdisplay(1L); c.setTcdisplaycontenttype("text/plain");
        c.setAuthenticationalgorithms("1"); c.setAssertionschemes("UAFV1TLV"); c.setAttestationtypes("15879");
        c.setAuthenticatorversion(1L); c.setMetahash("hash-0001");
        c.setJsondata("{\"aaid\":\"0012#0001\",\"description\":\"Sample fingerprint\"}");
        c.setCreatetime(LocalDateTime.of(2026, 9, 1, 9, 0)); c.setUpdatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return c;
    }

    /** COMPANY_IDX 가 없는 테이블의 화면은 SUPER 전용. URL 매처가 403 으로 막는다. */
    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/criteria").with(user(companyUser))).andExpect(status().isForbidden());
        mvc.perform(get("/criteria/1").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superSeesListWithoutJsonAndWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria())));
        mvc.perform(get("/criteria").param("aaid", "0012").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("hash-0001")))
            .andExpect(content().string(not(containsString("Sample fingerprint"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/criteria/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
        ArgumentCaptor<CriteriaSearchForm> captor = ArgumentCaptor.forClass(CriteriaSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getAaid()).isEqualTo("0012");
    }

    /** 상세의 JSONDATA 는 JsonPretty 로 정리되어 "key" : "value" 형태(이스케이프된 &quot;)로 나온다. */
    @Test void detailShowsPrettyJson() throws Exception {
        when(service.get(1L)).thenReturn(criteria());
        mvc.perform(get("/criteria/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")))
            .andExpect(content().string(containsString("UAFV1TLV")))
            .andExpect(content().string(not(containsString("data-confirm-form"))));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.CriteriaQueryServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.CriteriaControllerWebTest'`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 4: 리포지토리·검색 폼·DTO·조회 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido/repository/CriteriaRepository.java`

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Criteria;

public interface CriteriaRepository extends AdminRepository<Criteria, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaSearchForm.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CRITERIA 검색 조건. COMPANY_IDX 가 없는 테이블이라 기반의 companyIdx 는 쓰이지 않는다. */
@Getter @Setter
public class CriteriaSearchForm extends SearchForm {
    private String aaid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aaid", aaid);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaRow.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.time.LocalDateTime;

/** CRITERIA 목록 행. CLOB(JSONDATA) 은 싣지 않는다. */
public record CriteriaRow(Long idx, String aaid, String vendorids, Long userverification, Long keyprotection,
                          Long matcherprotection, Long authenticatorversion, String metahash, LocalDateTime updatedtime) {

    public static CriteriaRow of(Criteria c) {
        return new CriteriaRow(c.getIdx(), c.getAaid(), c.getVendorids(), c.getUserverification(), c.getKeyprotection(),
            c.getMatcherprotection(), c.getAuthenticatorversion(), c.getMetahash(), c.getUpdatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaView.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.time.LocalDateTime;

/** CRITERIA 상세. ERD 17개 컬럼을 모두 담되 JSONDATA 는 정리된 문자열(jsondataPretty)로 준다. */
public record CriteriaView(Long idx, String aaid, String vendorids, Long userverification, Long keyprotection,
                           Long matcherprotection, Long attachmenthnumber, Long tcdisplay, String tcdisplaycontenttype,
                           String authenticationalgorithms, String assertionschemes, String attestationtypes,
                           Long authenticatorversion, String metahash, String jsondataPretty,
                           LocalDateTime createtime, LocalDateTime updatedtime) {

    public static CriteriaView of(Criteria c) {
        return new CriteriaView(c.getIdx(), c.getAaid(), c.getVendorids(), c.getUserverification(), c.getKeyprotection(),
            c.getMatcherprotection(), c.getAttachmenthnumber(), c.getTcdisplay(), c.getTcdisplaycontenttype(),
            c.getAuthenticationalgorithms(), c.getAssertionschemes(), c.getAttestationtypes(),
            c.getAuthenticatorversion(), c.getMetahash(), JsonPretty.pretty(c.getJsondata()),
            c.getCreatetime(), c.getUpdatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryService.java`

```java
package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CRITERIA 조회 전용. COMPANY_IDX 가 없어 companyIdxAttribute() 는 null 이고,
 * 기반의 requireSuperForGlobalTable() 이 COMPANY 역할을 막는다(설계 3.3).
 */
@Service
public class CriteriaQueryService extends CrudService<Criteria, Long, CriteriaSearchForm> {

    public CriteriaQueryService(CriteriaRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Criteria> toSpecification(CriteriaSearchForm f) {
        return Specs.all(Specs.like("aaid", f.getAaid()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Criteria e) { return null; }
    @Override protected void setCompanyIdx(Criteria e, Long c) {}
    @Override public String idOf(Criteria e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CRITERIA"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "aaid", "updatedtime"); }
}
```

- [ ] **Step 5: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.CriteriaQueryServiceTest'`
Expected: PASS.

- [ ] **Step 6: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido/web/CriteriaController.java`

```java
package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** 인증기기 기준(UAF CRITERIA) 조회. SUPER 전용(SecurityConfig /criteria/**, 서비스 이중 차단). */
@Controller
@RequestMapping("/criteria")
@RequiredArgsConstructor
public class CriteriaController extends ReadOnlyController<Criteria, Long, CriteriaSearchForm> {

    private final CriteriaQueryService service;

    @Override protected CrudService<Criteria, Long, CriteriaSearchForm> service() { return service; }
    @Override protected String basePath() { return "/criteria"; }
    @Override protected String viewDir() { return "fido/criteria"; }
    @Override protected Object toListView(Criteria entity) { return CriteriaRow.of(entity); }
    @Override protected Object toDetailView(Criteria entity) { return CriteriaView.of(entity); }
}
```

- [ ] **Step 7: 템플릿 작성**

`src/main/resources/templates/fido/criteria/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>인증기기 기준</title></head>
<body>
<main>
  <h1 class="h4 mb-3">인증기기 기준</h1>
  <form method="get" th:action="@{/criteria}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">AAID</label><input name="aaid" th:value="${search.aaid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/criteria}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>AAID</th><th>벤더</th><th>UV</th><th>키보호</th><th>매처보호</th><th>버전</th><th>METAHASH</th><th>수정일시</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/criteria/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${r.aaid}"></td>
          <td th:text="${r.vendorids}"></td>
          <td th:text="${r.userverification}"></td>
          <td th:text="${r.keyprotection}"></td>
          <td th:text="${r.matcherprotection}"></td>
          <td th:text="${r.authenticatorversion}"></td>
          <td class="fa-mono" th:text="${#strings.abbreviate(r.metahash, 24)}"></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="9" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido/criteria/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>인증기기 기준 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|인증기기 기준 #${item.idx}|"></h1>
    <a th:href="@{/criteria}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>AAID</th><td th:text="${item.aaid}"></td></tr>
      <tr><th>VENDORIDS</th><td th:text="${item.vendorids}"></td></tr>
      <tr><th>USERVERIFICATION</th><td th:text="${item.userverification}"></td></tr>
      <tr><th>KEYPROTECTION</th><td th:text="${item.keyprotection}"></td></tr>
      <tr><th>MATCHERPROTECTION</th><td th:text="${item.matcherprotection}"></td></tr>
      <tr><th>ATTACHMENTHNUMBER</th><td th:text="${item.attachmenthnumber}"></td></tr>
      <tr><th>TCDISPLAY</th><td th:text="${item.tcdisplay}"></td></tr>
      <tr><th>TCDISPLAYCONTENTTYPE</th><td th:text="${item.tcdisplaycontenttype}"></td></tr>
      <tr><th>AUTHENTICATIONALGORITHMS</th><td th:text="${item.authenticationalgorithms}"></td></tr>
      <tr><th>ASSERTIONSCHEMES</th><td th:text="${item.assertionschemes}"></td></tr>
      <tr><th>ATTESTATIONTYPES</th><td th:text="${item.attestationtypes}"></td></tr>
      <tr><th>AUTHENTICATORVERSION</th><td th:text="${item.authenticatorversion}"></td></tr>
      <tr><th>METAHASH</th><td class="fa-mono" th:text="${item.metahash}"></td></tr>
      <tr><th>JSONDATA</th><td class="fa-pre fa-mono" th:text="${item.jsondataPretty}"></td></tr>
      <tr><th>생성일시</th><td th:text="${#temporals.format(item.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 8: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.web.CriteriaControllerWebTest' --tests 'com.crosscert.fidoadmin.common.LayoutWebTest'`
Expected: PASS (COMPANY 403 은 Task 1 의 `SecurityConfig` 변경으로 필터 단계에서 처리된다).

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/main/resources/templates/fido/criteria src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 인증기기 기준(CRITERIA) 조회 화면

- COMPANY_IDX 없는 테이블이라 SUPER 전용(설계 3.3), 서비스·URL 이중 차단
- 목록은 CriteriaRow 로 JSONDATA 제외, 상세는 JsonPretty 로 정리 출력

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 13: FIDO2 메타데이터 (FIDO2_METADATA) CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2MetadataRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2MetadataService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataRow.java`
- Create: `src/main/resources/templates/fido2/metadata/list.html`
- Create: `src/main/resources/templates/fido2/metadata/detail.html`
- Create: `src/main/resources/templates/fido2/metadata/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2MetadataServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `CrudService<E, ID, S>`, `CrudController<E, ID, F, S>`, `AdminRepository<E, ID>`, `Specs`, `ByteSize`, `AuditLogger`, 엔티티 `Fido2Metadata`(23 컬럼, CLOB `attestationrootcertificates`·`icon`, 컬럼명 오타 `assertionschme` 유지). Task 1 의 SUPER 전용 보정(`/fido2/**` → `hasRole("SUPER")`).
- Produces:
  - `Fido2MetadataRepository extends AdminRepository<Fido2Metadata, Long>`
  - `Fido2MetadataService extends CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm>` — 생성자 `(Fido2MetadataRepository, AuditLogger)`, `companyIdxAttribute()` 는 `null`(SUPER 전용), `sortableProperties()` = `{"idx","description","aaguid","protocolfamily","createdtime"}`, `touchCreated` 는 `createdtime` 만 채운다(UPDATEDTIME 컬럼 없음).
  - `Fido2MetadataRow(Long idx, String description, String aaguid, String protocolfamily, Long authenticatorversion, String assertionschme, String issecondfactoronly, LocalDateTime createdtime)` — 목록용 record, CLOB 제외.
  - 경로 `/fido2/metadata`, 뷰 디렉터리 `fido2/metadata`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2MetadataServiceTest.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2MetadataServiceTest {

    Fido2MetadataRepository repo = mock(Fido2MetadataRepository.class);
    Fido2MetadataService service = new Fido2MetadataService(repo, mock(AuditLogger.class));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용(설계 3.3). 서비스 계층에서도 막힌다. */
    @Test void companyRoleCannotSearch() {
        login(1L);
        assertThatThrownBy(() -> service.search(new Fido2MetadataSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test void createFillsCreatedtime() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2Metadata m = new Fido2Metadata();
        m.setDescription("YubiKey"); m.setAaguid("cb69481e-8ff7-4039-93ec-0a2729a154a8");
        Fido2Metadata saved = service.create(m);
        assertThat(saved.getCreatedtime()).isNotNull();
    }

    @Test void sortableIncludesListColumnsOnly() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "description", "aaguid", "protocolfamily", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2MetadataServiceTest'`
Expected: FAIL — `Fido2MetadataRepository`, `Fido2MetadataService`, `Fido2MetadataSearchForm` 없음(컴파일 오류).

- [ ] **Step 3: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2MetadataRepository.java`

```java
package com.crosscert.fidoadmin.fido2.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;

public interface Fido2MetadataRepository extends AdminRepository<Fido2Metadata, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataSearchForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2MetadataSearchForm extends SearchForm {
    private String aaguid;
    private String description;
    private String protocolfamily;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aaguid", aaguid); m.put("description", description); m.put("protocolfamily", protocolfamily);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2MetadataService.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO2_METADATA. COMPANY_IDX 가 없어 SUPER 전용(설계 3.3). UPDATEDTIME 컬럼이 없다. */
@Service
public class Fido2MetadataService extends CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm> {

    public Fido2MetadataService(Fido2MetadataRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Fido2Metadata> toSpecification(Fido2MetadataSearchForm f) {
        return Specs.all(
            Specs.like("aaguid", f.getAaguid()),
            Specs.like("description", f.getDescription()),
            Specs.like("protocolfamily", f.getProtocolfamily()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2Metadata e) { return null; }
    @Override protected void setCompanyIdx(Fido2Metadata e, Long c) {}
    @Override public String idOf(Fido2Metadata e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO2_METADATA"; }
    @Override public Set<String> sortableProperties() {
        return Set.of("idx", "description", "aaguid", "protocolfamily", "createdtime");
    }
    @Override protected void touchCreated(Fido2Metadata e, LocalDateTime now) { e.setCreatedtime(now); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2MetadataServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2MetadataController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class Fido2MetadataControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2MetadataService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Fido2Metadata sample() {
        Fido2Metadata m = new Fido2Metadata();
        m.setIdx(1L); m.setDescription("YubiKey 5 Series"); m.setAaguid("cb69481e-8ff7-4039-93ec-0a2729a154a8");
        m.setProtocolfamily("fido2"); m.setIcon("ICON-MARKER-SHOULD-NOT-RENDER");
        m.setAttestationrootcertificates("ROOTCERT-MARKER-SHOULD-NOT-RENDER");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/metadata").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 CLOB(ICON, ATTESTATIONROOTCERTIFICATES)을 싣지 않는다. */
    @Test void superListRendersRowsWithoutClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(sample())));
        mvc.perform(get("/fido2/metadata").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/list"))
            .andExpect(content().string(containsString("YubiKey 5 Series")))
            .andExpect(content().string(not(containsString("ICON-MARKER-SHOULD-NOT-RENDER"))))
            .andExpect(content().string(not(containsString("ROOTCERT-MARKER-SHOULD-NOT-RENDER"))));
    }

    @Test void detailShowsClob() throws Exception {
        when(service.get(1L)).thenReturn(sample());
        mvc.perform(get("/fido2/metadata/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/detail"))
            .andExpect(content().string(containsString("ICON-MARKER-SHOULD-NOT-RENDER")));
    }

    @Test void blankAaguidShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "설명").param("aaguid", "").param("issecondfactoronly", "false"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/metadata/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Fido2Metadata saved = new Fido2Metadata(); saved.setIdx(3L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("3");
        mvc.perform(post("/fido2/metadata").with(user(superUser)).with(csrf())
                .param("description", "Samsung Pass").param("aaguid", "53414d53-554e-4700-0000-000000000000")
                .param("issecondfactoronly", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/metadata/3"));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2MetadataControllerWebTest'`
Expected: FAIL — `Fido2MetadataController` 없음.

- [ ] **Step 7: 폼·목록 DTO·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** FIDO2_METADATA 입력 폼. 길이는 ERD 값(바이트). CLOB 두 개는 길이 제한 없음. */
@Getter @Setter
public class Fido2MetadataForm {
    @NotBlank @ByteSize(max = 256) private String description;
    @NotBlank @ByteSize(max = 256) private String aaguid;
    @ByteSize(max = 2048) private String alternativedescriptions;
    @ByteSize(max = 128) private String protocolfamily;
    private Long authenticatorversion;
    @ByteSize(max = 512) private String upv;
    @ByteSize(max = 128) private String assertionschme;
    private Long authenticationalgorithm;
    private Long publickeyalgandencoding;
    @ByteSize(max = 2048) private String attestationtypes;
    @ByteSize(max = 2048) private String userverificationdetails;
    private Long keyprotection;
    private Long matcherprotection;
    private Long cryptostrength;
    @ByteSize(max = 2048) private String operatingenv;
    private Long attachmenthint;
    @ByteSize(max = 8) private String issecondfactoronly = "false";
    private Long tcdisplay;
    private String attestationrootcertificates;
    private String icon;
    @ByteSize(max = 2048) private String ski;

    public static Fido2MetadataForm from(Fido2Metadata m) {
        Fido2MetadataForm f = new Fido2MetadataForm();
        f.description = m.getDescription(); f.aaguid = m.getAaguid();
        f.alternativedescriptions = m.getAlternativedescriptions(); f.protocolfamily = m.getProtocolfamily();
        f.authenticatorversion = m.getAuthenticatorversion(); f.upv = m.getUpv(); f.assertionschme = m.getAssertionschme();
        f.authenticationalgorithm = m.getAuthenticationalgorithm(); f.publickeyalgandencoding = m.getPublickeyalgandencoding();
        f.attestationtypes = m.getAttestationtypes(); f.userverificationdetails = m.getUserverificationdetails();
        f.keyprotection = m.getKeyprotection(); f.matcherprotection = m.getMatcherprotection();
        f.cryptostrength = m.getCryptostrength(); f.operatingenv = m.getOperatingenv();
        f.attachmenthint = m.getAttachmenthint(); f.issecondfactoronly = m.getIssecondfactoronly();
        f.tcdisplay = m.getTcdisplay(); f.attestationrootcertificates = m.getAttestationrootcertificates();
        f.icon = m.getIcon(); f.ski = m.getSki();
        return f;
    }

    public void applyTo(Fido2Metadata m) {
        m.setDescription(description); m.setAaguid(aaguid);
        m.setAlternativedescriptions(alternativedescriptions); m.setProtocolfamily(protocolfamily);
        m.setAuthenticatorversion(authenticatorversion); m.setUpv(upv); m.setAssertionschme(assertionschme);
        m.setAuthenticationalgorithm(authenticationalgorithm); m.setPublickeyalgandencoding(publickeyalgandencoding);
        m.setAttestationtypes(attestationtypes); m.setUserverificationdetails(userverificationdetails);
        m.setKeyprotection(keyprotection); m.setMatcherprotection(matcherprotection);
        m.setCryptostrength(cryptostrength); m.setOperatingenv(operatingenv);
        m.setAttachmenthint(attachmenthint); m.setIssecondfactoronly(issecondfactoronly);
        m.setTcdisplay(tcdisplay); m.setAttestationrootcertificates(attestationrootcertificates);
        m.setIcon(icon); m.setSki(ski);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataRow.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import java.time.LocalDateTime;

/** 목록 행. CLOB(ICON, ATTESTATIONROOTCERTIFICATES)은 제외한다. */
public record Fido2MetadataRow(Long idx, String description, String aaguid, String protocolfamily,
                               Long authenticatorversion, String assertionschme, String issecondfactoronly,
                               LocalDateTime createdtime) {
    public static Fido2MetadataRow of(Fido2Metadata m) {
        return new Fido2MetadataRow(m.getIdx(), m.getDescription(), m.getAaguid(), m.getProtocolfamily(),
            m.getAuthenticatorversion(), m.getAssertionschme(), m.getIssecondfactoronly(), m.getCreatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2MetadataController.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/metadata")
@RequiredArgsConstructor
public class Fido2MetadataController extends CrudController<Fido2Metadata, Long, Fido2MetadataForm, Fido2MetadataSearchForm> {

    private final Fido2MetadataService service;

    @Override protected CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/metadata"; }
    @Override protected String viewDir() { return "fido2/metadata"; }
    @Override protected Fido2MetadataSearchForm newSearchForm() { return new Fido2MetadataSearchForm(); }
    @Override protected Fido2MetadataForm newForm() { return new Fido2MetadataForm(); }
    @Override protected Fido2MetadataForm toForm(Fido2Metadata e) { return Fido2MetadataForm.from(e); }
    @Override protected Fido2Metadata toEntity(Fido2MetadataForm f) { Fido2Metadata m = new Fido2Metadata(); f.applyTo(m); return m; }
    @Override protected void applyForm(Fido2MetadataForm f, Fido2Metadata e) { f.applyTo(e); }
    /** 목록은 CLOB 제외. 상세는 전체 컬럼(민감 컬럼 없음)이라 엔티티 그대로. */
    @Override protected Object toListView(Fido2Metadata e) { return Fido2MetadataRow.of(e); }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/fido2/metadata/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 메타데이터</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">FIDO2 메타데이터</h1>
    <a th:href="@{/fido2/metadata/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/fido2/metadata}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">AAGUID</label><input name="aaguid" th:value="${search.aaguid}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">설명</label><input name="description" th:value="${search.description}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">프로토콜</label><input name="protocolfamily" th:value="${search.protocolfamily}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/fido2/metadata}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>설명</th><th>AAGUID</th><th>프로토콜</th><th>버전</th><th>어서션</th><th>2FA 전용</th><th>등록일</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/fido2/metadata/{id}(id=${r.idx})}" th:text="${r.description}">설명</a></td>
          <td class="fa-mono" th:text="${r.aaguid}"></td>
          <td th:text="${r.protocolfamily}"></td>
          <td th:text="${r.authenticatorversion}"></td>
          <td th:text="${r.assertionschme}"></td>
          <td th:text="${r.issecondfactoronly}"></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/metadata/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 메타데이터 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|FIDO2 메타데이터 #${item.idx}|">메타데이터</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/fido2/metadata/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/fido2/metadata/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/fido2/metadata}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>설명</th><td th:text="${item.description}"></td></tr>
        <tr><th>AAGUID</th><td class="fa-mono" th:text="${item.aaguid}"></td></tr>
        <tr><th>대체 설명</th><td class="fa-pre" th:text="${item.alternativedescriptions}"></td></tr>
        <tr><th>프로토콜</th><td th:text="${item.protocolfamily}"></td></tr>
        <tr><th>인증기기 버전</th><td th:text="${item.authenticatorversion}"></td></tr>
        <tr><th>UPV</th><td class="fa-pre" th:text="${item.upv}"></td></tr>
        <tr><th>어서션 스킴(ASSERTIONSCHME)</th><td th:text="${item.assertionschme}"></td></tr>
        <tr><th>인증 알고리즘</th><td th:text="${item.authenticationalgorithm}"></td></tr>
        <tr><th>공개키 알고리즘/인코딩</th><td th:text="${item.publickeyalgandencoding}"></td></tr>
        <tr><th>어테스테이션 유형</th><td class="fa-pre" th:text="${item.attestationtypes}"></td></tr>
        <tr><th>사용자 검증 상세</th><td class="fa-pre" th:text="${item.userverificationdetails}"></td></tr>
        <tr><th>키 보호</th><td th:text="${item.keyprotection}"></td></tr>
        <tr><th>매처 보호</th><td th:text="${item.matcherprotection}"></td></tr>
        <tr><th>암호 강도</th><td th:text="${item.cryptostrength}"></td></tr>
        <tr><th>운영 환경</th><td class="fa-pre" th:text="${item.operatingenv}"></td></tr>
        <tr><th>부착 힌트</th><td th:text="${item.attachmenthint}"></td></tr>
        <tr><th>2FA 전용</th><td th:text="${item.issecondfactoronly}"></td></tr>
        <tr><th>TC 표시</th><td th:text="${item.tcdisplay}"></td></tr>
        <tr><th>루트 인증서 (CLOB)</th><td class="fa-pre fa-mono" th:text="${item.attestationrootcertificates}"></td></tr>
        <tr><th>아이콘 (CLOB)</th><td class="fa-pre fa-mono" th:text="${item.icon}"></td></tr>
        <tr><th>SKI</th><td class="fa-pre fa-mono" th:text="${item.ski}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/metadata/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? 'FIDO2 메타데이터 등록' : 'FIDO2 메타데이터 수정'">메타데이터</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? 'FIDO2 메타데이터 등록' : 'FIDO2 메타데이터 수정'">메타데이터</h1>
  <form th:action="${isNew} ? @{/fido2/metadata} : @{/fido2/metadata/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 960px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">설명 <span class="text-danger">*</span></label>
        <input th:field="*{description}" class="form-control" th:classappend="${#fields.hasErrors('description')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{description}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">AAGUID <span class="text-danger">*</span></label>
        <input th:field="*{aaguid}" class="form-control fa-mono" th:classappend="${#fields.hasErrors('aaguid')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{aaguid}"></div>
      </div>
      <div class="col-12"><label class="form-label">대체 설명</label><input th:field="*{alternativedescriptions}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">프로토콜</label><input th:field="*{protocolfamily}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">인증기기 버전</label><input type="number" th:field="*{authenticatorversion}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">어서션 스킴</label><input th:field="*{assertionschme}" class="form-control"></div>
      <div class="col-md-3">
        <label class="form-label">2FA 전용</label>
        <select th:field="*{issecondfactoronly}" class="form-select"><option value="false">false</option><option value="true">true</option></select>
      </div>
      <div class="col-12"><label class="form-label">UPV</label><input th:field="*{upv}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">인증 알고리즘</label><input type="number" th:field="*{authenticationalgorithm}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">공개키 알고리즘/인코딩</label><input type="number" th:field="*{publickeyalgandencoding}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">키 보호</label><input type="number" th:field="*{keyprotection}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">매처 보호</label><input type="number" th:field="*{matcherprotection}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">암호 강도</label><input type="number" th:field="*{cryptostrength}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">부착 힌트</label><input type="number" th:field="*{attachmenthint}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">TC 표시</label><input type="number" th:field="*{tcdisplay}" class="form-control"></div>
      <div class="col-12"><label class="form-label">어테스테이션 유형</label><input th:field="*{attestationtypes}" class="form-control"></div>
      <div class="col-12"><label class="form-label">사용자 검증 상세</label><textarea th:field="*{userverificationdetails}" rows="2" class="form-control"></textarea></div>
      <div class="col-12"><label class="form-label">운영 환경</label><textarea th:field="*{operatingenv}" rows="2" class="form-control"></textarea></div>
      <div class="col-12"><label class="form-label">루트 인증서 (CLOB)</label><textarea th:field="*{attestationrootcertificates}" rows="6" class="form-control fa-mono"></textarea></div>
      <div class="col-12"><label class="form-label">아이콘 (CLOB, data URI)</label><textarea th:field="*{icon}" rows="4" class="form-control fa-mono"></textarea></div>
      <div class="col-12"><label class="form-label">SKI</label><textarea th:field="*{ski}" rows="2" class="form-control fa-mono"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/fido2/metadata} : @{/fido2/metadata/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2MetadataControllerWebTest'`
Expected: PASS (5개).

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido2 src/main/resources/templates/fido2/metadata src/test/java/com/crosscert/fidoadmin/fido2
git commit -m "feat: FIDO2 메타데이터 CRUD 화면 (SUPER 전용, 목록 CLOB 제외)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 14: FIDO2 크리덴셜 파라미터 (FIDO2_CREDENTIAL_PARAMS) CRUD

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2CredentialParamsRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2CredentialParamsService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsSearchForm.java`
- Create: `src/main/resources/templates/fido2/credential-params/list.html`
- Create: `src/main/resources/templates/fido2/credential-params/detail.html`
- Create: `src/main/resources/templates/fido2/credential-params/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2CredentialParamsServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `CrudService`, `CrudController`, `AdminRepository`, `Specs`, `ByteSize`, 엔티티 `Fido2CredentialParams`(idx, credType, credAlg, status, createdtime). DDL 기본값 `CRED_TYPE='public-key'`, `STATUS='T'`.
- Produces:
  - `Fido2CredentialParamsRepository extends AdminRepository<Fido2CredentialParams, Long>`
  - `Fido2CredentialParamsService extends CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm>` — 생성자 `(Fido2CredentialParamsRepository, AuditLogger)`, `companyIdxAttribute()` 는 `null`, `sortableProperties()` = `{"idx","credType","credAlg","createdtime"}`, `applyDefaults` 는 credType `"public-key"`, status `"T"`.
  - 경로 `/fido2/credential-params`, 뷰 디렉터리 `fido2/credential-params`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2CredentialParamsServiceTest.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2CredentialParamsServiceTest {

    Fido2CredentialParamsRepository repo = mock(Fido2CredentialParamsRepository.class);
    Fido2CredentialParamsService service = new Fido2CredentialParamsService(repo, mock(AuditLogger.class));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void defaultsFilledOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setCredAlg(-7L);
        Fido2CredentialParams saved = service.create(p);
        assertThat(saved.getCredType()).isEqualTo("public-key");
        assertThat(saved.getStatus()).isEqualTo("T");
        assertThat(saved.getCreatedtime()).isNotNull();
    }

    @Test void explicitValuesAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setCredType("custom"); p.setCredAlg(-257L); p.setStatus("F");
        Fido2CredentialParams saved = service.create(p);
        assertThat(saved.getCredType()).isEqualTo("custom");
        assertThat(saved.getStatus()).isEqualTo("F");
    }

    @Test void companyRoleCannotSearch() {
        login(1L);
        assertThatThrownBy(() -> service.search(new Fido2CredentialParamsSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsServiceTest'`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 3: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2CredentialParamsRepository.java`

```java
package com.crosscert.fidoadmin.fido2.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;

public interface Fido2CredentialParamsRepository extends AdminRepository<Fido2CredentialParams, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsSearchForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2CredentialParamsSearchForm extends SearchForm {
    private String credType;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("credType", credType); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2CredentialParamsService.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO2_CREDENTIAL_PARAMS. COMPANY_IDX 가 없어 SUPER 전용. UPDATEDTIME 컬럼 없음. */
@Service
public class Fido2CredentialParamsService extends CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm> {

    public Fido2CredentialParamsService(Fido2CredentialParamsRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Fido2CredentialParams> toSpecification(Fido2CredentialParamsSearchForm f) {
        return Specs.all(
            Specs.like("credType", f.getCredType()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2CredentialParams e) { return null; }
    @Override protected void setCompanyIdx(Fido2CredentialParams e, Long c) {}
    @Override public String idOf(Fido2CredentialParams e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO2_CREDENTIAL_PARAMS"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "credType", "credAlg", "createdtime"); }

    /** ERD 기본값: CRED_TYPE 'public-key', STATUS 'T'. */
    @Override protected void applyDefaults(Fido2CredentialParams e) {
        if (e.getCredType() == null || e.getCredType().isBlank()) e.setCredType("public-key");
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("T");
    }
    @Override protected void touchCreated(Fido2CredentialParams e, LocalDateTime now) { e.setCreatedtime(now); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2CredentialParamsController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class Fido2CredentialParamsControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2CredentialParamsService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/credential-params").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void superListRendersRows() throws Exception {
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setIdx(1L); p.setCredType("public-key"); p.setCredAlg(-7L); p.setStatus("T");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(p)));
        mvc.perform(get("/fido2/credential-params").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/list"))
            .andExpect(content().string(containsString("public-key")))
            .andExpect(content().string(containsString("-7")));
    }

    @Test void missingCredAlgShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "").param("status", "T"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/credential-params/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        Fido2CredentialParams saved = new Fido2CredentialParams(); saved.setIdx(4L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("4");
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).with(csrf())
                .param("credType", "public-key").param("credAlg", "-8").param("status", "T"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/credential-params/4"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/fido2/credential-params").with(user(superUser)).param("credAlg", "-7"))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsControllerWebTest'`
Expected: FAIL — `Fido2CredentialParamsController` 없음.

- [ ] **Step 7: 폼·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** FIDO2_CREDENTIAL_PARAMS 입력 폼. CRED_ALG 는 COSE 알고리즘 번호(-7 ES256, -257 RS256, -8 EdDSA). */
@Getter @Setter
public class Fido2CredentialParamsForm {
    @NotBlank @ByteSize(max = 32) private String credType = "public-key";
    @NotNull private Long credAlg;
    @NotBlank @ByteSize(max = 1) private String status = "T";

    public static Fido2CredentialParamsForm from(Fido2CredentialParams p) {
        Fido2CredentialParamsForm f = new Fido2CredentialParamsForm();
        f.credType = p.getCredType(); f.credAlg = p.getCredAlg(); f.status = p.getStatus();
        return f;
    }

    public void applyTo(Fido2CredentialParams p) {
        p.setCredType(credType); p.setCredAlg(credAlg); p.setStatus(status);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2CredentialParamsController.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/credential-params")
@RequiredArgsConstructor
public class Fido2CredentialParamsController
        extends CrudController<Fido2CredentialParams, Long, Fido2CredentialParamsForm, Fido2CredentialParamsSearchForm> {

    private final Fido2CredentialParamsService service;

    @Override protected CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/credential-params"; }
    @Override protected String viewDir() { return "fido2/credential-params"; }
    @Override protected Fido2CredentialParamsSearchForm newSearchForm() { return new Fido2CredentialParamsSearchForm(); }
    @Override protected Fido2CredentialParamsForm newForm() { return new Fido2CredentialParamsForm(); }
    @Override protected Fido2CredentialParamsForm toForm(Fido2CredentialParams e) { return Fido2CredentialParamsForm.from(e); }
    @Override protected Fido2CredentialParams toEntity(Fido2CredentialParamsForm f) {
        Fido2CredentialParams p = new Fido2CredentialParams(); f.applyTo(p); return p;
    }
    @Override protected void applyForm(Fido2CredentialParamsForm f, Fido2CredentialParams e) { f.applyTo(e); }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/fido2/credential-params/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 크리덴셜 파라미터</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">FIDO2 크리덴셜 파라미터</h1>
    <a th:href="@{/fido2/credential-params/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/fido2/credential-params}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">타입</label><input name="credType" th:value="${search.credType}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="T" th:selected="${search.status == 'T'}">T (사용)</option>
        <option value="F" th:selected="${search.status == 'F'}">F (미사용)</option>
      </select>
    </div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/fido2/credential-params}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>타입</th><th>알고리즘(COSE)</th><th>상태</th><th>등록일</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/fido2/credential-params/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${r.credType}"></td>
          <td th:text="${r.credAlg}"></td>
          <td><span class="badge" th:classappend="${r.status == 'T'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="5" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/credential-params/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 크리덴셜 파라미터 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|크리덴셜 파라미터 #${item.idx}|">크리덴셜 파라미터</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/fido2/credential-params/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/fido2/credential-params/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/fido2/credential-params}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>타입</th><td th:text="${item.credType}"></td></tr>
      <tr><th>알고리즘(COSE)</th><td th:text="${item.credAlg}"></td></tr>
      <tr><th>상태</th><td th:text="${item.status}"></td></tr>
      <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/credential-params/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '크리덴셜 파라미터 등록' : '크리덴셜 파라미터 수정'">크리덴셜 파라미터</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '크리덴셜 파라미터 등록' : '크리덴셜 파라미터 수정'">크리덴셜 파라미터</h1>
  <form th:action="${isNew} ? @{/fido2/credential-params} : @{/fido2/credential-params/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 560px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">타입 <span class="text-danger">*</span></label>
        <input th:field="*{credType}" class="form-control" th:classappend="${#fields.hasErrors('credType')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{credType}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">알고리즘(COSE) <span class="text-danger">*</span></label>
        <input type="number" th:field="*{credAlg}" class="form-control" placeholder="-7 (ES256), -257 (RS256)" th:classappend="${#fields.hasErrors('credAlg')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{credAlg}"></div>
      </div>
      <div class="col-md-6">
        <label class="form-label">상태 <span class="text-danger">*</span></label>
        <select th:field="*{status}" class="form-select"><option value="T">T (사용)</option><option value="F">F (미사용)</option></select>
      </div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/fido2/credential-params} : @{/fido2/credential-params/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsControllerWebTest'`
Expected: PASS (5개).

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido2 src/main/resources/templates/fido2/credential-params src/test/java/com/crosscert/fidoadmin/fido2
git commit -m "feat: FIDO2 크리덴셜 파라미터 CRUD 화면 (기본값 public-key/T)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 15: FIDO2 데모 접근코드 (FIDO2_DEMO_ACCESS_CODE) CRUD — 할당형 PK, epoch ↔ 일시

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/EpochSeconds.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2DemoAccessCodeRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2DemoAccessCodeService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeController.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeView.java`
- Create: `src/main/resources/templates/fido2/demo-access-code/list.html`
- Create: `src/main/resources/templates/fido2/demo-access-code/detail.html`
- Create: `src/main/resources/templates/fido2/demo-access-code/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/web/EpochSecondsTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2DemoAccessCodeServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeControllerWebTest.java`

**Interfaces:**
- Consumes: Task 1 의 `AssignedIdCrudService<E, ID, S>`(생성자 `(AdminRepository<E, ID>, AuditLogger, EntityManager)`, 추상 `ID assignedId(E)`, `insert()` 가 존재 검사 후 `persist`) 와 `CrudController.validate(F form, boolean isNew, BindingResult binding)`. 엔티티 `Fido2DemoAccessCode`(할당형 문자열 PK `accesscode`, `starttime`/`endtime` 은 **epoch 초** `Long`, 시드 값 1767225600 = 2026-01-01T00:00Z). DDL 기본값 `STATUS='E'`. 타임스탬프 컬럼 없음.
- Produces:
  - `EpochSeconds.toEpoch(LocalDateTime): Long`, `EpochSeconds.fromEpoch(Long): LocalDateTime` — `Asia/Seoul` 기준, null 안전.
  - `Fido2DemoAccessCodeRepository extends AdminRepository<Fido2DemoAccessCode, String>`
  - `Fido2DemoAccessCodeService extends AssignedIdCrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm>` — 생성자 `(Fido2DemoAccessCodeRepository, AuditLogger, EntityManager)`, `assignedId`/`idOf` = accesscode, `defaultSort()` = accesscode ASC, `sortableProperties()` = `{"accesscode","vendorname","status"}`, `applyDefaults` 는 status `"E"`.
  - `Fido2DemoAccessCodeView(String accesscode, String vendorname, Long starttime, Long endtime, LocalDateTime startAt, LocalDateTime endAt, String status, String note, String etc)` — 목록·상세 공용 record.
  - 경로 `/fido2/demo-access-codes`, 뷰 디렉터리 `fido2/demo-access-code`. 상세·수정 경로의 `{id}` 는 ACCESSCODE 문자열.

- [ ] **Step 1: EpochSeconds 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/web/EpochSecondsTest.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EpochSecondsTest {

    /** 시드 값 1767225600 = 2026-01-01T00:00:00Z = 서울 2026-01-01 09:00. */
    @Test void seedEpochIsSeoulNineOclock() {
        assertThat(EpochSeconds.fromEpoch(1767225600L)).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        assertThat(EpochSeconds.toEpoch(LocalDateTime.of(2026, 1, 1, 9, 0))).isEqualTo(1767225600L);
    }

    @Test void roundTrip() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 17, 13, 45);
        assertThat(EpochSeconds.fromEpoch(EpochSeconds.toEpoch(t))).isEqualTo(t);
    }

    @Test void nullPassesThrough() {
        assertThat(EpochSeconds.toEpoch(null)).isNull();
        assertThat(EpochSeconds.fromEpoch(null)).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.EpochSecondsTest'`
Expected: FAIL — `EpochSeconds` 없음.

- [ ] **Step 3: EpochSeconds 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/web/EpochSeconds.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * FIDO2_DEMO_ACCESS_CODE.STARTTIME/ENDTIME 은 epoch 초(NUMBER) 로 저장된다(설계 4.2).
 * 화면은 서울 시각으로 보여주고 입력받는다. 밀리초가 아니라 초 단위다(시드 1767225600 = 2026-01-01T00:00Z).
 */
public final class EpochSeconds {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private EpochSeconds() {}

    public static Long toEpoch(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toEpochSecond();
    }

    public static LocalDateTime fromEpoch(Long epochSeconds) {
        return epochSeconds == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZONE);
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.EpochSecondsTest'`
Expected: PASS.

- [ ] **Step 5: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/service/Fido2DemoAccessCodeServiceTest.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2DemoAccessCodeServiceTest {

    Fido2DemoAccessCodeRepository repo = mock(Fido2DemoAccessCodeRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    Fido2DemoAccessCodeService service = new Fido2DemoAccessCodeService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private Fido2DemoAccessCode code(String accesscode) {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode(); c.setAccesscode(accesscode); c.setVendorname("벤더"); return c;
    }

    /** 할당형 PK: 기존 코드로 등록하면 덮어쓰지 않고 거부한다. */
    @Test void duplicateAccessCodeIsRejectedWithoutPersist() {
        when(repo.existsById("DEMO-0001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(code("DEMO-0001")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("DEMO-0001");
        verify(em, never()).persist(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void newAccessCodePersistsWithDefaultStatusAndAudits() {
        when(repo.existsById("DEMO-0003")).thenReturn(false);
        Fido2DemoAccessCode saved = service.create(code("DEMO-0003"));
        assertThat(saved.getStatus()).isEqualTo("E");
        verify(em).persist(saved);
        verify(em).flush();
        verify(repo, never()).save(any());
        verify(audit).log(AuditType.CREATE, "FIDO2_DEMO_ACCESS_CODE CREATE DEMO-0003");
    }

    @Test void explicitStatusIsKept() {
        when(repo.existsById("DEMO-0004")).thenReturn(false);
        Fido2DemoAccessCode c = code("DEMO-0004"); c.setStatus("D");
        assertThat(service.create(c).getStatus()).isEqualTo("D");
    }

    /** idx 가 없는 엔티티라 기본 정렬을 반드시 재정의해야 한다(기본 "idx" 면 조회 시 500). */
    @Test void sortsByAccessCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "accesscode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("accesscode", "vendorname", "status");
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeServiceTest'`
Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 7: 리포지토리·검색 폼·서비스 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/repository/Fido2DemoAccessCodeRepository.java`

```java
package com.crosscert.fidoadmin.fido2.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;

public interface Fido2DemoAccessCodeRepository extends AdminRepository<Fido2DemoAccessCode, String> {
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeSearchForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2DemoAccessCodeSearchForm extends SearchForm {
    private String accesscode;
    private String vendorname;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accesscode", accesscode); m.put("vendorname", vendorname); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2DemoAccessCodeService.java`

```java
package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2DemoAccessCodeSearchForm;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * FIDO2_DEMO_ACCESS_CODE. 할당형 문자열 PK(ACCESSCODE) 라 존재 검사 + persist 로 등록한다.
 * COMPANY_IDX 가 없어 SUPER 전용. 타임스탬프 컬럼이 없다.
 */
@Service
public class Fido2DemoAccessCodeService
        extends AssignedIdCrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm> {

    public Fido2DemoAccessCodeService(Fido2DemoAccessCodeRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<Fido2DemoAccessCode> toSpecification(Fido2DemoAccessCodeSearchForm f) {
        return Specs.all(
            Specs.like("accesscode", f.getAccesscode()),
            Specs.like("vendorname", f.getVendorname()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2DemoAccessCode e) { return null; }
    @Override protected void setCompanyIdx(Fido2DemoAccessCode e, Long c) {}
    @Override protected String assignedId(Fido2DemoAccessCode e) { return e.getAccesscode(); }
    @Override public String idOf(Fido2DemoAccessCode e) { return e.getAccesscode(); }
    @Override protected String tableName() { return "FIDO2_DEMO_ACCESS_CODE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "accesscode"); }
    @Override public Set<String> sortableProperties() { return Set.of("accesscode", "vendorname", "status"); }

    /** ERD 기본값: STATUS 'E'(활성). */
    @Override protected void applyDefaults(Fido2DemoAccessCode e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("E");
    }
}
```

- [ ] **Step 8: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeServiceTest'`
Expected: PASS (4개).

- [ ] **Step 9: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeControllerWebTest.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2DemoAccessCodeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class Fido2DemoAccessCodeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2DemoAccessCodeService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Fido2DemoAccessCode seedCode() {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode();
        c.setAccesscode("DEMO-0001"); c.setVendorname("벤더A"); c.setStarttime(1767225600L); c.setEndtime(1798761600L);
        c.setStatus("E"); c.setNote("데모용");
        return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/demo-access-codes").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** epoch 초 1767225600 은 서울 2026-01-01 09:00 으로 보여야 한다. */
    @Test void listShowsEpochAsSeoulDateTime() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("accesscode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(seedCode())));
        mvc.perform(get("/fido2/demo-access-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/list"))
            .andExpect(content().string(containsString("DEMO-0001")))
            .andExpect(content().string(containsString("2026-01-01 09:00")));
    }

    @Test void detailShowsRawEpochToo() throws Exception {
        when(service.get("DEMO-0001")).thenReturn(seedCode());
        mvc.perform(get("/fido2/demo-access-codes/DEMO-0001").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/detail"))
            .andExpect(content().string(containsString("1767225600")))
            .andExpect(content().string(containsString("2026-01-01 09:00")));
    }

    @Test void endBeforeStartShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("status", "E")
                .param("starttime", "2026-01-02T00:00").param("endtime", "2026-01-01T00:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("종료일시는 시작일시보다 빠를 수 없습니다.")));
    }

    @Test void blankAccessCodeShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"));
    }

    @Test void createRedirectsToDetailByAccessCode() throws Exception {
        Fido2DemoAccessCode saved = new Fido2DemoAccessCode(); saved.setAccesscode("DEMO-0003");
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("DEMO-0003");
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("vendorname", "벤더C").param("status", "E")
                .param("starttime", "2026-01-01T09:00").param("endtime", "2026-12-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/demo-access-codes/DEMO-0003"));
    }
}
```

- [ ] **Step 10: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2DemoAccessCodeControllerWebTest'`
Expected: FAIL — `Fido2DemoAccessCodeController` 없음.

- [ ] **Step 11: 폼·출력 DTO·컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeForm.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * FIDO2_DEMO_ACCESS_CODE 입력 폼. 시작·종료는 화면에서 일시로 받고 저장 시 epoch 초로 바꾼다.
 * ACCESSCODE 는 할당형 PK 라 수정 화면에서는 readonly 이고 applyTo 는 기존 값을 바꾸지 않는다.
 */
@Getter @Setter
public class Fido2DemoAccessCodeForm {
    @NotBlank @ByteSize(max = 128) private String accesscode;
    @ByteSize(max = 128) private String vendorname;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime starttime;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime endtime;
    @NotBlank @ByteSize(max = 1) private String status = "E";
    @ByteSize(max = 300) private String note;
    @ByteSize(max = 1024) private String etc;

    public static Fido2DemoAccessCodeForm from(Fido2DemoAccessCode c) {
        Fido2DemoAccessCodeForm f = new Fido2DemoAccessCodeForm();
        f.accesscode = c.getAccesscode(); f.vendorname = c.getVendorname();
        f.starttime = EpochSeconds.fromEpoch(c.getStarttime()); f.endtime = EpochSeconds.fromEpoch(c.getEndtime());
        f.status = c.getStatus(); f.note = c.getNote(); f.etc = c.getEtc();
        return f;
    }

    /** 신규 엔티티에만 식별자를 채운다. 수정 시에는 applyTo 만 쓰므로 식별자가 바뀌지 않는다. */
    public Fido2DemoAccessCode toNewEntity() {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode();
        c.setAccesscode(accesscode == null ? null : accesscode.trim());
        applyTo(c);
        return c;
    }

    public void applyTo(Fido2DemoAccessCode c) {
        c.setVendorname(vendorname);
        c.setStarttime(EpochSeconds.toEpoch(starttime)); c.setEndtime(EpochSeconds.toEpoch(endtime));
        c.setStatus(status); c.setNote(note); c.setEtc(etc);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeView.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import java.time.LocalDateTime;

/** 목록·상세 공용 출력. epoch 원값과 서울 일시를 함께 싣는다. */
public record Fido2DemoAccessCodeView(String accesscode, String vendorname, Long starttime, Long endtime,
                                      LocalDateTime startAt, LocalDateTime endAt, String status, String note, String etc) {
    public static Fido2DemoAccessCodeView of(Fido2DemoAccessCode c) {
        return new Fido2DemoAccessCodeView(c.getAccesscode(), c.getVendorname(), c.getStarttime(), c.getEndtime(),
            EpochSeconds.fromEpoch(c.getStarttime()), EpochSeconds.fromEpoch(c.getEndtime()),
            c.getStatus(), c.getNote(), c.getEtc());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/fido2/web/Fido2DemoAccessCodeController.java`

```java
package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/demo-access-codes")
@RequiredArgsConstructor
public class Fido2DemoAccessCodeController
        extends CrudController<Fido2DemoAccessCode, String, Fido2DemoAccessCodeForm, Fido2DemoAccessCodeSearchForm> {

    private final Fido2DemoAccessCodeService service;

    @Override protected CrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/demo-access-codes"; }
    @Override protected String viewDir() { return "fido2/demo-access-code"; }
    @Override protected Fido2DemoAccessCodeSearchForm newSearchForm() { return new Fido2DemoAccessCodeSearchForm(); }
    @Override protected Fido2DemoAccessCodeForm newForm() { return new Fido2DemoAccessCodeForm(); }
    @Override protected Fido2DemoAccessCodeForm toForm(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeForm.from(e); }
    /** 할당형 PK 예외: 등록 시에만 폼의 ACCESSCODE 를 식별자로 쓴다. 서비스가 존재 검사 후 persist 한다. */
    @Override protected Fido2DemoAccessCode toEntity(Fido2DemoAccessCodeForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(Fido2DemoAccessCodeForm f, Fido2DemoAccessCode e) { f.applyTo(e); }
    @Override protected Object toListView(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeView.of(e); }
    @Override protected Object toDetailView(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeView.of(e); }

    @Override protected void validate(Fido2DemoAccessCodeForm form, boolean isNew, BindingResult binding) {
        if (form.getStarttime() != null && form.getEndtime() != null && form.getEndtime().isBefore(form.getStarttime())) {
            binding.rejectValue("endtime", "range", "종료일시는 시작일시보다 빠를 수 없습니다.");
        }
    }
}
```

- [ ] **Step 12: 템플릿 작성**

`src/main/resources/templates/fido2/demo-access-code/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 데모 접근코드</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">FIDO2 데모 접근코드</h1>
    <a th:href="@{/fido2/demo-access-codes/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/fido2/demo-access-codes}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">코드</label><input name="accesscode" th:value="${search.accesscode}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">벤더</label><input name="vendorname" th:value="${search.vendorname}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="E" th:selected="${search.status == 'E'}">E (활성)</option>
        <option value="D" th:selected="${search.status == 'D'}">D (만료)</option>
      </select>
    </div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/fido2/demo-access-codes}" class="btn btn-link btn-sm">초기화</a></div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>코드</th><th>벤더</th><th>시작일시</th><th>종료일시</th><th>상태</th><th>비고</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/fido2/demo-access-codes/{id}(id=${r.accesscode})}" class="fa-mono" th:text="${r.accesscode}">코드</a></td>
          <td th:text="${r.vendorname}"></td>
          <td th:text="${r.startAt == null} ? '' : ${#temporals.format(r.startAt, 'yyyy-MM-dd HH:mm')}"></td>
          <td th:text="${r.endAt == null} ? '' : ${#temporals.format(r.endAt, 'yyyy-MM-dd HH:mm')}"></td>
          <td><span class="badge" th:classappend="${r.status == 'E'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${#strings.abbreviate(r.note, 40)}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="6" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/demo-access-code/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO2 데모 접근코드 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|데모 접근코드 ${item.accesscode}|">데모 접근코드</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/fido2/demo-access-codes/{id}/edit(id=${item.accesscode})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/fido2/demo-access-codes/{id}/delete(id=${item.accesscode})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/fido2/demo-access-codes}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>코드</th><td class="fa-mono" th:text="${item.accesscode}"></td></tr>
      <tr><th>벤더</th><td th:text="${item.vendorname}"></td></tr>
      <tr><th>시작일시</th><td><span th:text="${item.startAt == null} ? '' : ${#temporals.format(item.startAt, 'yyyy-MM-dd HH:mm:ss')}"></span> <small class="text-secondary fa-mono" th:if="${item.starttime != null}" th:text="|(epoch ${item.starttime})|"></small></td></tr>
      <tr><th>종료일시</th><td><span th:text="${item.endAt == null} ? '' : ${#temporals.format(item.endAt, 'yyyy-MM-dd HH:mm:ss')}"></span> <small class="text-secondary fa-mono" th:if="${item.endtime != null}" th:text="|(epoch ${item.endtime})|"></small></td></tr>
      <tr><th>상태</th><td th:text="${item.status}"></td></tr>
      <tr><th>비고</th><td class="fa-pre" th:text="${item.note}"></td></tr>
      <tr><th>기타</th><td class="fa-pre" th:text="${item.etc}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/fido2/demo-access-code/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '데모 접근코드 등록' : '데모 접근코드 수정'">데모 접근코드</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '데모 접근코드 등록' : '데모 접근코드 수정'">데모 접근코드</h1>
  <form th:action="${isNew} ? @{/fido2/demo-access-codes} : @{/fido2/demo-access-codes/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">코드 <span class="text-danger">*</span></label>
        <!-- 할당형 PK: 수정 시에는 바꿀 수 없다. -->
        <input th:field="*{accesscode}" class="form-control fa-mono" th:readonly="${!isNew}" th:classappend="${#fields.hasErrors('accesscode')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{accesscode}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">벤더</label><input th:field="*{vendorname}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">시작일시</label><input type="datetime-local" th:field="*{starttime}" class="form-control"></div>
      <div class="col-md-6">
        <label class="form-label">종료일시</label>
        <input type="datetime-local" th:field="*{endtime}" class="form-control" th:classappend="${#fields.hasErrors('endtime')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{endtime}"></div>
      </div>
      <div class="col-md-3">
        <label class="form-label">상태 <span class="text-danger">*</span></label>
        <select th:field="*{status}" class="form-select"><option value="E">E (활성)</option><option value="D">D (만료)</option></select>
      </div>
      <div class="col-12"><label class="form-label">비고</label><input th:field="*{note}" class="form-control"></div>
      <div class="col-12"><label class="form-label">기타</label><textarea th:field="*{etc}" rows="3" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/fido2/demo-access-codes} : @{/fido2/demo-access-codes/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 13: 웹 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido2.web.Fido2DemoAccessCodeControllerWebTest'`
Expected: PASS (6개).

- [ ] **Step 14: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 전부 PASS. `ErdConformanceTest` 도 통과(엔티티를 건드리지 않았다).

- [ ] **Step 15: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido2 src/main/resources/templates/fido2/demo-access-code src/test/java/com/crosscert/fidoadmin/fido2
git commit -m "feat: FIDO2 데모 접근코드 CRUD 화면 (할당형 PK persist 경로, epoch 초 ↔ 서울 일시)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 16: FIDO 로그 조회 (FIDO_LOGS)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/log/repository/FidoLogsRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/FidoLogSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/FidoLogRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/FidoLogView.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/FidoLogController.java`
- Create: `src/main/resources/templates/log/fido/list.html`
- Create: `src/main/resources/templates/log/fido/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/log/service/FidoLogQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/log/web/FidoLogControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `ReadOnlyController<E, ID, S>`, `CrudService<E, ID, S>`, `AdminRepository`, `Specs`, `SearchForm`(`fromDateTime()`, `toDateTimeExclusive()`), `CompanyLookup`(`all()`, `names()`, `name(Long)`), `TenantContext.isSuper()`, 엔티티 `FidoLogs`(idx, companyIdx, serialcode, servicename, jsondata CLOB, createdtime). Task 1 의 `JsonPretty.pretty(String)`.
- Produces: `GET /logs/fido`(목록), `GET /logs/fido/{id}`(상세). `FidoLogQueryService.defaultSort()` = `createdtime DESC, idx DESC`, `sortableProperties()` = `{idx, createdtime, servicename}`. 출력 DTO `FidoLogRow`(목록, CLOB 제외), `FidoLogView`(상세, `jsondataPretty` 포함).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/service/FidoLogQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.repository.FidoLogsRepository;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FidoLogQueryServiceTest {

    FidoLogsRepository repo = mock(FidoLogsRepository.class);
    FidoLogQueryService service = new FidoLogQueryService(repo, mock(AuditLogger.class));

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void logsSortByCreatedtimeThenIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdtime", "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime", "servicename");
    }

    @Test void tenantAttributeIsCompanyIdx() {
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
        FidoLogs l = new FidoLogs(); l.setCompanyIdx(7L);
        assertThat(service.companyIdxOf(l)).isEqualTo(7L);
    }

    /** 기간 조건이 createdtime 에, 서비스명·시리얼이 like 로 걸리는지 Specification 을 실제로 평가해 확인한다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void specificationUsesCreatedtimeAndLikeColumns() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        FidoLogSearchForm f = new FidoLogSearchForm();
        f.setServicename("kbstar"); f.setSerialcode("SN-");
        f.setFromDate(LocalDate.of(2026, 9, 1)); f.setToDate(LocalDate.of(2026, 9, 16));

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<FidoLogs>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<FidoLogs> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root, Mockito.atLeastOnce()).get("createdtime");
        verify(root).get("servicename");
        verify(root).get("serialcode");
        verify(root).get("companyIdx"); // COMPANY 역할의 테넌트 필터
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.FidoLogQueryServiceTest'`
Expected: FAIL — `FidoLogsRepository`, `FidoLogQueryService`, `FidoLogSearchForm` 없음(컴파일 오류).

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/log/repository/FidoLogsRepository.java`

```java
package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.log.entity.FidoLogs;

public interface FidoLogsRepository extends AdminRepository<FidoLogs, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/FidoLogSearchForm.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** FIDO_LOGS 검색: 고객사(base companyIdx), 서비스명, 시리얼, 기간(base fromDate/toDate → CREATEDTIME). */
@Getter @Setter
public class FidoLogSearchForm extends SearchForm {
    private String servicename;
    private String serialcode;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("servicename", servicename); m.put("serialcode", serialcode);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/service/FidoLogQueryService.java`

```java
package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.repository.FidoLogsRepository;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO_LOGS 조회 전용(append-only 로그). create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class FidoLogQueryService extends CrudService<FidoLogs, Long, FidoLogSearchForm> {

    public FidoLogQueryService(FidoLogsRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<FidoLogs> toSpecification(FidoLogSearchForm f) {
        return Specs.all(
            Specs.like("servicename", f.getServicename()),
            Specs.like("serialcode", f.getSerialcode()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(FidoLogs e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(FidoLogs e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(FidoLogs e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO_LOGS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createdtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime", "servicename"); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.FidoLogQueryServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/web/FidoLogControllerWebTest.java`

```java
package com.crosscert.fidoadmin.log.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FidoLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class FidoLogControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FidoLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private FidoLogs log(long idx, String json) {
        FidoLogs l = new FidoLogs();
        l.setIdx(idx); l.setCompanyIdx(1L); l.setSerialcode("SN-0001"); l.setServicename("kbstar");
        l.setJsondata(json); l.setCreatedtime(LocalDateTime.of(2026, 9, 16, 10, 0));
        return l;
    }

    /** COMPANY 역할: 고객사 select 가 없고, CLOB(JSONDATA) 은 목록에 나오지 않는다. */
    @Test void companyListHidesCompanyFilterAndClob() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(log(5L, "MARKER_JSON_ONLY_IN_DETAIL"))));
        mvc.perform(get("/logs/fido").param("servicename", "kbstar").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/list"))
            .andExpect(content().string(containsString("SN-0001")))
            .andExpect(content().string(not(containsString("MARKER_JSON_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        ArgumentCaptor<FidoLogSearchForm> captor = ArgumentCaptor.forClass(FidoLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getServicename()).isEqualTo("kbstar");
    }

    @Test void superListShowsCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/fido").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }

    /**
     * 상세는 JSONDATA 를 정리해 보여준다. th:text 는 따옴표를 &quot; 로 이스케이프하므로
     * 정리된 형태 `"op" : "Auth"` 는 HTML 에서 `&quot;op&quot; : &quot;Auth&quot;` 로 나타난다.
     */
    @Test void detailPrettyPrintsJson() throws Exception {
        when(service.get(5L)).thenReturn(log(5L, "{\"op\":\"Auth\",\"result\":\"1200\"}"));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/fido/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/detail"))
            .andExpect(content().string(containsString("&quot;op&quot; : &quot;Auth&quot;")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.FidoLogControllerWebTest'`
Expected: FAIL — `FidoLogController` 없음.

- [ ] **Step 7: 출력 DTO 와 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/log/web/FidoLogRow.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.FidoLogs;
import java.time.LocalDateTime;

/** 목록 행. CLOB(JSONDATA) 은 싣지 않는다. */
public record FidoLogRow(Long idx, Long companyIdx, String serialcode, String servicename, LocalDateTime createdtime) {
    public static FidoLogRow from(FidoLogs e) {
        return new FidoLogRow(e.getIdx(), e.getCompanyIdx(), e.getSerialcode(), e.getServicename(), e.getCreatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/FidoLogView.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import java.time.LocalDateTime;

/** 상세. JSONDATA 는 정리(들여쓰기)해 담는다. JSON 이 아니면 원문 그대로. */
public record FidoLogView(Long idx, Long companyIdx, String serialcode, String servicename,
                          LocalDateTime createdtime, String jsondataPretty) {
    public static FidoLogView from(FidoLogs e) {
        return new FidoLogView(e.getIdx(), e.getCompanyIdx(), e.getSerialcode(), e.getServicename(),
            e.getCreatedtime(), JsonPretty.pretty(e.getJsondata()));
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/FidoLogController.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/fido")
@RequiredArgsConstructor
public class FidoLogController extends ReadOnlyController<FidoLogs, Long, FidoLogSearchForm> {

    private final FidoLogQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<FidoLogs, Long, FidoLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/fido"; }
    @Override protected String viewDir() { return "log/fido"; }
    @Override protected Object toListView(FidoLogs e) { return FidoLogRow.from(e); }
    @Override protected Object toDetailView(FidoLogs e) { return FidoLogView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(FidoLogs e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/log/fido/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO 로그</title></head>
<body>
<main>
  <h1 class="h4 mb-3">FIDO 로그</h1>
  <form method="get" th:action="@{/logs/fido}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">서비스명</label><input name="servicename" th:value="${search.servicename}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시리얼</label><input name="serialcode" th:value="${search.serialcode}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/logs/fido}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>서비스명</th><th>시리얼</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/logs/fido/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.servicename}"></td>
          <td class="fa-mono" th:text="${r.serialcode}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="5" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/log/fido/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO 로그 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|FIDO 로그 #${item.idx}|"></h1>
    <a th:href="@{/logs/fido}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>서비스명</th><td th:text="${item.servicename}"></td></tr>
      <tr><th>시리얼</th><td class="fa-mono" th:text="${item.serialcode}"></td></tr>
      <tr><th>JSONDATA</th><td><pre class="fa-pre fa-mono mb-0" th:text="${item.jsondataPretty}"></pre></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.FidoLogControllerWebTest' --tests 'com.crosscert.fidoadmin.log.service.FidoLogQueryServiceTest'`
Expected: PASS.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/log src/main/resources/templates/log/fido src/test/java/com/crosscert/fidoadmin/log
git commit -m "feat: FIDO 로그 조회 화면 (JSONDATA 정리 출력)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 17: 예외 로그 조회 (CCFA_EXCEPTIONS)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/log/repository/CcfaExceptionsRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/service/ExceptionLogQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogView.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogController.java`
- Create: `src/main/resources/templates/log/exceptions/list.html`
- Create: `src/main/resources/templates/log/exceptions/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/log/service/ExceptionLogQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/log/web/ExceptionLogControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `ReadOnlyController`, `CrudService`, `Specs`, `SearchForm`, `CompanyLookup`, `TenantContext`. 엔티티 `CcfaExceptions`(idx, companyIdx, eType, eLevel, exceptionMessage, exceptionDetailMessage, exceptionData CLOB, createdtime **String** VARCHAR2(64)). Lombok 접근자는 `getEType()`, `getELevel()` 이다.
- Produces: `GET /logs/exceptions`, `GET /logs/exceptions/{id}`. 검색: 유형(like), 레벨(eq), 메시지(like), 일시 문자열(like on `createdtime`). 기간(fromDate/toDate) 은 이 화면에서 쓰지 않는다(컬럼이 문자열). `defaultSort()` = `idx DESC`, `sortableProperties()` = `{idx, createdtime, eType, eLevel}`. 출력 DTO `ExceptionLogRow`(목록: CLOB·상세 메시지 제외), `ExceptionLogView`(상세: 8컬럼 전부, 속성명은 `type`, `level`, `message`, `detailMessage`, `data` 로 정리).

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/service/ExceptionLogQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository;
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ExceptionLogQueryServiceTest {

    CcfaExceptionsRepository repo = mock(CcfaExceptionsRepository.class);
    ExceptionLogQueryService service = new ExceptionLogQueryService(repo, mock(AuditLogger.class));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime", "eType", "eLevel");
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
    }

    /**
     * CREATEDTIME 은 VARCHAR2 라 기간(between) 이 아니라 문자열 like 로 검색한다(설계 5.2).
     * 실수로 base fromDate 를 between 에 넣으면 문자열 컬럼 비교로 조회가 깨지므로,
     * Specification 이 createdtime 을 like 경로로만 쓰는지 확인한다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void createdtimeIsSearchedAsStringLike() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ExceptionLogSearchForm f = new ExceptionLogSearchForm();
        f.setEType("AUTH"); f.setELevel("ERROR"); f.setMessage("signature"); f.setCreatedtime("2026-09-15");

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<CcfaExceptions>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<CcfaExceptions> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root).get("eType");
        verify(root).get("eLevel");
        verify(root).get("exceptionMessage");
        verify(root).get("createdtime");
        verify(cb, never()).greaterThanOrEqualTo(any(), (java.time.LocalDateTime) any());
        verify(cb, never()).lessThan(any(), (java.time.LocalDateTime) any());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.ExceptionLogQueryServiceTest'`
Expected: FAIL — `CcfaExceptionsRepository`, `ExceptionLogQueryService`, `ExceptionLogSearchForm` 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/log/repository/CcfaExceptionsRepository.java`

```java
package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

public interface CcfaExceptionsRepository extends AdminRepository<CcfaExceptions, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogSearchForm.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_EXCEPTIONS 검색. CREATEDTIME 이 VARCHAR2(64) 라 base 의 fromDate/toDate 대신
 * 문자열 부분 일치(createdtime) 로 검색한다. 필드명은 엔티티 속성명(eType, eLevel) 과 같게 둔다.
 */
@Getter @Setter
public class ExceptionLogSearchForm extends SearchForm {
    private String eType;
    private String eLevel;
    private String message;
    private String createdtime;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eType", eType); m.put("eLevel", eLevel); m.put("message", message); m.put("createdtime", createdtime);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/service/ExceptionLogQueryService.java`

```java
package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository;
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_EXCEPTIONS 조회 전용. CREATEDTIME 은 문자열이므로 like 검색만 지원한다. */
@Service
public class ExceptionLogQueryService extends CrudService<CcfaExceptions, Long, ExceptionLogSearchForm> {

    public ExceptionLogQueryService(CcfaExceptionsRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<CcfaExceptions> toSpecification(ExceptionLogSearchForm f) {
        return Specs.all(
            Specs.like("eType", f.getEType()),
            Specs.eq("eLevel", f.getELevel()),
            Specs.like("exceptionMessage", f.getMessage()),
            Specs.like("createdtime", f.getCreatedtime()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaExceptions e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaExceptions e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaExceptions e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_EXCEPTIONS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime", "eType", "eLevel"); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.ExceptionLogQueryServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/web/ExceptionLogControllerWebTest.java`

```java
package com.crosscert.fidoadmin.log.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExceptionLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class ExceptionLogControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean ExceptionLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaExceptions row(long idx) {
        CcfaExceptions e = new CcfaExceptions();
        e.setIdx(idx); e.setCompanyIdx(1L); e.setEType("AUTH"); e.setELevel("ERROR");
        e.setExceptionMessage("Invalid signature"); e.setExceptionDetailMessage("DETAIL_ONLY_IN_DETAIL");
        e.setExceptionData("CLOB_ONLY_IN_DETAIL"); e.setCreatedtime("2026-09-15 10:00:00");
        return e;
    }

    /** 검색 파라미터(문자열 일시 포함)가 폼에 바인딩되고, CLOB·상세 메시지는 목록에 나오지 않는다. */
    @Test void listBindsStringCreatedtimeAndHidesLongColumns() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(row(1L))));
        mvc.perform(get("/logs/exceptions").param("eType", "AUTH").param("eLevel", "ERROR")
                .param("message", "signature").param("createdtime", "2026-09-15").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/list"))
            .andExpect(content().string(containsString("Invalid signature")))
            .andExpect(content().string(not(containsString("DETAIL_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("CLOB_ONLY_IN_DETAIL"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("name=\"fromDate\""))));
        ArgumentCaptor<ExceptionLogSearchForm> captor = ArgumentCaptor.forClass(ExceptionLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        ExceptionLogSearchForm f = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(f.getEType()).isEqualTo("AUTH");
        org.assertj.core.api.Assertions.assertThat(f.getELevel()).isEqualTo("ERROR");
        org.assertj.core.api.Assertions.assertThat(f.getMessage()).isEqualTo("signature");
        org.assertj.core.api.Assertions.assertThat(f.getCreatedtime()).isEqualTo("2026-09-15");
    }

    @Test void superSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/exceptions").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsAllColumns() throws Exception {
        when(service.get(1L)).thenReturn(row(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/exceptions/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/exceptions/detail"))
            .andExpect(content().string(containsString("DETAIL_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("CLOB_ONLY_IN_DETAIL")))
            .andExpect(content().string(containsString("2026-09-15 10:00:00")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.ExceptionLogControllerWebTest'`
Expected: FAIL — `ExceptionLogController` 없음.

- [ ] **Step 7: 출력 DTO 와 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogRow.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

/** 목록 행. CLOB(EXCEPTION_DATA) 과 상세 메시지는 싣지 않는다. createdtime 은 원본대로 문자열. */
public record ExceptionLogRow(Long idx, Long companyIdx, String type, String level, String message, String createdtime) {
    public static ExceptionLogRow from(CcfaExceptions e) {
        return new ExceptionLogRow(e.getIdx(), e.getCompanyIdx(), e.getEType(), e.getELevel(),
            e.getExceptionMessage(), e.getCreatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogView.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

/** 상세. ERD 8컬럼 전부. 속성명은 뷰에서 다루기 쉽게 정리한다(eType → type 등). */
public record ExceptionLogView(Long idx, Long companyIdx, String type, String level, String message,
                               String detailMessage, String data, String createdtime) {
    public static ExceptionLogView from(CcfaExceptions e) {
        return new ExceptionLogView(e.getIdx(), e.getCompanyIdx(), e.getEType(), e.getELevel(),
            e.getExceptionMessage(), e.getExceptionDetailMessage(), e.getExceptionData(), e.getCreatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/ExceptionLogController.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/exceptions")
@RequiredArgsConstructor
public class ExceptionLogController extends ReadOnlyController<CcfaExceptions, Long, ExceptionLogSearchForm> {

    private final ExceptionLogQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaExceptions, Long, ExceptionLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/exceptions"; }
    @Override protected String viewDir() { return "log/exceptions"; }
    @Override protected Object toListView(CcfaExceptions e) { return ExceptionLogRow.from(e); }
    @Override protected Object toDetailView(CcfaExceptions e) { return ExceptionLogView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(CcfaExceptions e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/log/exceptions/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>예외 로그</title></head>
<body>
<main>
  <h1 class="h4 mb-3">예외 로그</h1>
  <!-- CREATEDTIME 이 문자열 컬럼이라 기간(date) 입력 대신 문자열 부분 일치 입력을 둔다. -->
  <form method="get" th:action="@{/logs/exceptions}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">유형</label><input name="eType" th:value="${search.eType}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">레벨</label>
      <select name="eLevel" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="ERROR" th:selected="${search.eLevel == 'ERROR'}">ERROR</option>
        <option value="WARN" th:selected="${search.eLevel == 'WARN'}">WARN</option>
        <option value="INFO" th:selected="${search.eLevel == 'INFO'}">INFO</option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">메시지</label><input name="message" th:value="${search.message}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">일시(문자열)</label><input name="createdtime" th:value="${search.createdtime}" placeholder="예: 2026-09-15" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/logs/exceptions}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>유형</th><th>레벨</th><th>메시지</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/logs/exceptions/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${r.createdtime}"></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${r.type}"></td>
          <td><span class="badge" th:classappend="${r.level == 'ERROR'} ? 'text-bg-danger' : 'text-bg-secondary'" th:text="${r.level}"></span></td>
          <td th:text="${#strings.abbreviate(r.message, 80)}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="6" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/log/exceptions/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>예외 로그 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|예외 로그 #${item.idx}|"></h1>
    <a th:href="@{/logs/exceptions}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>일시</th><td th:text="${item.createdtime}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>유형</th><td th:text="${item.type}"></td></tr>
      <tr><th>레벨</th><td th:text="${item.level}"></td></tr>
      <tr><th>메시지</th><td class="fa-pre" th:text="${item.message}"></td></tr>
      <tr><th>상세 메시지</th><td class="fa-pre" th:text="${item.detailMessage}"></td></tr>
      <tr><th>예외 데이터</th><td><pre class="fa-pre fa-mono mb-0" th:text="${item.data}"></pre></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.ExceptionLogControllerWebTest' --tests 'com.crosscert.fidoadmin.log.service.ExceptionLogQueryServiceTest'`
Expected: PASS.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/log src/main/resources/templates/log/exceptions src/test/java/com/crosscert/fidoadmin/log
git commit -m "feat: 예외 로그 조회 화면 (CREATEDTIME 문자열 검색)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 18: 메일/SMS 큐 조회 (CCFA_MAILING)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/log/repository/CcfaMailingRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/MailingSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/service/MailingQueryService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/web/MailingController.java`
- Create: `src/main/resources/templates/log/mailing/list.html`
- Create: `src/main/resources/templates/log/mailing/detail.html`
- Test: `src/test/java/com/crosscert/fidoadmin/log/service/MailingQueryServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/log/web/MailingControllerWebTest.java`

**Interfaces:**
- Consumes: 1부 `ReadOnlyController`, `CrudService`, `Specs`, `SearchForm`, `CompanyLookup`, `TenantContext`. 엔티티 `CcfaMailing`(idx, companyIdx, `to`(예약어 컬럼 `"TO"`), subject, content, status, smsTo, smsContent, smsStatus, sendtime **String**). 민감 컬럼·CLOB 이 없으므로 엔티티를 뷰에 그대로 준다(기본 `toListView`/`toDetailView`).
- Produces: `GET /logs/mailing`, `GET /logs/mailing/{id}`. 검색: 상태(eq `status`), 수신자(like `to`), SMS 상태(eq `smsStatus`). `defaultSort()` = `idx DESC`, `sortableProperties()` = `{idx, status, sendtime}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/service/MailingQueryServiceTest.java`

```java
package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository;
import com.crosscert.fidoadmin.log.web.MailingSearchForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MailingQueryServiceTest {

    CcfaMailingRepository repo = mock(CcfaMailingRepository.class);
    MailingQueryService service = new MailingQueryService(repo, mock(AuditLogger.class));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "status", "sendtime");
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
    }

    /**
     * 수신자 검색은 예약어 컬럼 "TO" 에 매핑된 엔티티 속성 `to` 를 써야 한다.
     * 속성명을 잘못 적으면(예: "TO", "receiver") 조회 시 500 이 나므로 실제 경로를 확인한다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void recipientSearchUsesToAttribute() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        MailingSearchForm f = new MailingSearchForm();
        f.setStatus("SENT"); f.setTo("ops@"); f.setSmsStatus("SENT");

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<CcfaMailing>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<CcfaMailing> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root).get("to");
        verify(root).get("status");
        verify(root).get("smsStatus");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.MailingQueryServiceTest'`
Expected: FAIL — `CcfaMailingRepository`, `MailingQueryService`, `MailingSearchForm` 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/log/repository/CcfaMailingRepository.java`

```java
package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;

public interface CcfaMailingRepository extends AdminRepository<CcfaMailing, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/log/web/MailingSearchForm.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CCFA_MAILING 검색: 상태, 수신자("TO" 컬럼 → 속성 to), SMS 상태. */
@Getter @Setter
public class MailingSearchForm extends SearchForm {
    private String status;
    private String to;
    private String smsStatus;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status); m.put("to", to); m.put("smsStatus", smsStatus);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/log/service/MailingQueryService.java`

```java
package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository;
import com.crosscert.fidoadmin.log.web.MailingSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_MAILING(메일/SMS 발송 큐) 조회 전용. SENDTIME 은 문자열이라 정렬만 지원한다. */
@Service
public class MailingQueryService extends CrudService<CcfaMailing, Long, MailingSearchForm> {

    public MailingQueryService(CcfaMailingRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<CcfaMailing> toSpecification(MailingSearchForm f) {
        return Specs.all(
            Specs.eq("status", f.getStatus()),
            Specs.like("to", f.getTo()),
            Specs.eq("smsStatus", f.getSmsStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaMailing e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaMailing e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaMailing e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MAILING"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "status", "sendtime"); }
}
```

- [ ] **Step 4: 서비스 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.service.MailingQueryServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/log/web/MailingControllerWebTest.java`

```java
package com.crosscert.fidoadmin.log.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.service.MailingQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MailingController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class MailingControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean MailingQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaMailing row(long idx) {
        CcfaMailing m = new CcfaMailing();
        m.setIdx(idx); m.setCompanyIdx(1L); m.setTo("ops@kb.local"); m.setSubject("[FIDO] 장애 알림");
        m.setContent("인증 실패율 증가"); m.setStatus("SENT"); m.setSmsTo("010-0000-0000");
        m.setSmsContent("인증 실패율 증가"); m.setSmsStatus("SENT"); m.setSendtime("2026-09-15 10:05:00");
        return m;
    }

    @Test void companyListRendersRecipientWithoutCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(row(1L))));
        mvc.perform(get("/logs/mailing").param("status", "SENT").param("to", "ops").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/mailing/list"))
            .andExpect(content().string(containsString("ops@kb.local")))
            .andExpect(content().string(containsString("010-0000-0000")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        ArgumentCaptor<MailingSearchForm> captor = ArgumentCaptor.forClass(MailingSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTo()).isEqualTo("ops");
    }

    @Test void superSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/mailing").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }

    @Test void detailShowsAllColumns() throws Exception {
        when(service.get(1L)).thenReturn(row(1L));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/mailing/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/mailing/detail"))
            .andExpect(content().string(containsString("[FIDO] 장애 알림")))
            .andExpect(content().string(containsString("인증 실패율 증가")))
            .andExpect(content().string(containsString("2026-09-15 10:05:00")))
            .andExpect(content().string(containsString("KB국민은행")));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.MailingControllerWebTest'`
Expected: FAIL — `MailingController` 없음.

- [ ] **Step 7: 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/log/web/MailingController.java`

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.service.MailingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 민감 컬럼·CLOB 이 없어 엔티티를 그대로 뷰에 준다(기본 toListView/toDetailView). */
@Controller
@RequestMapping("/logs/mailing")
@RequiredArgsConstructor
public class MailingController extends ReadOnlyController<CcfaMailing, Long, MailingSearchForm> {

    private final MailingQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaMailing, Long, MailingSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/mailing"; }
    @Override protected String viewDir() { return "log/mailing"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(CcfaMailing e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/log/mailing/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>메일/SMS 큐</title></head>
<body>
<main>
  <h1 class="h4 mb-3">메일/SMS 큐</h1>
  <form method="get" th:action="@{/logs/mailing}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">상태</label><input name="status" th:value="${search.status}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">수신자</label><input name="to" th:value="${search.to}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">SMS 상태</label><input name="smsStatus" th:value="${search.smsStatus}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/logs/mailing}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>고객사</th><th>수신자</th><th>제목</th><th>상태</th><th>SMS 수신자</th><th>SMS 상태</th><th>발송시각</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/logs/mailing/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${companyNames.get(r.companyIdx)} ?: ${r.companyIdx}"></td>
          <td th:text="${#strings.abbreviate(r.to, 40)}"></td>
          <td th:text="${#strings.abbreviate(r.subject, 40)}"></td>
          <td><span class="badge text-bg-secondary" th:text="${r.status}"></span></td>
          <td th:text="${#strings.abbreviate(r.smsTo, 40)}"></td>
          <td><span class="badge text-bg-secondary" th:text="${r.smsStatus}"></span></td>
          <td th:text="${r.sendtime}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="8" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>
  <div class="d-flex justify-content-between align-items-center mt-2 small text-secondary">
    <span th:text="|총 ${page.totalElements}건|"></span>
    <div th:replace="~{fragments/pagination :: pagination(${page}, ${basePath})}"></div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/log/mailing/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>메일/SMS 큐 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|메일/SMS 큐 #${item.idx}|"></h1>
    <a th:href="@{/logs/mailing}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>수신자(TO)</th><td class="fa-pre" th:text="${item.to}"></td></tr>
      <tr><th>제목</th><td th:text="${item.subject}"></td></tr>
      <tr><th>내용</th><td class="fa-pre" th:text="${item.content}"></td></tr>
      <tr><th>상태</th><td th:text="${item.status}"></td></tr>
      <tr><th>SMS 수신자</th><td class="fa-pre" th:text="${item.smsTo}"></td></tr>
      <tr><th>SMS 내용</th><td class="fa-pre" th:text="${item.smsContent}"></td></tr>
      <tr><th>SMS 상태</th><td th:text="${item.smsStatus}"></td></tr>
      <tr><th>발송시각</th><td th:text="${item.sendtime}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.MailingControllerWebTest' --tests 'com.crosscert.fidoadmin.log.service.MailingQueryServiceTest'`
Expected: PASS.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/log src/main/resources/templates/log/mailing src/test/java/com/crosscert/fidoadmin/log
git commit -m "feat: 메일/SMS 큐 조회 화면

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 19: 2부 최종 검증 — 전체 테스트, 경로·권한 실측, 브라우저 확인, README

**Files:**
- Modify: `README.md` ("화면을 추가할 때" 절, "문서" 절)
- Modify: `tasks/todo.md` (2부 항목과 Review 블록)
- 새 소스 없음. 로컬 Docker Oracle 과 실행 중인 앱이 필요하다.

**Interfaces:**
- Consumes: 2부 Task 1~18 의 모든 경로. 시드 계정 `superuser / Admin1234!`(SUPER), `kbadmin / Company1234!`(COMPANY_IDX 1). 시드에는 COMPANY_IDX 2 의 USERINFO·FIDO_LOGS 행이 있어 테넌트 격리 실측에 쓴다.
- Produces: 갱신된 `README.md`, `tasks/todo.md` 의 2부 Review 기록. 3부 계획은 이 태스크가 끝난 상태(모든 2부 경로 200/403 실측 완료)를 전제한다.

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL. 실패 0, 스킵 0 (Docker 가 있으면 통합 테스트 4건 실제 실행). 테스트 수를 기록한다:

```bash
grep -h -o 'tests="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END {print "총 테스트:", s}'
grep -h -o 'failures="[0-9]*"' build/test-results/test/*.xml | awk -F'"' '{s+=$2} END {print "실패:", s}'
```

- [ ] **Step 2: 로컬 Oracle 과 앱 기동**

```bash
docker compose -f docker/docker-compose.yml up -d
until [ "$(docker inspect -f '{{.State.Health.Status}}' fido-admin-oracle)" = "healthy" ]; do sleep 5; done
./gradlew bootRun --args='--spring.profiles.active=local' > /tmp/fido-admin-bootrun.log 2>&1 &
until curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/login | grep -q 200; do sleep 3; done
echo "앱 기동 완료"
```

Expected: 마지막 줄 `앱 기동 완료`. `/tmp/fido-admin-bootrun.log` 에 `Started FidoAdminApplication`.

- [ ] **Step 3: SUPER 로 전체 경로 실측**

로그인은 Spring Security formLogin(`POST /login`, 파라미터 `username`/`password`/`_csrf`)이다. CSRF 토큰은 `GET /login` 의 hidden input 에서 긁는다. 아래 스크립트를 그대로 실행한다.

```bash
BASE=http://localhost:8080
ROUTES="/fds-policies /licenses /managers /appids /appservers /users /challenges /signs /transaction-hashes /transaction-confirmations /criteria /fido2/metadata /fido2/credential-params /fido2/demo-access-codes /logs/fido /logs/exceptions /logs/mailing /logs/audit"

login() {  # $1=계정 $2=비밀번호 $3=쿠키 파일
  rm -f "$3"
  TOKEN=$(curl -s -c "$3" -b "$3" "$BASE/login" | grep -o '_csrf" value="[^"]*' | sed 's/.*value="//')
  curl -s -o /dev/null -w "login($1) %{http_code} -> %{redirect_url}\n" -c "$3" -b "$3" \
    --data-urlencode "username=$1" --data-urlencode "password=$2" --data-urlencode "_csrf=$TOKEN" "$BASE/login"
}

probe() {  # $1=쿠키 파일
  for r in $ROUTES; do
    printf '%-32s %s\n' "$r" "$(curl -s -o /dev/null -w '%{http_code}' -b "$1" "$BASE$r")"
  done
}

login superuser 'Admin1234!' /tmp/super.jar
probe /tmp/super.jar
```

Expected: `login(superuser) 302 -> http://localhost:8080/` (리다이렉트 URL 에 `error` 없음). 이어서 18개 경로(2부 17 + 1부 감사 로그 1) 모두 `200`.

| 경로 | SUPER 기대 |
|---|---|
| `/fds-policies` `/licenses` `/managers` | 200 |
| `/appids` `/appservers` `/users` `/challenges` `/signs` `/transaction-hashes` `/transaction-confirmations` `/criteria` | 200 |
| `/fido2/metadata` `/fido2/credential-params` `/fido2/demo-access-codes` | 200 |
| `/logs/fido` `/logs/exceptions` `/logs/mailing` `/logs/audit` | 200 |

하나라도 500 이면 `/tmp/fido-admin-bootrun.log` 의 스택트레이스를 보고 해당 태스크로 돌아가 고친다(전형적 원인: `idx` 가 없는 엔티티의 `defaultSort()` 미재정의, 템플릿 속성명 오타).

- [ ] **Step 4: COMPANY(kbadmin) 로 권한 실측**

```bash
login kbadmin 'Company1234!' /tmp/kb.jar
probe /tmp/kb.jar
```

Expected:

| 경로 | COMPANY 기대 | 근거 |
|---|---|---|
| `/fds-policies` `/appids` `/appservers` `/users` `/challenges` `/signs` `/transaction-hashes` `/transaction-confirmations` `/logs/fido` `/logs/exceptions` `/logs/mailing` `/logs/audit` | 200 | COMPANY_IDX 가 있는 테이블, 테넌트 필터 적용 |
| `/licenses` `/managers` | 403 | 설계 5.2 (S), `SecurityConfig` |
| `/criteria` `/fido2/metadata` `/fido2/credential-params` `/fido2/demo-access-codes` | 403 | 설계 3.3 (COMPANY_IDX 없음), Task 1 권한 보정 |

또한 kbadmin 의 사이드바에 SUPER 메뉴가 없어야 한다:

```bash
curl -s -b /tmp/kb.jar "$BASE/" | grep -c 'href="/fido2/\|href="/criteria"\|href="/licenses"\|href="/managers"'
```

Expected: `0`.

- [ ] **Step 5: 테넌트 격리 실측 (kbadmin)**

시드에는 COMPANY_IDX 2 의 사용자(`tester`)와 FIDO 로그(`SN-0003`)가 있다. kbadmin 에게 보이면 안 된다.

```bash
curl -s -b /tmp/kb.jar "$BASE/users" | grep -c 'tester'            # 기대 0
curl -s -b /tmp/kb.jar "$BASE/users" | grep -c 'user001'           # 기대 1 이상
curl -s -b /tmp/kb.jar "$BASE/logs/fido" | grep -c 'SN-0003'       # 기대 0
curl -s -b /tmp/kb.jar "$BASE/logs/fido?companyIdx=2" | grep -c 'SN-0003'   # 변조해도 기대 0
curl -s -b /tmp/kb.jar "$BASE/users?companyIdx=2" | grep -c 'tester'        # 변조해도 기대 0
curl -s -o /dev/null -w '%{http_code}\n' -b /tmp/kb.jar "$BASE/users/3"     # 다른 고객사 상세: 기대 404
```

Expected: 주석의 기대값과 일치. 특히 `?companyIdx=2` 변조 시 결과가 변조 전과 같아야 한다(`CrudService.search` 가 COMPANY 역할의 `companyIdx` 를 무시하고 `TenantContext.companyIdx()` 로 덮는다).

- [ ] **Step 6: 브라우저 확인 목록**

`http://localhost:8080` 에서 아래를 눈으로 확인하고 체크한다. 성공 시 플래시 메시지가 뜨고, 삭제·상태 변경은 Bootstrap 모달로 확인한다(브라우저 `alert/confirm` 이 뜨면 실패).

SUPER(`superuser`):
- [ ] `/fds-policies/new` — 고객사 `테스트고객사(2)` 로 등록 → 상세로 이동. 다시 `/fds-policies/new` 에서 고객사 `KB국민은행(1)`(시드에 이미 있음) 로 등록 → 폼에 "이미 존재하는 값이거나 제약 조건에 어긋납니다." 표시, 기존 행이 덮어써지지 않음(`/fds-policies/1` 의 AND_IP 가 `10.0.0.0/8` 그대로).
- [ ] `/managers/new` — `tempadmin` / 비밀번호 `Temp1234!` / 고객사 KB국민은행 / 상태 활성 으로 등록. 로그아웃 후 `tempadmin` 으로 틀린 비밀번호 5회 → 잠김. `superuser` 로 재로그인해 `/managers` 에서 `tempadmin` 상세 → "잠금 해제" → 플래시 "잠금이 해제되었습니다." → `tempadmin` 으로 정상 로그인 가능. `/logs/audit` 에 `CCFA_MANAGER CREATE`, `CCFA_MANAGER UNLOCK` 행 존재.
- [ ] `/managers/new` — 비밀번호를 비우고 등록 → "비밀번호는 필수입니다." 표시(Task 1 `validate()` 훅).
- [ ] `/fido2/demo-access-codes/new` — 코드 `DEMO-0001`(시드에 있음) 로 등록 → 중복 메시지, `/fido2/demo-access-codes/DEMO-0001` 의 벤더명이 `벤더A` 그대로. 새 코드 `DEMO-0003` 등록 → 상세에서 시작/종료가 일시로 표시.
- [ ] `/licenses` 목록에 고객사명이 표시되고, 등록 시 선택한 고객사의 이름이 COMPANY_NAME 에 채워진다.
- [ ] `/criteria`, `/fido2/metadata` 상세에서 CLOB(JSONDATA, ICON, 인증서)이 상세에서만 보이고 목록에는 없다.
- [ ] `/logs/fido/1` 상세의 JSONDATA 가 들여쓰기된 JSON 으로 보인다.

COMPANY(`kbadmin`):
- [ ] `/users` 에서 `user001` 상세 → 공개키·인증서가 앞 16자만 보인다 → 상태 `O → X` 변경(모달 확인) → 목록 상태 `X` → 다시 `X → O`. `/logs/audit` 에 `TYPE=STATUS` 행 2건이 kbadmin 명의로 남는다.
- [ ] `/appids/new` — 고객사 select 가 없고, 등록한 행의 상세에서 고객사가 `KB국민은행` 이다(강제 COMPANY_IDX).
- [ ] 사이드바에 `고객사 > 고객사`, `라이선스`, `운영자 > 운영자`, `FIDO > 인증기기 기준`, `FIDO2` 그룹, `시스템` 그룹이 없다.
- [ ] 주소창에 `/fido2/metadata` 직접 입력 → 403 페이지.

- [ ] **Step 7: 정적 규칙 검사**

```bash
grep -rn "alert(\|confirm(\|prompt(" src/main/resources ; echo "exit=$?"
grep -rn "https\?://" src/main/resources/templates | grep -v "xmlns:th=\|xmlns:sec=\|thymeleaf.org"
```

Expected: 첫 명령은 출력 없음(`exit=1`). 두 번째 명령은 출력 없음 — 템플릿 안의 URL 은 Thymeleaf/springsecurity6 xmlns 선언뿐이어야 한다(외부 CSS/JS/폰트 호출 금지).

- [ ] **Step 8: README 갱신**

`README.md` 의 `### 화면을 추가할 때 (2부 작업자용)` 절 제목을 `### 화면을 추가할 때` 로 바꾸고, 그 절의 마지막 항목(`- 참고 구현: CRUD 는 ...`) 앞에 아래 두 항목을 추가한다.

```markdown
- 할당형 PK 테이블(문자열 PK, COMPANY_IDX PK, 복합키)은 `CrudService` 대신 **`AssignedIdCrudService`** 를 상속하고
  `assignedId()` 를 구현한다. 등록은 존재 검사 + `persist` 로만 수행되어, 이미 있는 키를 입력해도 기존 행이 덮어써지지 않고
  "이미 존재하는 값" 오류로 돌아온다(`save()` 는 식별자가 있으면 MERGE 로 동작한다).
- `COMPANY_IDX` 가 없는 CRITERIA·FIDO2 화면은 시스템 메뉴와 같이 **SUPER 전용**이다(설계 3.3).
  `MenuRegistry` 의 `superOnly` 와 `SecurityConfig` 의 SUPER 매처를 함께 맞춘다.
```

기존 항목 중 `할당형 PK 테이블 ... 화면을 만들 때는 persist 기반 삽입 전용 경로로 바꾸는 것을 먼저 검토한다.` 문장은 위 항목으로 대체되었으므로 삭제한다(`toEntity(form)` 항목은 "식별자를 폼 값으로 채우지 않는다. 채우면 `save()` 가 INSERT 가 아니라 MERGE 로 동작해 기존 행을 덮어쓸 수 있다." 까지만 남긴다).

`## 문서` 절을 아래로 교체한다.

```markdown
## 문서

- 설계: `docs/superpowers/specs/2026-09-16-fido-admin-design.md`
- 구현 계획 1부(기반): `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md`
- 구현 계획 2부(업무 화면): `docs/superpowers/plans/2026-09-17-fido-admin-part2-screens.md`
- 구현 계획 3부(시스템 화면·최종 검증): `docs/superpowers/plans/2026-09-17-fido-admin-part3-system.md`
```

Run: `grep -n "AssignedIdCrudService\|part2-screens\|part3-system\|2부 작업자용" README.md`
Expected: 앞의 세 문자열은 각각 1줄 이상, `2부 작업자용` 은 0줄.

- [ ] **Step 9: tasks/todo.md 갱신**

`tasks/todo.md` 끝에 아래 절을 추가하고, 완료한 태스크에 체크한다. Review 표의 값은 Step 1·3·4·5 의 실측값으로 채운다.

```markdown
# FIDO Admin 2부 — 업무 화면

Plan: docs/superpowers/plans/2026-09-17-fido-admin-part2-screens.md
Branch: feature/part2-screens

각 태스크: 구현자 → 스펙 리뷰 → 코드 품질 리뷰 → Codex 리뷰(랜딩 게이트)

- [ ] Task 1: 공통 기반 보강 (AssignedIdCrudService, validate 훅, JsonPretty, CRITERIA·FIDO2 SUPER 전용)
- [ ] Task 2: FDS 정책
- [ ] Task 3: 라이선스
- [ ] Task 4: 운영자 (+잠금 해제)
- [ ] Task 5: 앱 ID
- [ ] Task 6: 앱 서버
- [ ] Task 7: 사용자 (조회+상태 변경)
- [ ] Task 8: 챌린지
- [ ] Task 9: 서명
- [ ] Task 10: 거래 해시
- [ ] Task 11: 거래 확인
- [ ] Task 12: 인증기기 기준
- [ ] Task 13: FIDO2 메타데이터
- [ ] Task 14: FIDO2 크리덴셜 파라미터
- [ ] Task 15: FIDO2 데모 접근코드
- [ ] Task 16: FIDO 로그
- [ ] Task 17: 예외 로그
- [ ] Task 18: 메일/SMS 큐
- [ ] Task 19: 2부 최종 검증

## Review (YYYY-MM-DD)

- clean test: 총 N개, 실패 0, 스킵 0 (통합 테스트 실제 실행 여부: 예/아니오)
- SUPER 경로 18개: 전부 200 (예외 있으면 경로와 코드)
- COMPANY 경로: 200 12개 / 403 6개 (표와 일치 여부)
- 테넌트 격리: /users tester 0건, /logs/fido SN-0003 0건, ?companyIdx=2 변조 무시, /users/3 404
- 브라우저 확인 목록: N/N 통과 (실패 항목과 조치 커밋)
- 외부 자원 호출 0건, JS alert/confirm/prompt 0건

### 3부 시작 전 처리 필요
- (없으면 "없음")

### 수용한 잔여 위험
- (1부 항목 유지 + 새로 발견한 항목)
```

- [ ] **Step 10: 앱 정리와 커밋**

```bash
kill %1 2>/dev/null || pkill -f 'FidoAdminApplication' || true
git add README.md tasks/todo.md
git commit -m "docs: README 와 2부 완료 검증

- 화면 추가 규칙에 AssignedIdCrudService, CRITERIA·FIDO2 SUPER 전용 추가
- 2부 경로·권한·테넌트 격리 실측 기록

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
