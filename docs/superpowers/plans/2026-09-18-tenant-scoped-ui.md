# 테넌트 선택 기반 UI/UX 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 슈퍼관리자가 전체 고객사를 한 번에 보는 모델을, 고객사를 하나 선택하면 그 테넌트가 조회·상세·등록·수정·삭제 전부의 경계가 되는 모델로 바꾼다.

**Architecture:** `TenantContext` 를 정적 유틸에서 스프링 빈으로 바꾸고, `companyIdx()` 의 의미를 "로그인한 사람의 소속"에서 "지금 보고 있는 테넌트"로 바꾼다. 선택값은 `@SessionScope` 빈 `SelectedTenant` 가 보관한다. 그러면 `CrudService` 의 역할 분기가 사라지고, `checkTenant()` 가 SUPER 를 그냥 통과시키던 단건 접근 구멍이 닫힌다. 화면은 TENANT / SYSTEM / PERSONAL 영역으로 나누고 인터셉터가 미선택 상태를 막는다.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Security 6, Thymeleaf, Spring Data JPA, JUnit 5 + AssertJ + Mockito, Gradle

**Spec:** `docs/superpowers/specs/2026-09-18-tenant-scoped-ui-design.md`

## Global Constraints

- **KBFIDO Oracle 스키마를 바꾸지 않는다.** 컬럼·테이블·시퀀스 추가 금지. `docker/init/*.sql` 은 로컬 검증 전용이다.
- **폼 길이 검증은 `@Size` 가 아니라 `@ByteSize`** 를 쓴다 (`tasks/lessons.md` — VARCHAR2 가 BYTE 의미라 한글에서 ORA-12899 가 난다). 비밀번호의 문자 수 검증만 예외다.
- **`@Disabled` 를 커밋에 흘리지 않는다.** 커밋 전 `grep -ho 'skipped="[0-9]*"' build/test-results/test/*.xml` 로 skipped 합계가 0인지 확인한다. "통과했다"는 실행된 테스트에 대해서만 참이다.
- **엔티티를 뷰에 직접 노출하지 않는다** (설계 2.2). 민감 컬럼(`USER_PW`, `PUBKEY`, `CERTIFICATE`, `AMZ_TOKEN`)이 있는 테이블은 `toListView`/`toDetailView` 를 구현한다.
- **테넌트 불일치는 404** 로 처리한다(403 이 아니다 — 존재를 숨긴다).
- **전역 고객사 `IDX 0` 은 테넌트로 선택할 수 없다.** `SignupPolicy.SUPER_COMPANY_IDX` 와 같은 규칙이다.
- 테스트 실행: `./gradlew test`. Docker Oracle 이 없으면 Testcontainers 테스트는 건너뛴다(이는 skipped 카운트에 잡히므로, 위 검증은 Docker 가 뜬 상태에서 한다).

---

## 작업 순서 개요

| Task | 내용 | 산출물 |
|---|---|---|
| 1 | `SelectedTenant` + `NoTenantSelectedException` | 세션 보관소 |
| 2 | `TenantContext` 빈 전환 | 유효 테넌트 단일 출처 |
| 3 | `CrudService` 역할 분기 제거 | **단건 접근 구멍 차단** |
| 4 | `MenuArea` 도입 | 영역 구분 |
| 5 | 선택 화면 + 전환 컨트롤러 | `/select-tenant` |
| 6 | 인터셉터 | 미선택 차단 |
| 7 | 대시보드 생 SQL | `CrudService` 밖 경로 |
| 8 | 감사 로그 대상 테넌트 기록 | 추적성 |
| 9 | `/managers/super` 신설 | 회귀 차단 |
| 10 | 템플릿·컨트롤러 정리 | select 22개 제거 |
| 11 | FDS 정책·시스템 설정 PK 처리 | 예외 화면 2개 |
| 12 | 최종 검증 | 회귀 확인 |

Task 3 이 보안상 핵심이다. Task 1~3 만으로도 격리는 닫히고, 이후는 UI 와 정리다.

---

### Task 1: SelectedTenant 와 NoTenantSelectedException

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/SelectedTenant.java`
- Create: `src/main/java/com/crosscert/fidoadmin/common/NoTenantSelectedException.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/SelectedTenantTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `SelectedTenant.companyIdx()` → `Optional<Long>`
  - `SelectedTenant.select(Long idx)` → `void`, `idx` 가 null 이거나 0 이면 `IllegalArgumentException`
  - `SelectedTenant.clear()` → `void`
  - `NoTenantSelectedException extends RuntimeException`, 기본 생성자

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SelectedTenantTest {

    @Test void 처음에는_선택이_없다() {
        assertThat(new SelectedTenant().companyIdx()).isEmpty();
    }

    @Test void 선택하면_값이_남는다() {
        SelectedTenant t = new SelectedTenant();
        t.select(7L);
        assertThat(t.companyIdx()).contains(7L);
    }

    @Test void 해제하면_비워진다() {
        SelectedTenant t = new SelectedTenant();
        t.select(7L);
        t.clear();
        assertThat(t.companyIdx()).isEmpty();
    }

    /**
     * IDX 0 은 SUPER 를 뜻한다. 테넌트로 선택되면 유효 테넌트가 0 이 되어
     * COMPANY_IDX = 0 인 행(슈퍼관리자 계정 등)이 일반 테넌트 화면에 섞인다.
     */
    @Test void 전역_고객사는_선택할_수_없다() {
        assertThatThrownBy(() -> new SelectedTenant().select(0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void null_은_선택할_수_없다() {
        assertThatThrownBy(() -> new SelectedTenant().select(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*SelectedTenantTest'`
Expected: FAIL — `SelectedTenant` 클래스가 없어 컴파일 오류

- [ ] **Step 3: 최소 구현을 쓴다**

`NoTenantSelectedException.java`:

```java
package com.crosscert.fidoadmin.common;

/**
 * 슈퍼관리자가 고객사를 고르지 않은 채 테넌트 화면의 데이터를 요청했다.
 *
 * <p>미선택을 null 로 표현하지 않는 이유: {@code Specs.eq(attr, null)} 은 술어를
 * 생략하므로, null 이 흘러가면 필터가 조용히 사라져 전체 조회가 된다.
 * 예외로 두면 누락이 선택 화면 리다이렉트로 드러난다.
 */
public class NoTenantSelectedException extends RuntimeException {
    public NoTenantSelectedException() {
        super("고객사가 선택되지 않았습니다");
    }
}
```

`SelectedTenant.java`:

```java
package com.crosscert.fidoadmin.common;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * 슈퍼관리자가 현재 보고 있는 고객사. 세션에 산다.
 *
 * <p>COMPANY 역할은 이 값을 쓰지 않는다. 자기 소속이 곧 유효 테넌트다.
 * 로그아웃의 invalidateHttpSession(true) 가 선택을 지운다.
 *
 * <p>고객사가 실재하는지는 여기서 검사하지 않는다. 그 판정에는 리포지터리가 필요하고,
 * 세션 빈이 영속 계층에 의존하면 테스트와 수명주기가 얽힌다. 호출자
 * (TenantSelectionController)가 검사한 뒤 넘긴다.
 */
@Component
@SessionScope
public class SelectedTenant {

    private Long companyIdx;

    public Optional<Long> companyIdx() { return Optional.ofNullable(companyIdx); }

    /** IDX 0(SUPER)과 null 은 테넌트가 아니므로 거부한다. */
    public void select(Long idx) {
        if (idx == null) throw new IllegalArgumentException("고객사를 선택해야 합니다");
        if (idx == 0L) throw new IllegalArgumentException("전역(IDX 0)은 테넌트로 선택할 수 없습니다");
        this.companyIdx = idx;
    }

    public void clear() { this.companyIdx = null; }
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*SelectedTenantTest'`
Expected: PASS (5개)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/SelectedTenant.java \
        src/main/java/com/crosscert/fidoadmin/common/NoTenantSelectedException.java \
        src/test/java/com/crosscert/fidoadmin/common/SelectedTenantTest.java
git commit -m "feat: 선택된 테넌트를 세션에 보관하는 SelectedTenant 추가"
```

---

### Task 2: TenantContext 를 빈으로 바꾸고 유효 테넌트를 준다

정적 유틸이라 세션 빈에 닿을 수 없다. 빈으로 바꾼다. 호출부는 `src/main` 38곳 / 22파일이다. **이 태스크는 컴파일이 깨진 상태로 진행되며, 호출부를 전부 고쳐야 끝난다.**

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/common/TenantContext.java` (전면 재작성)
- Modify: 아래 22개 파일의 호출부
- Test: `src/test/java/com/crosscert/fidoadmin/common/TenantContextTest.java` (재작성)

**Interfaces:**
- Consumes: `SelectedTenant.companyIdx()` (Task 1)
- Produces:
  - `TenantContext` 는 `@Component`. 생성자 `TenantContext(SelectedTenant selected)`
  - `current()` → `Optional<ManagerUserDetails>` (인스턴스 메서드로 바뀜)
  - `require()` → `ManagerUserDetails`
  - `companyIdx()` → `Long`, 미선택 SUPER 면 `NoTenantSelectedException`
  - `hasTenant()` → `boolean`
  - **`isSuper()` 는 삭제된다.** 계정이 SUPER 인지는 `require().isSuper()` 로 묻는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextTest {

    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void COMPANY_는_자기_소속이_유효_테넌트다() {
        login(5L);
        assertThat(tenant.companyIdx()).isEqualTo(5L);
        assertThat(tenant.hasTenant()).isTrue();
    }

    /** COMPANY 는 세션 선택값을 읽지 않는다. 다른 값이 들어 있어도 자기 소속이다. */
    @Test void COMPANY_는_세션_선택을_무시한다() {
        login(5L);
        selected.select(9L);
        assertThat(tenant.companyIdx()).isEqualTo(5L);
    }

    @Test void SUPER_는_선택한_고객사가_유효_테넌트다() {
        login(0L);
        selected.select(9L);
        assertThat(tenant.companyIdx()).isEqualTo(9L);
        assertThat(tenant.hasTenant()).isTrue();
    }

    /** 미선택을 null 로 돌려주면 필터가 조용히 사라진다. 예외여야 한다. */
    @Test void SUPER_가_미선택이면_예외다() {
        login(0L);
        assertThat(tenant.hasTenant()).isFalse();
        assertThatThrownBy(() -> tenant.companyIdx())
            .isInstanceOf(NoTenantSelectedException.class);
    }

    @Test void 로그인이_없으면_require_가_실패한다() {
        assertThatThrownBy(() -> tenant.require()).isInstanceOf(IllegalStateException.class);
        assertThat(tenant.current()).isEmpty();
        assertThat(tenant.hasTenant()).isFalse();
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*TenantContextTest'`
Expected: FAIL — 생성자가 없고 메서드가 정적이라 컴파일 오류

- [ ] **Step 3: TenantContext 를 재작성한다**

```java
package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 로그인 운영자와 <b>유효 테넌트</b>를 준다.
 *
 * <p>유효 테넌트는 "지금 보고 있는 고객사"다. COMPANY 는 항상 자기 소속이고,
 * SUPER 는 세션에서 고른 고객사다. 이 구분 덕분에 CrudService 는 역할을 묻지 않는다.
 *
 * <p>isSuper() 를 두지 않는다. "지금 보는 테넌트가 전역인가"는 이 모델에서
 * 성립하지 않는 질문이고, 남겨 두면 예전 의미(계정이 SUPER 인가)로 오용된다.
 * 계정 성질을 물어야 하는 곳은 {@code require().isSuper()} 를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TenantContext {

    private final SelectedTenant selected;

    public Optional<ManagerUserDetails> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ManagerUserDetails user)) return Optional.empty();
        return Optional.of(user);
    }

    public ManagerUserDetails require() {
        return current().orElseThrow(() -> new IllegalStateException("로그인 사용자가 없습니다"));
    }

    /** 유효 테넌트. 미선택 SUPER 는 값이 없으므로 예외다. */
    public Long companyIdx() {
        ManagerUserDetails user = require();
        if (!user.isSuper()) return user.getCompanyIdx();
        return selected.companyIdx().orElseThrow(NoTenantSelectedException::new);
    }

    /** 유효 테넌트가 있는가. 인터셉터와 레이아웃이 분기에 쓴다. */
    public boolean hasTenant() {
        return current().map(u -> !u.isSuper() || selected.companyIdx().isPresent()).orElse(false);
    }
}
```

- [ ] **Step 4: 호출부 22개 파일을 고친다**

각 파일에서 `TenantContext.x()` 정적 호출을 주입받은 인스턴스 호출로 바꾼다. 이미 스프링 빈이므로 생성자 파라미터에 `TenantContext tenant` 를 더한다(`@RequiredArgsConstructor` 를 쓰는 클래스는 `private final TenantContext tenant;` 필드만 더하면 된다).

대상 파일과 조치:

| 파일 | 조치 |
|---|---|
| `auth/PasswordChangeController.java` | `TenantContext.require()` → `tenant.require()` |
| `signup/SignupService.java` | 주석의 `TenantContext.companyIdx()` 언급만 수정 |
| `fido/web/{Appid,Appserver,User,Challenge,Sign,Transactionhash,TransactionConfirmation}Controller.java` | `if (TenantContext.isSuper()) model.addAttribute("companies", ...)` — **Task 10 에서 통째로 지운다. 여기서는 `tenant.require().isSuper()` 로만 바꿔 컴파일을 통과시킨다** |
| `dashboard/DashboardController.java` | 동일 + `TenantContext.isSuper() ? search.getCompanyIdx() : TenantContext.companyIdx()` → `tenant.companyIdx()` |
| `dashboard/StatisticsQueryService.java` | Task 7 에서 본격 처리. 여기서는 `tenant.companyIdx()` 로 바꾼다 |
| `manager/service/ManagerService.java` | `TenantContext.require().getIdx()` → `tenant.require().getIdx()`. 생성자에 `TenantContext` 추가 |
| `audit/AuditLogger.java` | `TenantContext.current()` → `tenant.current()`. Task 8 에서 본격 처리 |
| `log/web/{AuditLog,Mailing,ExceptionLog,FidoLog}Controller.java` | `isSuper()` → `tenant.require().isSuper()` |
| `company/web/FdsPolicyController.java` | 동일 (Task 11 에서 본격 처리) |
| `company/web/LicenseController.java` | 동일 |
| `company/service/CompanyService.java` | `TenantContext.require().getIdx()` → `tenant.require().getIdx()` |
| `company/service/CompanyLookup.java` | `TenantContext.isSuper()` → `tenant.require().isSuper()`, `TenantContext.companyIdx()` → `tenant.require().getCompanyIdx()` — **유효 테넌트가 아니라 계정 소속을 봐야 한다.** 이 클래스가 선택 목록의 출처이므로 유효 테넌트를 보면 자기 자신을 참조하게 된다 |
| `common/CrudService.java` | Task 3 에서 처리. 여기서는 생성자에 `TenantContext tenant` 를 추가한다 |
| `common/AssignedIdCrudService.java` | `super(repository, audit)` → `super(repository, audit, tenant)`. 생성자에 `TenantContext tenant` 파라미터를 더한다 |

`CrudService` 생성자 변경은 **중간 클래스 `AssignedIdCrudService` 를 거쳐** 하위 28개 서비스에 파급된다. 할당형 PK 서비스(`FdsPolicyService`, `SystemPropService`, `Fido2DemoAccessCodeService`, `ErrorCodeService`, `FidoClientService` 등)는 `AssignedIdCrudService` 를 상속하므로 그쪽 생성자를 먼저 고쳐야 한다. 기계적이므로 컴파일러를 따라가며 고친다.

`CrudService` 의 새 생성자:

```java
    protected final TenantContext tenant;

    protected CrudService(AdminRepository<E, ID> repository, AuditLogger audit, TenantContext tenant) {
        this.repository = repository;
        this.audit = audit;
        this.tenant = tenant;
    }
```

- [ ] **Step 5: 전체 컴파일과 테스트를 돌린다**

Run: `./gradlew test`
Expected: 컴파일 성공. `CrudServiceTest` 등 일부 실패는 **예상된 것**이다(Task 3 에서 고친다). `TenantContextTest` 5개는 PASS 여야 한다.

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: TenantContext 를 빈으로 바꾸고 유효 테넌트 개념을 도입한다

isSuper() 를 삭제해 '계정이 SUPER 인가'와 '지금 보는 테넌트'가 섞이지 않게 한다.
CrudService 의 동작 변경은 다음 커밋에서 한다."
```

---

### Task 3: CrudService 의 역할 분기를 제거한다 — 보안 핵심

**이 태스크가 단건 접근 구멍을 닫는다.** 지금은 SUPER 가 `/users/12345` 를 직접 입력해 임의 테넌트의 행을 열고 수정·삭제할 수 있다.

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/common/CrudService.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/CrudServiceTest.java`

**Interfaces:**
- Consumes: `TenantContext.companyIdx()` (Task 2)
- Produces: `CrudService.search/get/create/update/delete` 의 동작이 역할과 무관해진다. 시그니처는 그대로다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`CrudServiceTest` 의 셋업을 유효 테넌트 기반으로 바꾸고, 뒤집히는 단언을 고친다.

```java
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);

    CrudService<CcfaLicense, Long, SearchForm> service = new CrudService<>(repo, audit, tenant) {
        // ... 기존 훅 구현 그대로 ...
    };

    /** SUPER 로 로그인하고 고객사를 선택한다. */
    private void loginSuperSelecting(long companyIdx) {
        login(0L);
        selected.select(companyIdx);
    }
```

새로 넣는 테스트:

```java
    /**
     * 기존에는 SUPER 면 필터가 없어 전체가 조회됐다(이 단언이 isNull() 이었다).
     * 이제는 선택한 고객사로 걸린다.
     */
    @Test void SUPER_는_선택한_고객사로_필터된다() {
        loginSuperSelecting(9L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(java.util.List.of()));
        ArgumentCaptor<Specification<CcfaLicense>> captor = ArgumentCaptor.forClass(Specification.class);

        service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx")));

        verify(repo).findAll(captor.capture(), any(PageRequest.class));
        assertThat(companyIdxEqualsIn(captor.getValue())).isEqualTo(9L);
    }

    /**
     * 이번 변경의 핵심. 예전에는 checkTenant() 가 isSuper() 면 그냥 통과해
     * SUPER 가 URL 로 임의 테넌트의 행을 열 수 있었다.
     */
    @Test void SUPER_가_다른_테넌트의_행을_열면_거부된다() {
        loginSuperSelecting(9L);
        when(repo.findById(1L)).thenReturn(Optional.of(license(1L, 3L)));

        assertThatThrownBy(() -> service.get(1L))
            .isInstanceOf(TenantMismatchException.class);
    }

    @Test void SUPER_가_선택한_테넌트의_행은_열린다() {
        loginSuperSelecting(9L);
        when(repo.findById(1L)).thenReturn(Optional.of(license(1L, 9L)));

        assertThat(service.get(1L).getCompanyIdx()).isEqualTo(9L);
    }

    /** 등록은 역할과 무관하게 유효 테넌트 소유가 된다. */
    @Test void SUPER_가_만든_행은_선택한_테넌트_소유다() {
        loginSuperSelecting(9L);
        when(repo.save(any(CcfaLicense.class))).thenAnswer(i -> i.getArgument(0));

        CcfaLicense created = service.create(license(0L, 3L));

        assertThat(created.getCompanyIdx()).isEqualTo(9L);
    }

    /** 미선택 SUPER 는 조회 자체가 성립하지 않는다. */
    @Test void 미선택_SUPER_는_조회할_수_없다() {
        login(0L);
        assertThatThrownBy(() -> service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(NoTenantSelectedException.class);
    }
```

기존 테스트 중 **뒤집히는 것**: `assertThat(companyIdxEqualsIn(captor.getValue())).isNull()` 로 "SUPER 는 필터 없음"을 확인하던 테스트를 위 `SUPER_는_선택한_고객사로_필터된다` 로 대체한다.

기존 테스트 중 **그대로 통과해야 하는 것**(회귀 방지): COMPANY 역할의 `getRejectsOtherTenant`, 자기 테넌트 조회, 등록 시 소속 강제 덮어쓰기.

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*CrudServiceTest'`
Expected: FAIL — `SUPER_가_다른_테넌트의_행을_열면_거부된다` 가 예외 없이 통과해 버린다(구멍이 아직 열려 있다)

- [ ] **Step 3: CrudService 를 고친다**

`search()`:

```java
    @Transactional(readOnly = true)
    public Page<E> search(S form, Pageable pageable) {
        requireSuperForGlobalTable();
        Specification<E> spec = toSpecification(form);
        String attr = companyIdxAttribute();
        if (attr != null) {
            // 역할을 묻지 않는다. 유효 테넌트가 곧 경계다.
            spec = Specs.all(spec, Specs.eq(attr, tenant.companyIdx()));
        }
        return repository.findAll(spec == null ? Specs.all() : spec, pageable);
    }
```

`checkTenant()` — `isSuper()` 통과를 없앤다:

```java
    protected void checkTenant(E e) {
        if (companyIdxAttribute() == null) return;
        Long owner = companyIdxOf(e);
        if (owner == null || !owner.equals(tenant.companyIdx())) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }
```

`create()` / `update()` — 조건 없는 덮어쓰기:

```java
        if (companyIdxAttribute() != null) {
            setCompanyIdx(entity, tenant.companyIdx());
        }
```

`requireSuperForGlobalTable()` 은 계정 성질을 보므로 `tenant.require().isSuper()` 로 바꾼다:

```java
    protected void requireSuperForGlobalTable() {
        if (companyIdxAttribute() == null && !tenant.require().isSuper()) {
            throw new org.springframework.security.access.AccessDeniedException(
                tableName() + " 은 최고 관리자 전용입니다");
        }
    }
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*CrudServiceTest'`
Expected: PASS 전부

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/CrudService.java \
        src/test/java/com/crosscert/fidoadmin/common/CrudServiceTest.java
git commit -m "fix: SUPER 가 URL 로 임의 테넌트의 행에 접근하던 경로를 막는다

checkTenant() 가 isSuper() 면 통과시키던 분기를 없앴다. 목록에서 고객사를 골라도
/users/{id} 를 직접 입력하면 다른 테넌트의 행이 열리고 수정·삭제까지 됐다.
search/create/update 도 역할 분기 없이 유효 테넌트만 본다."
```

---

### Task 4: MenuArea 로 화면을 영역으로 나눈다

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/MenuArea.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuItem.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum MenuArea { TENANT, SYSTEM, PERSONAL }`
  - `MenuItem(MenuArea area, String group, String title, String href, boolean superOnly, String icon)`
  - `MenuRegistry.itemsFor(boolean isSuper)` → 기존과 동일
  - `MenuRegistry.areaOf(String path)` → `MenuArea`, 가장 긴 접두사 일치. 일치가 없으면 `SYSTEM`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test void 테넌트_영역_화면은_16개다() {
        long tenant = MenuRegistry.ALL.stream().filter(m -> m.area() == MenuArea.TENANT).count();
        assertThat(tenant).isEqualTo(16);
    }

    @Test void 시스템_영역은_전부_superOnly_다() {
        assertThat(MenuRegistry.ALL.stream()
            .filter(m -> m.area() == MenuArea.SYSTEM))
            .allMatch(MenuItem::superOnly);
    }

    /** 가입 승인은 COMPANY_IDX = -1 인 미배정 계정을 다루므로 테넌트 영역이 아니다. */
    @Test void 가입_승인은_시스템_영역이다() {
        assertThat(area("/signups")).isEqualTo(MenuArea.SYSTEM);
    }

    /** 라이선스·운영자는 SUPER 전용이지만 실제 테넌트 데이터다. */
    @Test void 라이선스와_운영자는_테넌트_영역이다() {
        assertThat(area("/licenses")).isEqualTo(MenuArea.TENANT);
        assertThat(area("/managers")).isEqualTo(MenuArea.TENANT);
    }

    @Test void 하위_경로는_가장_긴_접두사로_판정한다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/users/123/edit")).isEqualTo(MenuArea.TENANT);
        assertThat(r.areaOf("/system/menus/5")).isEqualTo(MenuArea.SYSTEM);
    }

    /** /managers 는 TENANT 지만 /managers/super 는 SYSTEM 이다. 더 긴 쪽이 이긴다. */
    @Test void 슈퍼관리자_계정_화면은_시스템_영역이다() {
        assertThat(new MenuRegistry().areaOf("/managers/super")).isEqualTo(MenuArea.SYSTEM);
        assertThat(new MenuRegistry().areaOf("/managers/super/3")).isEqualTo(MenuArea.SYSTEM);
    }

    @Test void 내_비밀번호_변경은_개인_영역이다() {
        assertThat(new MenuRegistry().areaOf("/me/password")).isEqualTo(MenuArea.PERSONAL);
    }

    private MenuArea area(String href) {
        return MenuRegistry.ALL.stream().filter(m -> m.href().equals(href))
            .findFirst().orElseThrow().area();
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*MenuRegistryTest'`
Expected: FAIL — `MenuArea` 가 없어 컴파일 오류

- [ ] **Step 3: 구현한다**

`MenuArea.java`:

```java
package com.crosscert.fidoadmin.common;

/**
 * 화면이 속한 영역.
 *
 * <p>기존 {@code superOnly} 는 "SUPER 만 보는 화면"과 "COMPANY_IDX 가 없는 전역
 * 테이블"을 뭉뚱그렸다. 테넌트 선택 모델에서는 이 둘이 갈라진다 — 라이선스·운영자는
 * SUPER 전용이면서 테넌트 데이터다. 그래서 영역은 superOnly 와 직교한다.
 */
public enum MenuArea {
    /** 테넌트 데이터. 고객사를 선택해야 열린다. */
    TENANT,
    /** 전역 테이블과 테넌트에 속하지 않는 워크플로. 선택과 무관하다. */
    SYSTEM,
    /** 로그인한 본인에 대한 화면. 선택과 무관하다. */
    PERSONAL
}
```

`MenuItem.java`:

```java
package com.crosscert.fidoadmin.common;

/**
 * 사이드바 메뉴 한 줄. {@code icon} 은 Bootstrap Icons 클래스명(예: {@code bi-people})으로,
 * 템플릿이 {@code <i class="bi ..."></i>} 에 그대로 넣는다.
 *
 * <p>{@code area} 는 테넌트 선택이 필요한지를, {@code superOnly} 는 역할을 가린다.
 * 둘은 직교한다(라이선스 = TENANT + superOnly).
 */
public record MenuItem(MenuArea area, String group, String title, String href,
                       boolean superOnly, String icon) {}
```

`MenuRegistry.ALL` 은 30개 항목 전부에 영역을 붙인다. 배정은 스펙 3.1 을 따른다:

```java
    static final List<MenuItem> ALL = List.of(
        new MenuItem(MenuArea.TENANT, "대시보드", "통계", "/", false, "bi-speedometer2"),
        new MenuItem(MenuArea.SYSTEM, "고객사", "고객사", "/companies", true, "bi-building"),
        new MenuItem(MenuArea.TENANT, "고객사", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem(MenuArea.TENANT, "고객사", "라이선스", "/licenses", true, "bi-award"),
        new MenuItem(MenuArea.TENANT, "운영자", "운영자", "/managers", true, "bi-person-badge"),
        new MenuItem(MenuArea.SYSTEM, "운영자", "가입 승인", "/signups", true, "bi-person-check"),
        new MenuItem(MenuArea.PERSONAL, "운영자", "내 비밀번호 변경", "/me/password", false, "bi-key"),
        new MenuItem(MenuArea.TENANT, "FIDO", "앱 ID", "/appids", false, "bi-app-indicator"),
        new MenuItem(MenuArea.TENANT, "FIDO", "앱 서버", "/appservers", false, "bi-hdd-network"),
        new MenuItem(MenuArea.TENANT, "FIDO", "사용자", "/users", false, "bi-people"),
        new MenuItem(MenuArea.TENANT, "FIDO", "챌린지", "/challenges", false, "bi-patch-question"),
        new MenuItem(MenuArea.TENANT, "FIDO", "서명", "/signs", false, "bi-pen"),
        new MenuItem(MenuArea.TENANT, "FIDO", "거래 해시", "/transaction-hashes", false, "bi-hash"),
        new MenuItem(MenuArea.TENANT, "FIDO", "거래 확인", "/transaction-confirmations", false, "bi-check2-square"),
        new MenuItem(MenuArea.SYSTEM, "FIDO", "인증기기 기준", "/criteria", true, "bi-fingerprint"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "메타데이터", "/fido2/metadata", true, "bi-card-list"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true, "bi-shield-lock"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "데모 접근코드", "/fido2/demo-access-codes", true, "bi-ticket-perforated"),
        new MenuItem(MenuArea.TENANT, "로그", "FIDO 로그", "/logs/fido", false, "bi-journal-text"),
        new MenuItem(MenuArea.TENANT, "로그", "감사 로그", "/logs/audit", false, "bi-clipboard-check"),
        new MenuItem(MenuArea.TENANT, "로그", "예외 로그", "/logs/exceptions", false, "bi-exclamation-triangle"),
        new MenuItem(MenuArea.TENANT, "로그", "메일/SMS 큐", "/logs/mailing", false, "bi-envelope"),
        new MenuItem(MenuArea.TENANT, "시스템", "시스템 설정", "/system/props", true, "bi-sliders"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "시스템 정보", "/system/info", true, "bi-info-circle"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "에러 코드", "/system/error-codes", true, "bi-bug"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "FIDO 서버", "/system/fido-clients", true, "bi-server"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "어드민 기준", "/system/criteria", true, "bi-ui-checks"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "메뉴 정의", "/system/menus", true, "bi-list-nested"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "코드 그룹/코드", "/system/options", true, "bi-tags"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "필드 정의", "/system/fields", true, "bi-input-cursor-text"));
```

`areaOf` 를 더한다:

```java
    /**
     * 경로가 속한 영역. 하위 경로(/users/123/edit)는 가장 긴 접두사로 판정한다.
     *
     * <p>가장 긴 접두사여야 하는 이유: /managers 는 TENANT 지만 /managers/super 는
     * SYSTEM 이다. 짧은 쪽이 먼저 맞으면 슈퍼관리자 계정 화면이 테넌트 선택을 요구한다.
     *
     * <p>메뉴에 없는 경로는 SYSTEM 으로 본다. 테넌트 화면을 메뉴에 등록하지 않은 채
     * 추가하면 필터가 걸리지 않는데, 그것은 CrudService 가 유효 테넌트로 막는다.
     */
    public MenuArea areaOf(String path) {
        if (path == null) return MenuArea.SYSTEM;
        return ALL.stream()
            .filter(m -> path.equals(m.href()) || path.startsWith(withSlash(m.href())))
            .max(java.util.Comparator.comparingInt(m -> m.href().length()))
            .map(MenuItem::area)
            .orElse(MenuArea.SYSTEM);
    }

    /** "/" 는 그대로, 나머지는 "/users" → "/users/" 로 만들어 /usersfoo 오탐을 막는다. */
    private static String withSlash(String href) {
        return href.endsWith("/") ? href : href + "/";
    }
```

**주의**: `/` 는 `withSlash` 가 `/` 그대로이므로 모든 경로가 `startsWith("/")` 로 맞는다. 하지만 길이 1 이라 항상 가장 짧으므로 다른 일치가 있으면 지고, 없을 때만 대시보드(TENANT)가 된다. 이것이 의도한 동작이다.

`/managers/super` 는 Task 9 에서 `ALL` 에 추가한다. 위 테스트 `슈퍼관리자_계정_화면은_시스템_영역이다` 는 **Task 9 까지 실패한다** — Task 4 에서는 이 테스트를 `@Disabled` 가 아니라 **Task 9 에 작성**한다(lessons.md: 진단용 @Disabled 를 커밋에 흘리지 않는다).

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*MenuRegistryTest'`
Expected: PASS (슈퍼관리자 계정 테스트 제외 — Task 9 에서 추가)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/MenuArea.java \
        src/main/java/com/crosscert/fidoadmin/common/MenuItem.java \
        src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
        src/test/java/com/crosscert/fidoadmin/common/MenuRegistryTest.java
git commit -m "feat: 화면을 TENANT/SYSTEM/PERSONAL 영역으로 나눈다"
```

---

### Task 5: 고객사 선택 화면과 전환 컨트롤러

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/TenantSelectionController.java`
- Create: `src/main/resources/templates/tenant/select.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java` (`listPathFor` 추가)
- Modify: `src/main/resources/static/js/admin.js` (선택 화면 이름 검색)
- Test: `src/test/java/com/crosscert/fidoadmin/common/TenantSelectionControllerWebTest.java`

**Interfaces:**
- Consumes: `SelectedTenant.select()` (Task 1), `TenantContext.require()` (Task 2),
  `MenuRegistry.areaOf()` 와 `MenuArea` (Task 4), `CompanyLookup.all()`,
  `CcfaCompanyRepository.existsById()`
- Produces:
  - `GET /select-tenant` → `tenant/select` 뷰. COMPANY 계정은 `redirect:/`
  - `POST /select-tenant` (파라미터 `companyIdx`, `returnTo`) → 선택 후 리다이렉트
  - `MenuRegistry.listPathFor(String path)` → `String`. 경로가 속한 메뉴의 목록 경로.
    일치가 없으면 `"/"`. Task 6 은 쓰지 않지만 같은 `withSlash` 헬퍼를 공유한다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
package com.crosscert.fidoadmin.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// (프로젝트의 기존 *WebTest 가 쓰는 @WebMvcTest 설정·인증 헬퍼를 그대로 따른다)
class TenantSelectionControllerWebTest {

    @Test void SUPER_는_선택_화면을_본다() throws Exception {
        mvc.perform(get("/select-tenant").with(superUser()))
            .andExpect(status().isOk())
            .andExpect(view().name("tenant/select"));
    }

    /** COMPANY 는 고를 것이 없다. */
    @Test void COMPANY_는_대시보드로_보낸다() throws Exception {
        mvc.perform(get("/select-tenant").with(companyUser(5L)))
            .andExpect(redirectedUrl("/"));
    }

    @Test void 선택하면_돌아갈_곳으로_리다이렉트한다() throws Exception {
        mvc.perform(post("/select-tenant").with(superUser()).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/users"));
    }

    /**
     * 상세 화면의 ID 는 이전 테넌트의 것이라 새 테넌트에서는 404 다.
     * 목록으로 절상해서 보낸다.
     */
    @Test void 상세_화면에서_전환하면_목록으로_보낸다() throws Exception {
        mvc.perform(post("/select-tenant").with(superUser()).with(csrf())
                .param("companyIdx", "9").param("returnTo", "/users/123"))
            .andExpect(redirectedUrl("/users"));
    }

    /** 외부 URL 로 튕기지 않게 한다(오픈 리다이렉트 차단). */
    @Test void 외부_주소로는_리다이렉트하지_않는다() throws Exception {
        mvc.perform(post("/select-tenant").with(superUser()).with(csrf())
                .param("companyIdx", "9").param("returnTo", "https://evil.example/x"))
            .andExpect(redirectedUrl("/"));
    }

    @Test void 존재하지_않는_고객사는_거부한다() throws Exception {
        mvc.perform(post("/select-tenant").with(superUser()).with(csrf())
                .param("companyIdx", "99999").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/select-tenant"));
    }

    /** IDX 0 은 SUPER 를 뜻하므로 테넌트가 될 수 없다. */
    @Test void 전역_고객사는_거부한다() throws Exception {
        mvc.perform(post("/select-tenant").with(superUser()).with(csrf())
                .param("companyIdx", "0").param("returnTo", "/users"))
            .andExpect(redirectedUrl("/select-tenant"));
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*TenantSelectionControllerWebTest'`
Expected: FAIL — 컨트롤러가 없어 404

- [ ] **Step 3: 컨트롤러를 만든다**

```java
package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 고객사 선택. 슈퍼관리자가 로그인 직후 처음 만나는 화면이다.
 *
 * <p>고객사 존재 검사에 CompanyLookup 이 아니라 리포지터리를 직접 쓴다.
 * CompanyLookup 은 로그인 사용자 기준으로 결과를 가리는 화면용 조회라
 * 없는 고객사와 볼 수 없는 고객사를 구분하지 못한다
 * (SignupService.approve() 가 같은 이유로 리포지터리를 쓴다).
 */
@Controller
@RequiredArgsConstructor
public class TenantSelectionController {

    private final SelectedTenant selected;
    private final TenantContext tenant;
    private final CompanyLookup companies;
    private final CcfaCompanyRepository repository;
    private final MenuRegistry menus;

    @GetMapping("/select-tenant")
    public String selectForm(Model model) {
        if (!tenant.require().isSuper()) return "redirect:/";
        model.addAttribute("companies", companies.all().stream()
            .filter(c -> c.getIdx() != null && c.getIdx() != 0L)
            .toList());
        model.addAttribute("systemMenus", menus.itemsFor(true).stream()
            .filter(m -> m.area() == MenuArea.SYSTEM)
            .toList());
        return "tenant/select";
    }

    @PostMapping("/select-tenant")
    public String select(@RequestParam(required = false) Long companyIdx,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirect) {
        if (!tenant.require().isSuper()) return "redirect:/";
        if (companyIdx == null || companyIdx == 0L || !repository.existsById(companyIdx)) {
            redirect.addFlashAttribute("flashError", "선택할 수 없는 고객사입니다.");
            return "redirect:/select-tenant";
        }
        selected.select(companyIdx);
        return "redirect:" + safeReturnTo(returnTo);
    }

    /**
     * 돌아갈 경로를 정한다.
     *
     * <p>슬래시 하나로 시작하는 내부 경로만 받는다("//host" 와 "https://host" 는 외부다).
     * 상세·수정 경로(/users/123)는 그 ID 가 이전 테넌트의 것이라 새 테넌트에서 404 가
     * 되므로 메뉴에 등록된 목록 경로로 절상한다.
     */
    private String safeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) return "/";
        if (menus.areaOf(returnTo) != MenuArea.TENANT) return "/";
        return menus.listPathFor(returnTo);
    }
}
```

`MenuRegistry` 에 `listPathFor` 를 더한다:

```java
    /**
     * 경로가 속한 메뉴의 목록 경로. "/users/123/edit" → "/users".
     * 일치하는 메뉴가 없으면 "/" 다.
     */
    public String listPathFor(String path) {
        if (path == null) return "/";
        return ALL.stream()
            .filter(m -> path.equals(m.href()) || path.startsWith(withSlash(m.href())))
            .max(java.util.Comparator.comparingInt(m -> m.href().length()))
            .map(MenuItem::href)
            .orElse("/");
    }
```

- [ ] **Step 4: 선택 화면 템플릿을 만든다**

`src/main/resources/templates/tenant/select.html`:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>고객사 선택</title></head>
<body>
<main>
  <h1 class="h4 mb-1">고객사 선택</h1>
  <p class="text-secondary small mb-3">작업할 고객사를 선택하세요. 선택한 고객사의 데이터만 조회·수정합니다.</p>

  <div class="mb-3">
    <input type="text" id="tenantFilter" class="form-control form-control-sm"
           style="max-width:320px" placeholder="고객사 이름 검색" autocomplete="off">
  </div>

  <div class="row g-2">
    <div class="col-md-4" th:each="c : ${companies}" data-tenant-card
         th:attr="data-name=${c.companyName}">
      <form method="post" th:action="@{/select-tenant}" class="m-0">
        <input type="hidden" name="companyIdx" th:value="${c.idx}">
        <input type="hidden" name="returnTo" value="/">
        <button type="submit" class="btn btn-outline-secondary w-100 text-start p-3">
          <span class="d-block fw-semibold" th:text="${c.companyName}">고객사</span>
          <span class="d-block small text-secondary" th:text="|IDX ${c.idx}|">IDX</span>
        </button>
      </form>
    </div>
    <div class="col-12" th:if="${#lists.isEmpty(companies)}">
      <div class="text-center text-secondary py-4">선택할 수 있는 고객사가 없습니다.</div>
    </div>
  </div>

  <hr class="my-4">
  <h2 class="h6 text-secondary">시스템 관리</h2>
  <p class="small text-secondary">고객사 선택과 무관한 전역 화면입니다.</p>
  <div class="d-flex flex-wrap gap-2">
    <a th:each="m : ${systemMenus}" th:href="@{${m.href}}" class="btn btn-sm btn-outline-secondary">
      <i class="bi" th:classappend="${m.icon}" aria-hidden="true"></i>
      <span th:text="${m.title}">메뉴</span>
    </a>
  </div>
</main>
</body>
</html>
```

`src/main/resources/static/js/admin.js` 끝에 검색 동작을 더한다:

```javascript
// 고객사 선택 화면의 이름 검색. 카드가 많아도 서버를 다시 부르지 않는다.
(function () {
  var input = document.getElementById('tenantFilter');
  if (!input) return;
  input.addEventListener('input', function () {
    var q = input.value.trim().toLowerCase();
    document.querySelectorAll('[data-tenant-card]').forEach(function (card) {
      var name = (card.getAttribute('data-name') || '').toLowerCase();
      card.style.display = name.indexOf(q) === -1 ? 'none' : '';
    });
  });
})();
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests '*TenantSelectionControllerWebTest'`
Expected: PASS (7개)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/TenantSelectionController.java \
        src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
        src/main/resources/templates/tenant/select.html \
        src/main/resources/static/js/admin.js \
        src/test/java/com/crosscert/fidoadmin/common/TenantSelectionControllerWebTest.java
git commit -m "feat: 고객사 선택 화면과 전환 경로를 추가한다"
```

---

### Task 6: 인터셉터로 미선택 상태를 막는다

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/TenantSelectionInterceptor.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/WebMvcConfig.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/GlobalExceptionHandler.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/TenantSelectionInterceptorWebTest.java`

**Interfaces:**
- Consumes: `MenuRegistry.areaOf()` (Task 4), `TenantContext.hasTenant()` (Task 2)
- Produces: TENANT 영역 요청에 선택이 없으면 `/select-tenant` 로 리다이렉트

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test void 미선택_SUPER_가_테넌트_화면에_가면_선택_화면으로_보낸다() throws Exception {
        mvc.perform(get("/users").with(superUser()))
            .andExpect(redirectedUrl("/select-tenant"));
    }

    @Test void 선택한_SUPER_는_테넌트_화면을_본다() throws Exception {
        mvc.perform(get("/users").with(superUserSelecting(9L)))
            .andExpect(status().isOk());
    }

    /** 시스템 영역은 선택과 무관하다. */
    @Test void 미선택_SUPER_도_시스템_화면은_본다() throws Exception {
        mvc.perform(get("/companies").with(superUser()))
            .andExpect(status().isOk());
    }

    @Test void 미선택_SUPER_도_선택_화면과_로그아웃은_된다() throws Exception {
        mvc.perform(get("/select-tenant").with(superUser())).andExpect(status().isOk());
        mvc.perform(get("/me/password").with(superUser())).andExpect(status().isOk());
    }

    /** COMPANY 는 항상 유효 테넌트가 있으므로 인터셉터에 걸리지 않는다. */
    @Test void COMPANY_는_영향을_받지_않는다() throws Exception {
        mvc.perform(get("/users").with(companyUser(5L)))
            .andExpect(status().isOk());
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*TenantSelectionInterceptorWebTest'`
Expected: FAIL — 첫 테스트가 200 을 받는다(인터셉터 없음)

- [ ] **Step 3: 인터셉터를 만든다**

```java
package com.crosscert.fidoadmin.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 테넌트 영역 화면은 고객사가 선택된 뒤에만 열린다.
 *
 * <p>이것은 편의이지 방어선이 아니다. 실제 격리는 CrudService 가 유효 테넌트로
 * 건다. 인터셉터가 없어도 데이터는 새지 않고 NoTenantSelectedException 이 날 뿐이다.
 * 여기서 막는 이유는 운영자에게 오류 대신 선택 화면을 보여 주기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class TenantSelectionInterceptor implements HandlerInterceptor {

    private final TenantContext tenant;
    private final MenuRegistry menus;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (tenant.current().isEmpty()) return true;            // 인증 전 — 시큐리티가 처리한다
        if (menus.areaOf(request.getRequestURI()) != MenuArea.TENANT) return true;
        if (tenant.hasTenant()) return true;
        response.sendRedirect(request.getContextPath() + "/select-tenant");
        return false;
    }
}
```

`WebMvcConfig` 에 등록한다:

```java
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantSelectionInterceptor tenantSelection;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantSelection)
            .excludePathPatterns("/login", "/logout", "/signup", "/select-tenant",
                "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**",
                "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png");
    }

    // addViewControllers, errorPages 는 그대로
}
```

`GlobalExceptionHandler` 에 이중 방어를 더한다:

```java
    /**
     * 인터셉터가 놓친 경로에서 유효 테넌트를 요구했다. 500 대신 선택 화면으로 보낸다.
     *
     * <p>여기 걸리는 경로가 있다면 MenuRegistry 에 등록되지 않은 테넌트 화면이라는
     * 뜻이므로 로그를 남긴다.
     */
    @ExceptionHandler(NoTenantSelectedException.class)
    public String noTenant(NoTenantSelectedException e, HttpServletRequest request) {
        log.warn("테넌트 미선택 상태로 테넌트 데이터를 요청했다. 인터셉터가 놓친 경로일 수 있다: {}",
            request.getRequestURI());
        return "redirect:/select-tenant";
    }
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*TenantSelectionInterceptorWebTest'`
Expected: PASS (5개)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/TenantSelectionInterceptor.java \
        src/main/java/com/crosscert/fidoadmin/config/WebMvcConfig.java \
        src/main/java/com/crosscert/fidoadmin/common/GlobalExceptionHandler.java \
        src/test/java/com/crosscert/fidoadmin/common/TenantSelectionInterceptorWebTest.java
git commit -m "feat: 미선택 상태에서 테넌트 화면 접근을 선택 화면으로 돌린다"
```

---

### Task 7: 대시보드 생 SQL 을 유효 테넌트로 건다

`StatisticsQueryService` 는 `CrudService` 를 타지 않고 `NamedParameterJdbcTemplate` 로 직접 조회한다. Task 2 에서 컴파일만 맞췄으므로 여기서 동작을 확정한다.

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/dashboard/StatisticsQueryService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/dashboard/DashboardController.java`
- Test: `src/test/java/com/crosscert/fidoadmin/dashboard/StatisticsTenantScopeTest.java`

**Interfaces:**
- Consumes: `TenantContext.companyIdx()` (Task 2)
- Produces: `groupbys()`, `serviceNames(Long)`, 집계 조회가 전부 유효 테넌트로 걸린다. `serviceNames` 의 파라미터는 **삭제**한다 — 유효 테넌트가 유일한 출처이므로 받을 이유가 없다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    /**
     * 예전에는 SUPER 면 companyIdx 에 null 이 들어가 전체가 합산됐다.
     * 이제는 선택한 고객사만 합산한다.
     */
    @Test void SUPER_의_집계는_선택한_고객사로_걸린다() {
        loginSuperSelecting(9L);
        service.groupbys();
        verify(jdbc).queryForList(anyString(), paramCaptor.capture(), eq(String.class));
        assertThat(paramCaptor.getValue()).containsEntry("companyIdx", 9L);
    }

    @Test void COMPANY_의_집계는_자기_고객사로_걸린다() {
        login(5L);
        service.groupbys();
        verify(jdbc).queryForList(anyString(), paramCaptor.capture(), eq(String.class));
        assertThat(paramCaptor.getValue()).containsEntry("companyIdx", 5L);
    }

    @Test void 미선택_SUPER_의_집계는_예외다() {
        login(0L);
        assertThatThrownBy(() -> service.groupbys())
            .isInstanceOf(NoTenantSelectedException.class);
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*StatisticsTenantScopeTest'`
Expected: FAIL — 기존 코드가 SUPER 에 null 을 넣던 기대값과 충돌

- [ ] **Step 3: 서비스를 고친다**

세 곳(`groupbys`, `serviceNames`, 집계 조회)에서 `p.put("companyIdx", ...)` 를 `tenant.companyIdx()` 로 바꾼다.

```java
    @Transactional(readOnly = true)
    public List<String> groupbys() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", tenant.companyIdx());
        return jdbc.queryForList(
            "SELECT DISTINCT GROUPBY FROM FIDO_STATISTICS"
                + " WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY GROUPBY",
            p, String.class);
    }

    /** 유효 테넌트가 유일한 출처이므로 파라미터로 받지 않는다. */
    @Transactional(readOnly = true)
    public List<String> serviceNames() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", tenant.companyIdx());
        return jdbc.queryForList(
            "SELECT DISTINCT SERVICE_NAME FROM FIDO_STATISTICS"
                + " WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY SERVICE_NAME",
            p, String.class);
    }
```

`(:companyIdx IS NULL OR ...)` 는 그대로 둔다. 값이 null 일 일이 없어졌지만 SQL 을 바꾸면 회귀 위험만 늘고 얻는 것이 없다.

`DashboardController` 에서 `companyForNames` 계산과 `companies` 모델 속성을 지우고, `serviceNames()` 호출에서 인자를 뺀다.

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*StatisticsTenantScopeTest' --tests '*DashboardControllerWebTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/dashboard/ \
        src/test/java/com/crosscert/fidoadmin/dashboard/
git commit -m "fix: 대시보드 집계를 유효 테넌트로 건다"
```

---

### Task 8: 감사 로그에 대상 테넌트를 기록한다

지금은 행위자의 `COMPANY_IDX` 를 남겨 SUPER 의 모든 행위가 0 으로 뭉친다. 대상 테넌트를 남기면 "슈퍼관리자가 어느 고객사를 만졌는가"가 처음으로 추적된다.

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/audit/AuditLogger.java`
- Test: `src/test/java/com/crosscert/fidoadmin/audit/AuditLoggerTenantTest.java`

**Interfaces:**
- Consumes: `TenantContext.hasTenant()`, `companyIdx()` (Task 2), `CompanyLookup.name()`
- Produces: `AuditLogger.log(AuditType, String)` 이 대상 테넌트를 기록한다. `log(ManagerUserDetails, ...)` 5인자 버전은 **시그니처·동작 모두 그대로** 둔다 — 로그인/로그아웃은 테넌트가 없는 사건이고 `LoginSuccessHandler` 가 이 버전을 쓴다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test void SUPER_의_행위는_대상_테넌트로_기록된다() {
        loginSuperSelecting(9L);
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(9L);
        assertThat(captor.getValue().getUserId()).isEqualTo("superuser");
    }

    @Test void COMPANY_의_행위는_자기_고객사로_기록된다() {
        login(5L);
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(5L);
    }

    /** 시스템 영역 작업은 선택이 없다. 행위자 소속(0)으로 남긴다. */
    @Test void 미선택_상태의_행위는_행위자_소속으로_기록된다() {
        login(0L);
        logger.log(AuditType.CREATE, "CCFA_COMPANY CREATE 3");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(0L);
    }

    /** 해시는 저장되는 값으로 계산되므로 재계산이 성립해야 한다. */
    @Test void 무결성_해시가_저장값과_일치한다() {
        loginSuperSelecting(9L);
        logger.log(AuditType.UPDATE, "x");
        verify(writer).write(captor.capture());
        var row = captor.getValue();
        assertThat(row.getIntergrityHash()).isEqualTo(AuditLogger.integrityHash(row));
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*AuditLoggerTenantTest'`
Expected: FAIL — 첫 테스트가 0 을 받는다(행위자 소속)

- [ ] **Step 3: AuditLogger 를 고친다**

```java
    /**
     * 현재 로그인 사용자·현재 HTTP 요청 기준으로 기록한다.
     *
     * <p>COMPANY_IDX 에는 <b>대상 테넌트</b>를 넣는다. 행위자는 USER_ID 로 식별된다.
     * 슈퍼관리자가 어느 고객사 데이터를 만졌는지가 이 컬럼에만 남는다
     * (스키마를 바꿀 수 없어 기존 컬럼을 이렇게 해석한다).
     * 선택이 없는 전역 작업은 행위자 소속(0)을 그대로 쓴다 — 사실에 맞다.
     */
    public void log(AuditType type, String message) {
        tenant.current().ifPresent(actor -> {
            HttpServletRequest req = currentRequest();
            Long targetCompany = tenant.hasTenant() ? tenant.companyIdx() : actor.getCompanyIdx();
            String targetName = tenant.hasTenant() ? companies.name(targetCompany) : actor.getCompanyName();
            log(actor, type, message, targetCompany, targetName,
                req == null ? null : req.getRemoteAddr(),
                req == null ? null : req.getHeader("User-Agent"));
        });
    }

    /** 로그인·로그아웃처럼 테넌트가 없는 사건용. 행위자 소속을 그대로 쓴다. */
    public void log(ManagerUserDetails actor, AuditType type, String message, String ip, String ua) {
        log(actor, type, message, actor.getCompanyIdx(), actor.getCompanyName(), ip, ua);
    }

    private void log(ManagerUserDetails actor, AuditType type, String message,
                     Long companyIdx, String companyName, String ip, String ua) {
        try {
            CcfaAuditLog row = new CcfaAuditLog();
            row.setCompanyIdx(companyIdx);
            row.setCompanyName(cut(companyName, 512));
            // 나머지는 기존과 동일
            ...
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e);
        }
    }
```

`CompanyLookup` 주입이 필요하다. **순환 의존 주의**: `CompanyLookup` 은 `TenantContext` 만 쓰고 `AuditLogger` 를 쓰지 않으므로 순환은 생기지 않는다. 컴파일 후 부팅 테스트로 확인한다.

`integrityHash()` 는 **손대지 않는다.** 입력 필드 목록과 인코딩이 그대로이므로 기존 행의 검증도 깨지지 않는다.

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests '*AuditLogger*' --tests '*AuditLogRoundTrip*'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/audit/AuditLogger.java \
        src/test/java/com/crosscert/fidoadmin/audit/AuditLoggerTenantTest.java
git commit -m "feat: 감사 로그에 행위자 소속이 아니라 대상 테넌트를 남긴다"
```

---

### Task 9: 슈퍼관리자 계정 화면 (/managers/super)

SUPER 계정은 `COMPANY_IDX = 0` 이고 0 은 선택 목록에 없다. 그래서 어떤 고객사를 골라도 슈퍼관리자 계정이 운영자 목록에 나오지 않는다. **이것은 이번 변경이 만드는 회귀이므로 반드시 막아야 한다.**

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/manager/service/SuperManagerService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/manager/web/SuperManagerController.java`
- Create: `src/main/resources/templates/manager/super/{list,form,detail}.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java` (메뉴 1개 추가)
- Test: `src/test/java/com/crosscert/fidoadmin/manager/SuperManagerServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/manager/web/SuperManagerControllerWebTest.java`

**Interfaces:**
- Consumes: `CcfaManagerRepository`, `AuditLogger`, `LoginAttemptService`, `TenantContext`
- Produces: `/managers/super` 목록·상세·등록·수정·삭제·잠금해제. `COMPANY_IDX = 0` 인 행만 다룬다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    /** 이 화면은 유효 테넌트가 아니라 상수 0 으로 건다. */
    @Test void 슈퍼관리자_계정만_조회된다() {
        loginSuperSelecting(9L);
        service.search(new ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx")));
        verify(repo).findAll(captor.capture(), any(PageRequest.class));
        assertThat(companyIdxEqualsIn(captor.getValue())).isEqualTo(0L);
    }

    @Test void 등록하면_COMPANY_IDX_가_0_이_된다() {
        loginSuperSelecting(9L);
        when(repo.save(any(CcfaManager.class))).thenAnswer(i -> i.getArgument(0));
        CcfaManager created = service.create(manager(null, 5L));
        assertThat(created.getCompanyIdx()).isEqualTo(0L);
    }

    /** 테넌트 운영자 행은 이 화면에서 열 수 없다. */
    @Test void 일반_운영자_행은_열_수_없다() {
        loginSuperSelecting(9L);
        when(repo.findById(1L)).thenReturn(Optional.of(manager(1L, 5L)));
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(TenantMismatchException.class);
    }

    @Test void 자기_자신은_삭제할_수_없다() {
        loginSuperAs(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(manager(1L, 0L)));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
    }
```

웹 테스트:

```java
    @Test void 일반_운영자_목록에는_슈퍼관리자가_없다() throws Exception {
        // ManagerController 는 유효 테넌트(9)로 걸리므로 COMPANY_IDX=0 행은 안 나온다
        mvc.perform(get("/managers").with(superUserSelecting(9L)))
            .andExpect(status().isOk());
        // 서비스에 전달된 Specification 이 companyIdx=9 인지 확인
    }

    @Test void COMPANY_계정은_접근할_수_없다() throws Exception {
        mvc.perform(get("/managers/super").with(companyUser(5L)))
            .andExpect(status().isForbidden());
    }

    /** 시스템 영역이므로 고객사 선택 없이 열린다. */
    @Test void 미선택_SUPER_도_열_수_있다() throws Exception {
        mvc.perform(get("/managers/super").with(superUser()))
            .andExpect(status().isOk());
    }
```

Task 4 에서 보류한 메뉴 테스트를 `MenuRegistryTest` 에 추가한다:

```java
    @Test void 슈퍼관리자_계정_화면은_시스템_영역이다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/managers/super")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/managers/super/3")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/managers")).isEqualTo(MenuArea.TENANT);
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*SuperManager*' --tests '*MenuRegistryTest'`
Expected: FAIL — 클래스가 없어 컴파일 오류

- [ ] **Step 3: SuperManagerService 를 만든다**

`ManagerService` 를 상속하면 유효 테넌트 경로가 섞인다. `CrudService` 를 직접 상속하고 테넌트 속성 훅만 상수 0 으로 고정한다.

```java
package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.TenantMismatchException;
// ... 나머지 import 는 ManagerService 와 동일

/**
 * COMPANY_IDX = 0 인 슈퍼관리자 계정 전용 화면.
 *
 * <p>테넌트 선택 모델에서 IDX 0 은 선택할 수 없으므로(SelectedTenant.select),
 * 어떤 고객사를 골라도 슈퍼관리자 계정이 /managers 목록에 나오지 않는다.
 * 이 화면이 그 계정을 관리하는 유일한 자리다.
 *
 * <p>테넌트 필터를 상수 0 으로 고정한다. CrudService 의 유효 테넌트 경로를
 * 쓰지 않으므로 선택 여부와 무관하게 동작한다(SYSTEM 영역).
 */
@Service
public class SuperManagerService extends CrudService<CcfaManager, Long, ManagerSearchForm> {

    /** 슈퍼관리자 계정의 소속. SignupPolicy.SUPER_COMPANY_IDX 와 같은 값이다. */
    private static final long SUPER_COMPANY_IDX = 0L;

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final LoginAttemptService loginAttempts;
    private final EntityManager em;
    private final TenantContext tenant;

    public SuperManagerService(CcfaManagerRepository managers, AuditLogger audit,
                               CcfaManagerPwPolicyRepository policies,
                               LoginAttemptService loginAttempts, EntityManager em,
                               TenantContext tenant) {
        super(managers, audit, tenant);
        this.managers = managers;
        this.policies = policies;
        this.loginAttempts = loginAttempts;
        this.em = em;
        this.tenant = tenant;
    }

    @Override protected Specification<CcfaManager> toSpecification(ManagerSearchForm f) {
        return Specs.all(
            Specs.eq("companyIdx", SUPER_COMPANY_IDX),
            Specs.like("userId", f.getUserId()),
            Specs.like("userNm", f.getUserNm()),
            Specs.eq("status", f.getStatus()));
    }

    /**
     * null 을 돌려주면 CrudService 가 "전역 테이블"로 보고 테넌트 필터를 아예 걸지 않는다.
     * 여기서는 필터가 필요하되 값이 유효 테넌트가 아니라 상수 0 이므로,
     * 훅은 null 로 두고 toSpecification 에서 직접 건 뒤 checkTenant 를 재정의한다.
     */
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaManager e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaManager e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaManager e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MANAGER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userId", "userNm", "lastAccess"); }

    /** 상수 0 으로 검사한다. 다른 테넌트의 운영자 행은 이 화면에서 열 수 없다. */
    @Override protected void checkTenant(CcfaManager e) {
        if (e.getCompanyIdx() == null || e.getCompanyIdx() != SUPER_COMPANY_IDX) {
            throw new TenantMismatchException("CCFA_MANAGER " + idOf(e));
        }
    }

    @Override protected void applyDefaults(CcfaManager e) {
        e.setCompanyIdx(SUPER_COMPANY_IDX);   // 이 화면이 만드는 계정은 항상 슈퍼관리자다
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus(ManagerStatus.ACTIVE);
        if (e.getLogin() == null) e.setLogin("OFF-LINE");
        if (e.getAlramType() == null || e.getAlramType().isBlank()) e.setAlramType("none");
        if (e.getAlramLevel() == null || e.getAlramLevel().isBlank()) e.setAlramLevel("0");
    }

    @Override protected void touchCreated(CcfaManager e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaManager e, LocalDateTime now) { e.setUpdatedtime(now); }

    /** USER_ID 유니크 제약이 없어 코드에서 직렬화한다(ManagerService 와 같은 이유). */
    @Override protected CcfaManager insert(CcfaManager e) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(e.getUserId()).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + e.getUserId() + " 은(는) 이미 존재합니다");
        }
        return super.insert(e);
    }

    @Override protected void beforeDelete(CcfaManager e) {
        if (e.getIdx() != null && e.getIdx().equals(tenant.require().getIdx())) {
            throw new IllegalStateException("자기 자신은 삭제할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<CcfaManagerPwPolicy> lockState(String userId) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId);
    }

    @Transactional
    public void unlock(Long id) {
        CcfaManager m = get(id);
        em.refresh(m, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        loginAttempts.unlock(m.getUserId());
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.getUserId());
    }
}
```

**주의 1**: `companyIdxAttribute()` 가 null 이면 `requireSuperForGlobalTable()` 이 계정 SUPER 를 요구한다. 이 화면은 SUPER 전용이므로 의도에 맞다.

**주의 2 (중복)**: 위 `insert()` 는 `ManagerService.insert()` 와 같다. `CCFA_MANAGER` 에 USER_ID 유니크 제약이 없어 테이블 잠금으로 직렬화하는 로직인데, 두 서비스가 같은 테이블을 쓰므로 **한쪽만 잠가서는 안 된다**(두 화면에서 동시에 같은 USER_ID 를 등록하면 중복이 생긴다). 구현 시 이 메서드를 `ManagerService` 에서 `protected static` 헬퍼로 빼거나, 공통 부모를 두어 한 곳에 둔다. 복사해 두면 한쪽만 고쳐질 위험이 있다.

- [ ] **Step 4: 컨트롤러와 템플릿을 만든다**

`SuperManagerController` 는 `ManagerController` 를 본떠 만들되 `basePath()` 를 `/managers/super`, `viewDir()` 을 `manager/super` 로 한다. 고객사 select 는 없다(항상 0 이다). 템플릿 3개는 `manager/manager/*.html` 을 복사한 뒤 고객사 열·select 를 빼고 제목을 "슈퍼관리자 계정"으로 바꾼다.

**경로 충돌 주의**: `ManagerController` 가 `/managers/{id}` 를 매핑하고 `SuperManagerController` 가 `/managers/super` 를 매핑한다. Spring 은 더 구체적인 패턴을 우선하므로 `/managers/super` 가 이긴다. 다만 `{id}` 가 `Long` 이라 `"super"` 는 `MethodArgumentTypeMismatchException` → 404 로 이미 걸러진다. 웹 테스트로 확인한다.

`MenuRegistry.ALL` 에 추가한다(가입 승인 다음 줄):

```java
        new MenuItem(MenuArea.SYSTEM, "시스템", "슈퍼관리자 계정", "/managers/super", true, "bi-person-gear"),
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests '*SuperManager*' --tests '*Manager*' --tests '*MenuRegistryTest'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/manager/ \
        src/main/resources/templates/manager/super/ \
        src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
        src/test/java/com/crosscert/fidoadmin/manager/
git commit -m "feat: 슈퍼관리자 계정 화면을 시스템 영역에 추가한다

테넌트 선택 모델에서 IDX 0 은 선택할 수 없어 슈퍼관리자 계정이 운영자 목록에서
사라진다. 그 계정을 관리할 자리를 만든다."
```

---

### Task 10: 상단 선택기와 중복 UI 제거

**Files:**
- Modify: `src/main/resources/templates/layout/base.html`
- Modify: `src/main/resources/templates/fragments/sidebar.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/CurrentPathAdvice.java`
- Modify: 16개 `list.html` (검색폼 select + 고객사 컬럼 제거)
- Modify: 4개 `form.html` (고객사 select 제거 — FDS 정책·시스템 설정은 Task 11)
- Modify: 14개 컨트롤러 (`companies` 모델 속성 제거)
- Modify: `src/main/java/com/crosscert/fidoadmin/common/SearchForm.java`
- Test: `src/test/java/com/crosscert/fidoadmin/common/LayoutWebTest.java`

**Interfaces:**
- Consumes: `TenantContext`, `MenuArea`, `CompanyLookup.all()`
- Produces: `@ModelAttribute("currentArea")`, `@ModelAttribute("selectedCompanyName")`, `@ModelAttribute("selectableCompanies")`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test void 테넌트_화면에는_선택기가_보인다() throws Exception {
        mvc.perform(get("/users").with(superUserSelecting(9L)))
            .andExpect(content().string(containsString("tenant-switcher")));
    }

    @Test void 시스템_화면에는_시스템_뱃지가_보인다() throws Exception {
        mvc.perform(get("/companies").with(superUser()))
            .andExpect(content().string(containsString("시스템 관리")))
            .andExpect(content().string(not(containsString("tenant-switcher"))));
    }

    @Test void COMPANY_에게는_선택기가_보이지_않는다() throws Exception {
        mvc.perform(get("/users").with(companyUser(5L)))
            .andExpect(content().string(not(containsString("tenant-switcher"))));
    }

    /** 검색폼의 고객사 select 가 사라졌다. */
    @Test void 목록_검색폼에_고객사_select_가_없다() throws Exception {
        mvc.perform(get("/logs/audit").with(superUserSelecting(9L)))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*LayoutWebTest'`
Expected: FAIL

- [ ] **Step 3: CurrentPathAdvice 를 확장한다**

```java
package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.common.MenuArea;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 레이아웃이 쓰는 전역 모델 속성. */
@ControllerAdvice
@RequiredArgsConstructor
public class CurrentPathAdvice {

    private final TenantContext tenant;
    private final MenuRegistry menus;
    private final CompanyLookup companies;

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("currentArea")
    public MenuArea currentArea(HttpServletRequest request) {
        return menus.areaOf(request.getRequestURI());
    }

    /** 상단 선택기에 표시할 이름. 미선택이거나 COMPANY 면 null. */
    @ModelAttribute("selectedCompanyName")
    public String selectedCompanyName() {
        if (tenant.current().isEmpty() || !tenant.hasTenant()) return null;
        return companies.name(tenant.companyIdx());
    }

    /**
     * 선택기 드롭다운 목록. SUPER 에게만 채운다.
     * 전역(IDX 0)은 테넌트가 아니므로 뺀다.
     */
    @ModelAttribute("selectableCompanies")
    public List<CcfaCompany> selectableCompanies() {
        if (tenant.current().isEmpty() || !tenant.require().isSuper()) return List.of();
        return companies.all().stream().filter(c -> c.getIdx() != null && c.getIdx() != 0L).toList();
    }
}
```

- [ ] **Step 4: 레이아웃의 상단바를 바꾼다**

`layout/base.html` 의 `<header>` 안을 이렇게 바꾼다:

```html
    <header class="fa-topbar d-flex align-items-center justify-content-between px-3">
      <span class="fw-semibold">FIDO Admin</span>
      <div class="d-flex align-items-center gap-3 small">

        <!-- SUPER + 테넌트 영역: 고객사 전환 드롭다운 -->
        <div class="dropdown" id="tenant-switcher"
             th:if="${currentArea?.name() == 'TENANT' and !#lists.isEmpty(selectableCompanies)}">
          <button class="btn btn-sm btn-outline-secondary dropdown-toggle" type="button"
                  data-bs-toggle="dropdown" aria-expanded="false">
            <i class="bi bi-building" aria-hidden="true"></i>
            <span th:text="${selectedCompanyName} ?: '고객사 선택'">고객사</span>
          </button>
          <ul class="dropdown-menu dropdown-menu-end">
            <li th:each="c : ${selectableCompanies}">
              <form method="post" th:action="@{/select-tenant}" class="m-0">
                <input type="hidden" name="companyIdx" th:value="${c.idx}">
                <input type="hidden" name="returnTo" th:value="${currentPath}">
                <button type="submit" class="dropdown-item" th:text="${c.companyName}">고객사</button>
              </form>
            </li>
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item" th:href="@{/select-tenant}">
              <i class="bi bi-gear" aria-hidden="true"></i> 고객사 선택 화면으로</a></li>
          </ul>
        </div>

        <!-- SUPER + 시스템 영역: 테넌트 맥락이 아님을 드러낸다 -->
        <span class="badge text-bg-dark" sec:authorize="hasRole('SUPER')"
              th:if="${currentArea?.name() == 'SYSTEM'}">
          <i class="bi bi-gear" aria-hidden="true"></i> 시스템 관리
        </span>

        <span sec:authentication="principal.userNm">이름</span>
        <!-- COMPANY 는 자기 고객사가 고정이므로 정적 텍스트 -->
        <span class="text-secondary" sec:authorize="!hasRole('SUPER')"
              sec:authentication="principal.companyName">고객사</span>
        <span class="badge text-bg-secondary" sec:authorize="hasRole('SUPER')">SUPER</span>
        <form th:action="@{/logout}" method="post" class="m-0">
          <button class="btn btn-outline-secondary btn-sm" type="submit">로그아웃</button>
        </form>
      </div>
    </header>
```

- [ ] **Step 5: 사이드바를 영역별로 나눈다**

`fragments/sidebar.html`:

```html
<nav xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
     class="fa-sidebar" th:fragment="sidebar"
     th:with="isSuper=${#authorization.expression('hasRole(''SUPER'')')},
              items=${@menuRegistry.itemsFor(isSuper)},
              hasTenant=${selectedCompanyName != null}">
  <div class="fa-brand px-3 py-3">FIDO Admin</div>

  <!-- 테넌트 영역: 선택된 고객사의 데이터 -->
  <div class="px-3 pb-1 small text-secondary text-truncate"
       th:text="${selectedCompanyName} ?: '고객사 미선택'">고객사</div>
  <div th:each="g : ${T(com.crosscert.fidoadmin.common.MenuRegistry).groups(
         items.?[area.name() == 'TENANT'])}" class="mb-2"
       th:classappend="${hasTenant} ? '' : 'opacity-50'">
    <div class="fa-group px-3 text-uppercase small text-secondary" th:text="${g.key}">그룹</div>
    <a th:each="m : ${g.value}" th:href="${hasTenant} ? @{${m.href}} : 'javascript:void(0)'"
       th:classappend="${currentPath == m.href} ? 'active' : ''"
       th:attr="tabindex=${hasTenant} ? null : '-1', aria-disabled=${hasTenant} ? null : 'true'"
       class="fa-link d-flex align-items-center gap-2 px-3 py-1">
      <i class="bi" th:classappend="${m.icon}" aria-hidden="true"></i>
      <span th:text="${m.title}">메뉴</span>
    </a>
  </div>

  <!-- 시스템 영역: 선택과 무관한 전역 화면 -->
  <div th:if="${isSuper}">
    <hr class="my-2 mx-3">
    <div th:each="g : ${T(com.crosscert.fidoadmin.common.MenuRegistry).groups(
           items.?[area.name() == 'SYSTEM'])}" class="mb-2">
      <div class="fa-group px-3 text-uppercase small text-secondary" th:text="${g.key}">그룹</div>
      <a th:each="m : ${g.value}" th:href="@{${m.href}}"
         th:classappend="${currentPath == m.href} ? 'active' : ''"
         class="fa-link d-flex align-items-center gap-2 px-3 py-1">
        <i class="bi" th:classappend="${m.icon}" aria-hidden="true"></i>
        <span th:text="${m.title}">메뉴</span>
      </a>
    </div>
  </div>

  <!-- 개인 영역 -->
  <div th:each="g : ${T(com.crosscert.fidoadmin.common.MenuRegistry).groups(
         items.?[area.name() == 'PERSONAL'])}" class="mb-2">
    <a th:each="m : ${g.value}" th:href="@{${m.href}}"
       th:classappend="${currentPath == m.href} ? 'active' : ''"
       class="fa-link d-flex align-items-center gap-2 px-3 py-1">
      <i class="bi" th:classappend="${m.icon}" aria-hidden="true"></i>
      <span th:text="${m.title}">메뉴</span>
    </a>
  </div>
</nav>
```

- [ ] **Step 6: 16개 list.html 에서 고객사 select 와 컬럼을 지운다**

대상: `dashboard/index.html`, `fido/{appid,appserver,user,challenge,sign,transactionhash,transaction-confirmation}/list.html`, `log/{fido,audit,exceptions,mailing}/list.html`, `company/{license,fds-policy}/list.html`, `manager/manager/list.html`, `system/props/list.html`

각 파일에서:
1. `<div class="col-auto" sec:authorize="hasRole('SUPER')">` 로 감싼 고객사 `<select name="companyIdx">` 블록을 통째로 삭제
2. `<thead>` 의 `<th>고객사</th>` 삭제
3. `<tbody>` 의 대응하는 `<td th:text="${r.companyName}">` 또는 `<td th:text="${companyNames.get(r.companyIdx)}">` 삭제
4. `colspan` 숫자를 1 줄임 (예: `colspan="7"` → `colspan="6"`)

**4번을 빠뜨리면 "데이터가 없습니다" 행이 어긋난다.** 각 파일에서 확인한다.

구체적인 예로 `log/audit/list.html` 은 이렇게 바뀐다.

지우는 블록:

```html
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
```

표 머리 — `<th>고객사</th>` 를 뺀다:

```html
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>유형</th><th>사용자</th><th>메시지</th><th>IP</th></tr></thead>
```

표 본문 — `<td th:text="${r.companyName}"></td>` 를 빼고 `colspan` 을 7 에서 6 으로 줄인다:

```html
        <tr th:if="${page.totalElements == 0}"><td colspan="6" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
```

나머지 15개 파일도 같은 형태다. 열 개수가 파일마다 다르므로 `colspan` 은 각 파일의 실제 `<th>` 수를 세어 맞춘다.

작업 후 확인:

```bash
grep -rln 'name="companyIdx"' src/main/resources/templates
```

Task 10 을 마친 시점에 남아야 하는 것은 **`signup/list.html`**(승인 시 소속 배정용, 의도적으로 남긴다)과 **`system/props/form.html`**(Task 11 에서 처리) 둘뿐이다. Task 11 까지 끝나면 `signup/list.html` 하나만 남는다.

`th:field="*{companyIdx}"` 쪽도 따로 확인한다:

```bash
grep -rln 'th:field="\*{companyIdx}"' src/main/resources/templates
```

Task 10 이후 남아야 하는 것은 **`company/fds-policy/form.html`**(수정 화면의 hidden 식별자 — 영구히 남는다)과 **`system/props/form.html`**(Task 11 에서 처리)이다.

- [ ] **Step 7: 4개 form.html 에서 고객사 select 를 지운다**

대상: `fido/appid/form.html`, `fido/appserver/form.html`, `manager/manager/form.html`, `company/license/form.html`
(FDS 정책·시스템 설정은 PK 라서 Task 11 에서 따로 다룬다)

각 파일에서 `<select th:field="*{companyIdx}">` 를 감싼 `<div class="col-md-6">` 블록을 삭제한다. `CrudService.create()` 가 유효 테넌트로 덮어쓰므로 폼 필드가 없어도 값이 채워진다.

- [ ] **Step 8: 14개 컨트롤러에서 companies 주입을 지운다**

`if (tenant.require().isSuper()) model.addAttribute("companies", companies.all());` 17개 호출부를 삭제한다(Task 2 에서 컴파일만 맞춰 둔 것). 조건 없는 호출부 5곳(`ManagerController` ×2, `SystemPropController` ×2, `LicenseController` ×1)도 지운다.

**`SignupAdminController` 의 `companies` 는 남긴다** — 승인 시 소속을 배정하는 용도이고 SYSTEM 영역이다.

`companyNames` 모델 속성은 상세 화면이 여전히 쓰므로 **남긴다**(스펙 6.4: 상세의 고객사 표시는 유지).

- [ ] **Step 9: SearchForm 에서 companyIdx 를 지운다**

```java
    private int page = 0;
    private int size = DEFAULT_SIZE;
    private String sort;
    // companyIdx 삭제 — 테넌트는 세션이 정하며 검색 조건이 아니다
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate;
```

`toQueryString()` 의 `params.put("companyIdx", companyIdx);` 줄도 지운다. `SearchFormTest` 에서 이 항목을 검증하는 단언이 있으면 함께 지운다.

- [ ] **Step 10: 통과를 확인한다**

Run: `./gradlew test`
Expected: PASS 전부

- [ ] **Step 11: 커밋**

```bash
git add -A
git commit -m "feat: 상단 고객사 선택기를 넣고 화면별 고객사 select 를 제거한다

검색폼 select 16개, 폼 select 4개, 목록의 고객사 컬럼 16개, SearchForm.companyIdx
를 지운다. 테넌트는 이제 세션이 정하므로 화면마다 고를 이유가 없다."
```

---

### Task 11: FDS 정책과 시스템 설정의 PK 처리

두 화면은 `companyIdx` 가 식별자의 일부라 select 를 지우는 것만으로 끝나지 않는다.

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyController.java`
- Modify: `src/main/resources/templates/company/fds-policy/form.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropController.java` 또는 `SystemPropForm`
- Modify: `src/main/resources/templates/system/props/form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/company/FdsPolicyTenantTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/SystemPropTenantTest.java`

**Interfaces:**
- Consumes: `TenantContext.companyIdx()` (Task 2)
- Produces: 두 화면의 등록이 유효 테넌트로 PK 를 채운다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

```java
    @Test void FDS_정책_등록은_선택한_고객사를_PK_로_쓴다() {
        loginSuperSelecting(9L);
        CcfaFdsPolicy e = controller.toEntity(new FdsPolicyForm());
        assertThat(e.getCompanyIdx()).isEqualTo(9L);
    }

    /**
     * 식별자가 URL 에 들어간다(/system/props/{key}@{companyIdx}).
     * 다른 테넌트의 값을 넣어도 열리지 않아야 한다.
     */
    @Test void 시스템_설정은_URL_의_다른_테넌트를_거부한다() throws Exception {
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@3").with(superUserSelecting(9L)))
            .andExpect(status().isNotFound());
    }

    @Test void 시스템_설정은_선택한_테넌트의_값은_연다() throws Exception {
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@9").with(superUserSelecting(9L)))
            .andExpect(status().isOk());
    }

    @Test void 시스템_설정_등록은_선택한_고객사를_PK_로_쓴다() {
        loginSuperSelecting(9L);
        CcfaSystemProp e = controller.toEntity(form("PW_FAIL_LIMIT", "5"));
        assertThat(e.getId().getCompanyIdx()).isEqualTo(9L);
    }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests '*FdsPolicyTenantTest' --tests '*SystemPropTenantTest'`
Expected: FAIL

- [ ] **Step 3: FdsPolicyController 를 고친다**

```java
    /**
     * 할당형 PK 이므로 폼이 아니라 <b>유효 테넌트</b>로 식별자를 채운다.
     * 고객사 select 가 사라졌으므로 폼에는 값이 없다.
     * AssignedIdCrudService.insert() 가 존재 검사 후 persist 하므로
     * 이미 정책이 있는 고객사를 고른 채 등록하면 기존과 같은 중복 거부가 난다.
     */
    @Override protected CcfaFdsPolicy toEntity(FdsPolicyForm f) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(tenant.companyIdx());
        f.applyTo(p);
        return p;
    }

    /** 유효 테넌트는 항상 값이 있으므로 "고객사를 선택하세요" 거부가 성립하지 않는다. */
    @Override protected void validate(FdsPolicyForm f, boolean isNew, BindingResult binding) {}
```

`validate` 가 비었으면 오버라이드 자체를 지운다(기반 클래스의 빈 구현이 있다).

`company/fds-policy/form.html` 에서 등록용 select 블록(`th:if="${isNew}" sec:authorize="hasRole('SUPER')"`)을 삭제한다. 수정용 읽기 전용 표시와 hidden 필드는 **그대로 둔다**(식별자는 바꿀 수 없다).

- [ ] **Step 4: SystemProp 을 고친다**

`SystemPropForm.toNewEntity()` 가 `companyIdx` 를 받도록 바꾸거나, 컨트롤러가 채운다:

```java
    /** 복합키(PROP_KEY + COMPANY_IDX)의 COMPANY_IDX 를 유효 테넌트로 채운다. */
    @Override protected CcfaSystemProp toEntity(SystemPropForm f) {
        return f.toNewEntity(tenant.companyIdx());
    }
```

`system/props/form.html` 과 `list.html` 에서 고객사 select 를 지운다(Task 10 에서 list 는 이미 처리됐다).

상세·수정 경로의 방어는 **이미 Task 3 이 처리했다** — `checkTenant()` 가 `id.companyIdx` 를 유효 테넌트와 비교한다. 위 테스트가 그것을 확인한다.

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests '*FdsPolicy*' --tests '*SystemProp*'`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "fix: PK 가 companyIdx 인 두 화면을 유효 테넌트로 채운다

FDS 정책은 할당형 PK, 시스템 설정은 복합키라 select 제거만으로는 식별자를
채울 수 없다. 시스템 설정은 식별자가 URL 에 들어가는데, 예전에는 SUPER 가
{key}@{다른테넌트} 로 임의 테넌트의 설정을 열 수 있었다."
```

---

### Task 12: 최종 검증과 문서

**Files:**
- Modify: `README.md`
- Modify: `tasks/todo.md` (Review 절 추가)

- [ ] **Step 1: 전체 테스트를 돌린다**

Docker Oracle 을 띄운 상태에서:

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps    # healthy 확인
./gradlew clean test
```

Expected: BUILD SUCCESSFUL, 실패 0

- [ ] **Step 2: 건너뛴 테스트가 없는지 확인한다**

```bash
grep -ho 'skipped="[0-9]*"' build/test-results/test/*.xml | sort -u
```

Expected: `skipped="0"` 만 나온다. 하나라도 0 이 아니면 그 테스트를 찾아 되살린다
(`tasks/lessons.md`: "통과했다"는 실행된 테스트에 대해서만 참이다).

- [ ] **Step 3: 손으로 확인한다**

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`superuser / Admin1234!` 로 로그인해 순서대로 확인한다:

1. 로그인 직후 `/select-tenant` 가 뜬다
2. 고객사를 고르면 대시보드로 들어가고 상단에 이름이 뜬다
3. `/users` 목록에 검색폼 고객사 select 와 고객사 컬럼이 없다
4. 상단에서 다른 고객사로 바꾸면 같은 화면에 다른 데이터가 뜬다
5. 상세 화면(`/users/{id}`)에서 고객사를 바꾸면 **목록으로** 간다
6. **다른 테넌트의 ID 를 URL 에 직접 넣으면 404 다** ← 이번 변경의 핵심
7. `/companies` 등 시스템 화면은 선택과 무관하게 열리고 상단에 "시스템 관리" 뱃지가 뜬다
8. `/managers/super` 에 슈퍼관리자 계정이 보이고 `/managers` 에는 안 보인다
9. `/logs/audit` 에서 방금 한 작업의 고객사가 **대상 테넌트**로 남았다

`kbadmin / Company1234!` 로 로그인해 확인한다:

10. 선택 화면이 뜨지 않고 바로 대시보드다
11. 상단에 선택기가 없고 고객사 이름이 정적 텍스트다
12. 동작이 변경 전과 같다 (회귀 없음)

- [ ] **Step 4: README 를 갱신한다**

"실행" 절 아래에 더한다:

```markdown
## 테넌트 선택

슈퍼관리자는 한 번에 하나의 고객사만 본다. 로그인하면 `/select-tenant` 에서
고객사를 고르고, 상단 선택기로 바꾼다. 선택한 고객사가 조회·상세·등록·수정·삭제
전부의 경계다(설계: `docs/superpowers/specs/2026-09-18-tenant-scoped-ui-design.md`).

화면은 세 영역으로 나뉜다.

- **테넌트 영역** — 고객사를 골라야 열린다. 대시보드, FIDO 데이터, 로그,
  라이선스, 운영자, 시스템 설정.
- **시스템 영역** — 선택과 무관하다. 고객사 관리, 가입 승인, 슈퍼관리자 계정,
  FIDO2 메타데이터, 시스템 정보 등 `COMPANY_IDX` 컬럼이 없는 전역 테이블.
- **개인 영역** — 내 비밀번호 변경.

슈퍼관리자 계정(`COMPANY_IDX = 0`)은 테넌트로 선택할 수 없으므로 운영자 목록에
나오지 않는다. **`/managers/super`** 에서 관리한다.

고객사 운영자(COMPANY) 계정의 동작은 변경 전과 같다.
```

- [ ] **Step 5: tasks/todo.md 에 Review 절을 더한다**

완료한 태스크, 테스트 결과(통과/실패/스킵 수), 손 확인 결과, 남은 과제를 적는다.

- [ ] **Step 6: 커밋**

```bash
git add README.md tasks/todo.md
git commit -m "docs: 테넌트 선택 모델을 README 에 반영한다"
```

---

## 자체 점검 결과

**스펙 커버리지** — 스펙의 각 절을 태스크에 대응시켰다.

| 스펙 절 | 태스크 |
|---|---|
| 2. 유효 테넌트 | Task 1, 2 |
| 2.1 TenantContext 빈 전환 | Task 2 |
| 2.2 SelectedTenant | Task 1 |
| 3. 영역 구분 | Task 4 |
| 4. 슈퍼관리자 계정 화면 | Task 9 |
| 5.1 CrudService | Task 3 |
| 5.2 CrudService 밖 경로 | Task 7 (대시보드), Task 2 (CompanyLookup), Task 10 (SignupAdmin 유지) |
| 5.3 인터셉터 | Task 6 |
| 6.1 상단 선택기 | Task 10 |
| 6.2 선택 화면 | Task 5 |
| 6.3 사이드바 | Task 10 |
| 6.4 제거 목록 | Task 10 |
| 6.5 FDS 정책·시스템 설정 PK | Task 11 |
| 7. 감사 로그 | Task 8 |
| 8. 테스트 9항목 | Task 3(2,3), 5, 6(1,5), 7, 8(8), 9(6,7), 12(4,9) |
| 9. 리스크 | Task 2(빈 전환), 9(회귀), 12(검증) |

**남는 판단 두 가지** (구현자가 마주칠 것이므로 미리 적는다):

1. `SuperManagerService` 가 `companyIdxAttribute()` 를 `null` 로 두고 `checkTenant()` 를 재정의하는 방식은, `CrudService` 의 "null = 전역 테이블 = SUPER 전용" 규약을 빌려 쓴 것이다. 의미상 맞지만(이 화면은 실제로 SUPER 전용이다) 다소 우회적이다. 구현 중 `CrudService` 에 "고정 테넌트" 훅을 더하는 편이 깔끔해 보이면 그렇게 해도 좋다 — 다만 그 변경은 28개 하위 서비스에 영향을 주므로 Task 3 이 끝난 뒤 별도로 판단한다.

2. Thymeleaf 의 `items.?[area.name() == 'TENANT']` SpEL 투영이 이 프로젝트의 다른 템플릿에 전례가 없다. 동작하지 않거나 읽기 어려우면 `MenuRegistry` 에 `itemsFor(boolean isSuper, MenuArea area)` 오버로드를 더해 자바 쪽에서 거르는 편이 낫다.
