# FIDO Admin 3부 — 시스템 화면과 최종 검증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 시스템 메뉴(SUPER 전용) 8개 화면 — 시스템 설정, 시스템 정보, 에러 코드, FIDO 서버, 어드민 기준, 메뉴 정의, 코드 그룹/코드, 필드 정의 — 를 추가하고, 29개 화면 전체를 브라우저·테스트로 최종 검증해 설계 11 의 인도물을 완성한다.

**Architecture:** 2부와 같은 패턴(`SearchForm` 하위 + `CrudService` 하위 + `CrudController` 하위 + list/detail/form 템플릿). 시스템 테이블은 모두 `COMPANY_IDX` 가 없거나(`companyIdxAttribute() == null`) 복합키 안에만 있어(`CCFA_SYSTEM_PROP`) SUPER 전용이며, `SecurityConfig` 의 `/system/**` 매처와 `CrudService.requireSuperForGlobalTable()` 이 이중으로 막는다. 할당형 PK 4개(`CCFA_SYSTEM_PROP` 복합키, `CCFA_SYSTEM_INFO`, `CCFA_ERROR_TABLE`, `CCFA_FIDOCLIENT`)는 2부 Task 1 의 `AssignedIdCrudService` 를 상속한다. 복합키 `CcfaSystemPropId` 는 URL 경로 한 조각 `"{PROP_KEY}@{COMPANY_IDX}"` 로 오가며 `Converter<String, CcfaSystemPropId>` 가 변환한다.

**Tech Stack:** 2부와 동일. Java 17, Spring Boot 3.5.3, Thymeleaf, Spring Data JPA, Bootstrap 5.3.7 (WebJars), JUnit 5 + Mockito + `@WebMvcTest`, Testcontainers oracle-free.

**Spec:** `docs/superpowers/specs/2026-09-16-fido-admin-design.md` (5.2 시스템 (S) 행, 4.2 매핑 규칙, 10 테스트, 11 인도물). 선행 계획: `2026-09-16-fido-admin-part1-foundation.md`, `2026-09-17-fido-admin-part2-screens.md` (**2부 Task 1 이 완료되어 있어야 한다** — `AssignedIdCrudService`, `CrudController.validate()`, `JsonPretty`).

## Global Constraints

- 2부 계획의 Global Constraints 와 공통 작성 규칙(웹 테스트 골격, 템플릿 골격, 출력 DTO)을 그대로 적용한다. 여기서는 3부 고유 사항만 더한다.
- 시스템 화면은 전부 SUPER 전용. 모든 서비스의 `companyIdxAttribute()` 는 `null` 을 돌려준다. **예외:** `CCFA_SYSTEM_PROP` 은 복합키 안에 `COMPANY_IDX` 가 있으므로 `"id.companyIdx"` 를 돌려준다(1부 `Specs.eq` 가 점 경로를 지원한다). SUPER 만 접근하므로 테넌트 강제는 일어나지 않고, 검색 폼의 고객사 select 필터로만 쓰인다.
- 할당형 PK 화면(시스템 설정, 시스템 정보, 에러 코드, FIDO 서버)은 `AssignedIdCrudService` 를 상속하고 `assignedId()` 를 구현한다. 폼에서 식별자를 받되 **수정 화면에서는 식별자 입력을 `readonly` 로 두고, `applyForm` 에서 식별자를 바꾸지 않는다.**
- `idx` 가 없는 엔티티의 `defaultSort()`·`sortableProperties()` 를 반드시 재정의한다: 시스템 설정 `id.propKey`, 시스템 정보 `propKey`, 에러 코드 `errorCode`, FIDO 서버 `servercode`.
- 복합키 경로 값: `CcfaSystemPropId` 에 `toPathValue()`(`propKey + "@" + companyIdx`)와 `static parse(String)`(마지막 `@` 기준 분리)를 더한다. 엔티티 파일이 아니라 `@Embeddable` 클래스에 메서드만 추가하는 것이므로 `ErdConformanceTest` 에 영향이 없다. PROP_KEY 에 `/` 가 들어가는 키는 지원하지 않는다(경로 조각 제약, 등록 폼에서 거부).
- `CCFA_CRITERIA.JSONDATA` 편집은 `JsonPretty.isValidJson()` 으로 형식을 검증하고, 상세에서는 `JsonPretty.pretty()` 로 정리해 보여준다.
- `CCFA_OPTION` 상세 안에 `CCFA_OPTIONS` 인라인 목록·추가·삭제를 둔다. 하위 코드가 있는 코드 그룹은 삭제를 차단한다(고객사 삭제 차단과 같은 방식, `IllegalStateException`).
- `CCFA_MENU` 는 데이터로만 다룬다. 사이드바는 계속 `MenuRegistry` 고정 메뉴다.
- 커밋 메시지는 한국어 요약 + `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. 태스크마다 커밋한다.

## 파일 구조 (3부에서 생성·수정)

```
src/main/java/com/crosscert/fidoadmin/system/
├── entity/CcfaSystemPropId.java                      (수정) toPathValue(), parse() 추가
├── repository/CcfaSystemInfoRepository.java, CcfaErrorTableRepository.java, CcfaFidoclientRepository.java,
│              CcfaCriteriaRepository.java, CcfaMenuRepository.java, CcfaOptionRepository.java,
│              CcfaOptionsRepository.java, CcfaFieldsRepository.java
├── service/SystemPropService.java, SystemInfoService.java, ErrorCodeService.java, FidoClientService.java,
│           AdminCriteriaService.java, MenuService.java, OptionService.java, FieldService.java
└── web/SystemPropIdConverter.java,
        SystemPropController.java, SystemPropForm.java, SystemPropSearchForm.java, SystemPropRow.java,
        SystemInfoController.java, SystemInfoForm.java, SystemInfoSearchForm.java,
        ErrorCodeController.java, ErrorCodeForm.java, ErrorCodeSearchForm.java,
        FidoClientController.java, FidoClientForm.java, FidoClientSearchForm.java,
        AdminCriteriaController.java, AdminCriteriaForm.java, AdminCriteriaSearchForm.java, AdminCriteriaRow.java, AdminCriteriaView.java,
        MenuController.java, MenuForm.java, MenuSearchForm.java,
        OptionController.java, OptionForm.java, OptionSearchForm.java, OptionItemForm.java,
        FieldController.java, FieldForm.java, FieldSearchForm.java
src/main/resources/templates/system/
├── props/, info/, error-codes/, fido-clients/, criteria/, menus/, options/, fields/  (각 list.html, detail.html, form.html)
src/test/java/com/crosscert/fidoadmin/system/... 각 *ControllerWebTest, *ServiceTest, entity/CcfaSystemPropIdTest, web/SystemPropIdConverterTest
README.md, tasks/todo.md                                (최종 검증에서 갱신)
```

---
## Task 1: 시스템 설정 (CCFA_SYSTEM_PROP, 복합키)

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/system/entity/CcfaSystemPropId.java` (`toPathValue()`, `parse()`, `toString()` 추가 — `@Column` 필드는 손대지 않는다)
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropIdConverter.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/SystemPropService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemPropController.java`
- Create: `src/main/resources/templates/system/props/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/entity/CcfaSystemPropIdTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/SystemPropIdConverterTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/SystemPropServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/SystemPropControllerWebTest.java`

**Interfaces:**
- Consumes: 2부 Task 1 `AssignedIdCrudService<E, ID, S>`(생성자 `(AdminRepository, AuditLogger, EntityManager)`, 추상 `assignedId(E)`), `CrudController.validate(F, boolean, BindingResult)`, 1부 `CcfaSystemPropRepository extends AdminRepository<CcfaSystemProp, CcfaSystemPropId>`, `CompanyLookup.all()/names()`, `Specs.eq/like`(점 경로 지원).
- Produces:
  - `CcfaSystemPropId.toPathValue(): String` — `"{propKey}@{companyIdx}"`. `toString()` 도 같은 값을 돌려준다(`CrudController.update` 가 `"redirect:" + basePath() + "/" + id` 로 문자열 결합하기 때문).
  - `static CcfaSystemPropId.parse(String): CcfaSystemPropId` — 마지막 `@` 기준으로 분리. 형식이 틀리면 `IllegalArgumentException`.
  - `SystemPropIdConverter implements Converter<String, CcfaSystemPropId>` — `@PathVariable CcfaSystemPropId` 바인딩.
  - `SystemPropService.idOf(e)` = `e.getId().toPathValue()`. `SystemPropRow(propKey, companyIdx, propValue, shareType, updatedtime, pathValue)` 가 목록·상세 DTO.

- [ ] **Step 1: 복합키 경로 값 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/entity/CcfaSystemPropIdTest.java`

```java
package com.crosscert.fidoadmin.system.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CcfaSystemPropIdTest {

    @Test void toPathValueJoinsKeyAndCompany() {
        assertThat(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toPathValue()).isEqualTo("PW_FAIL_LIMIT@0");
        assertThat(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L).toString()).isEqualTo("PW_FAIL_LIMIT@0");
    }

    @Test void parseRoundTrips() {
        CcfaSystemPropId id = CcfaSystemPropId.parse("PW_FAIL_LIMIT@0");
        assertThat(id.getPropKey()).isEqualTo("PW_FAIL_LIMIT");
        assertThat(id.getCompanyIdx()).isEqualTo(0L);
        assertThat(id).isEqualTo(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L));
    }

    /** 키 자체에 '@' 가 있어도 마지막 '@' 뒤가 COMPANY_IDX 다. */
    @Test void parseSplitsAtLastAt() {
        CcfaSystemPropId id = CcfaSystemPropId.parse("a@b@12");
        assertThat(id.getPropKey()).isEqualTo("a@b");
        assertThat(id.getCompanyIdx()).isEqualTo(12L);
        assertThat(id.toPathValue()).isEqualTo("a@b@12");
    }

    @Test void parseRejectsMalformed() {
        assertThatThrownBy(() -> CcfaSystemPropId.parse("NO_AT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("@0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("KEY@x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse("KEY@")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CcfaSystemPropId.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.entity.CcfaSystemPropIdTest'`
Expected: FAIL — `toPathValue`, `parse` 메서드 없음.

- [ ] **Step 3: CcfaSystemPropId 에 메서드 추가**

`src/main/java/com/crosscert/fidoadmin/system/entity/CcfaSystemPropId.java` — 기존 필드·애너테이션은 그대로 두고 클래스 본문 끝에 메서드만 더한다. 전체 파일:

```java
package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class CcfaSystemPropId implements Serializable {
    @Column(name = "PROP_KEY", length = 128) private String propKey;
    @Column(name = "COMPANY_IDX") private Long companyIdx;

    /** URL 경로 한 조각으로 쓰는 표현. "{PROP_KEY}@{COMPANY_IDX}". */
    public String toPathValue() {
        return propKey + "@" + companyIdx;
    }

    /**
     * 경로 값 → 복합키. 키에 '@' 가 들어갈 수 있으므로 마지막 '@' 기준으로 나눈다.
     * 형식이 틀리면 IllegalArgumentException (컨트롤러에서는 400 으로 끝난다).
     */
    public static CcfaSystemPropId parse(String value) {
        if (value == null) throw new IllegalArgumentException("시스템 설정 키가 없습니다");
        int at = value.lastIndexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            throw new IllegalArgumentException("시스템 설정 키 형식이 아닙니다: " + value);
        }
        String key = value.substring(0, at);
        try {
            return new CcfaSystemPropId(key, Long.parseLong(value.substring(at + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("시스템 설정 키 형식이 아닙니다: " + value, e);
        }
    }

    /** CrudController 가 리다이렉트 경로를 "basePath/" + id 로 만들므로 경로 값과 같게 둔다. */
    @Override public String toString() { return toPathValue(); }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인 (ERD 정합성 포함)**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.entity.CcfaSystemPropIdTest' --tests 'com.crosscert.fidoadmin.erd.ErdConformanceTest'`
Expected: PASS — 컬럼 집합은 바뀌지 않았다.

- [ ] **Step 5: Converter 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/SystemPropIdConverterTest.java`

```java
package com.crosscert.fidoadmin.system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.junit.jupiter.api.Test;

class SystemPropIdConverterTest {

    SystemPropIdConverter converter = new SystemPropIdConverter();

    @Test void convertsPathValue() {
        assertThat(converter.convert("SERVICE_NAME@1")).isEqualTo(new CcfaSystemPropId("SERVICE_NAME", 1L));
    }

    @Test void rejectsMalformed() {
        assertThatThrownBy(() -> converter.convert("SERVICE_NAME")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemPropIdConverterTest'`
Expected: FAIL — `SystemPropIdConverter` 없음.

- [ ] **Step 7: Converter 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/SystemPropIdConverter.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * "/system/props/{id}" 의 경로 변수를 복합키로 바꾼다.
 * Converter 빈은 Spring Boot 가 FormatterRegistry 에 자동 등록한다(@WebMvcTest 도 포함).
 */
@Component
public class SystemPropIdConverter implements Converter<String, CcfaSystemPropId> {

    @Override
    public CcfaSystemPropId convert(String source) {
        return CcfaSystemPropId.parse(source);
    }
}
```

- [ ] **Step 8: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemPropIdConverterTest'`
Expected: PASS.

- [ ] **Step 9: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/SystemPropServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SystemPropServiceTest {

    CcfaSystemPropRepository repo = mock(CcfaSystemPropRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    SystemPropService service = new SystemPropService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemProp prop(String key, long company) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company));
        p.setPropValue("v");
        return p;
    }

    /** 같은 (PROP_KEY, COMPANY_IDX) 가 있으면 MERGE 로 덮어쓰지 않고 거부한다. */
    @Test void createRejectsDuplicateCompositeKey() {
        when(repo.existsById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(true);
        assertThatThrownBy(() -> service.create(prop("PW_FAIL_LIMIT", 0L)))
            .isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void createPersistsWithDefaultsAndAudits() {
        when(repo.existsById(any())).thenReturn(false);
        CcfaSystemProp saved = service.create(prop("PW_FAIL_LIMIT", 0L));
        assertThat(saved.getShareType()).isEqualTo("NO");
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_PROP CREATE PW_FAIL_LIMIT@0");
    }

    @Test void idOfIsPathValue() {
        assertThat(service.idOf(prop("SESSION_TIMEOUT", 0L))).isEqualTo("SESSION_TIMEOUT@0");
    }

    @Test void defaultSortIsByKeyThenCompany() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("id.propKey", "id.companyIdx", "updatedtime");
    }
}
```

- [ ] **Step 10: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.SystemPropServiceTest'`
Expected: FAIL — `SystemPropService`, `SystemPropSearchForm` 없음.

- [ ] **Step 11: 검색 폼과 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/SystemPropSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 시스템 설정 검색: 키(부분 일치), 고객사(기반 companyIdx). */
@Getter @Setter
public class SystemPropSearchForm extends SearchForm {
    private String propKey;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propKey", propKey);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/SystemPropService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import com.crosscert.fidoadmin.system.web.SystemPropSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CCFA_SYSTEM_PROP. 복합키(PROP_KEY, COMPANY_IDX) 가 폼에서 채워지므로 삽입 전용 경로를 쓴다.
 * SUPER 전용 URL(/system/**) 이지만 COMPANY_IDX 가 키 안에 있어 테넌트 속성은 "id.companyIdx" 로 둔다
 * (SUPER 의 고객사 필터에만 쓰인다).
 */
@Service
public class SystemPropService extends AssignedIdCrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> {

    public SystemPropService(CcfaSystemPropRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaSystemProp> toSpecification(SystemPropSearchForm f) {
        return Specs.all(Specs.like("id.propKey", f.getPropKey()));
    }
    @Override protected String companyIdxAttribute() { return "id.companyIdx"; }
    @Override protected Long companyIdxOf(CcfaSystemProp e) { return e.getId() == null ? null : e.getId().getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaSystemProp e, Long c) {
        if (e.getId() == null) e.setId(new CcfaSystemPropId());
        e.getId().setCompanyIdx(c);
    }
    @Override public String idOf(CcfaSystemProp e) { return e.getId().toPathValue(); }
    @Override protected CcfaSystemPropId assignedId(CcfaSystemProp e) {
        CcfaSystemPropId id = e.getId();
        if (id == null || id.getPropKey() == null || id.getPropKey().isBlank() || id.getCompanyIdx() == null) return null;
        return id;
    }
    @Override protected String tableName() { return "CCFA_SYSTEM_PROP"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"); }
    @Override public Set<String> sortableProperties() { return Set.of("id.propKey", "id.companyIdx", "updatedtime"); }

    @Override protected void applyDefaults(CcfaSystemProp e) {
        if (e.getShareType() == null || e.getShareType().isBlank()) e.setShareType("NO");
    }
    @Override protected void touchCreated(CcfaSystemProp e, LocalDateTime now) { e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaSystemProp e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 12: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.SystemPropServiceTest'`
Expected: PASS.

- [ ] **Step 13: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/SystemPropControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.service.SystemPropService;
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

@WebMvcTest(controllers = SystemPropController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
         SystemPropIdConverter.class})
class SystemPropControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SystemPropService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaSystemProp prop(String key, long company, String value) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company)); p.setPropValue(value); p.setShareType("YES");
        return p;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/props").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRowsWithPathLinks() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("id.propKey"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(prop("PW_FAIL_LIMIT", 0L, "5"))));
        when(companies.names()).thenReturn(Map.of(0L, "전역(시스템)"));
        mvc.perform(get("/system/props").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/list"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("/system/props/PW_FAIL_LIMIT@0")))
            .andExpect(content().string(containsString("전역(시스템)")));
    }

    /** 경로 변수 "PW_FAIL_LIMIT@0" 가 복합키로 변환되어 서비스에 전달된다. */
    @Test void detailConvertsCompositePathVariable() throws Exception {
        CcfaSystemPropId id = new CcfaSystemPropId("PW_FAIL_LIMIT", 0L);
        when(service.get(id)).thenReturn(prop("PW_FAIL_LIMIT", 0L, "5"));
        when(companies.name(0L)).thenReturn("전역(시스템)");
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@0").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/detail"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("전역(시스템)")));
        verify(service).get(id);
    }

    @Test void keyWithSlashIsRejected() throws Exception {
        mvc.perform(post("/system/props").with(user(superUser)).with(csrf())
                .param("propKey", "a/b").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키에 &#39;/&#39; 는 쓸 수 없습니다.")));
    }

    @Test void createRedirectsToCompositeDetail() throws Exception {
        when(service.create(any())).thenReturn(prop("NEW", 0L, "x"));
        when(service.idOf(any())).thenReturn("NEW@0");
        mvc.perform(post("/system/props").with(user(superUser)).with(csrf())
                .param("propKey", "NEW").param("companyIdx", "0").param("propValue", "x").param("shareType", "NO"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/NEW@0"));
    }

    /** update 리다이렉트는 CrudController 가 id.toString() 으로 만든다. toString 이 경로 값이어야 한다. */
    @Test void updateRedirectsToCompositeDetail() throws Exception {
        mvc.perform(post("/system/props/PW_FAIL_LIMIT@0").with(user(superUser)).with(csrf())
                .param("propKey", "PW_FAIL_LIMIT").param("companyIdx", "0").param("propValue", "7").param("shareType", "YES"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/PW_FAIL_LIMIT@0"));
    }
}
```

- [ ] **Step 14: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemPropControllerWebTest'`
Expected: FAIL — `SystemPropController`, `SystemPropForm`, `SystemPropRow` 없음.

- [ ] **Step 15: 폼, DTO, 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/SystemPropForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_SYSTEM_PROP 입력 폼. 식별자(propKey, companyIdx)는 등록 시에만 쓰이고 수정에서는 바뀌지 않는다. */
@Getter @Setter
public class SystemPropForm {
    @NotBlank @ByteSize(max = 128) private String propKey;
    @NotNull private Long companyIdx = 0L;
    @ByteSize(max = 4000) private String propValue;
    @NotBlank @ByteSize(max = 20) private String shareType = "NO";

    public static SystemPropForm from(CcfaSystemProp p) {
        SystemPropForm f = new SystemPropForm();
        f.propKey = p.getId().getPropKey(); f.companyIdx = p.getId().getCompanyIdx();
        f.propValue = p.getPropValue(); f.shareType = p.getShareType();
        return f;
    }

    /** 신규 엔티티. 식별자는 여기서만 채운다. */
    public CcfaSystemProp toNewEntity() {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(propKey == null ? null : propKey.trim(), companyIdx));
        applyTo(p);
        return p;
    }

    /** 수정 가능한 값만 반영한다. 식별자는 건드리지 않는다. */
    public void applyTo(CcfaSystemProp p) {
        p.setPropValue(propValue);
        p.setShareType(shareType);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/SystemPropRow.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import java.time.LocalDateTime;

/** 목록·상세 출력 DTO. pathValue 가 링크에 쓰인다. */
public record SystemPropRow(String propKey, Long companyIdx, String propValue, String shareType,
                            LocalDateTime updatedtime, String pathValue) {
    public static SystemPropRow of(CcfaSystemProp p) {
        return new SystemPropRow(p.getId().getPropKey(), p.getId().getCompanyIdx(), p.getPropValue(),
            p.getShareType(), p.getUpdatedtime(), p.getId().toPathValue());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/SystemPropController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.service.SystemPropService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/props")
@RequiredArgsConstructor
public class SystemPropController extends CrudController<CcfaSystemProp, CcfaSystemPropId, SystemPropForm, SystemPropSearchForm> {

    private final SystemPropService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/props"; }
    @Override protected String viewDir() { return "system/props"; }
    @Override protected SystemPropSearchForm newSearchForm() { return new SystemPropSearchForm(); }
    @Override protected SystemPropForm newForm() { return new SystemPropForm(); }
    @Override protected SystemPropForm toForm(CcfaSystemProp e) { return SystemPropForm.from(e); }
    @Override protected CcfaSystemProp toEntity(SystemPropForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(SystemPropForm f, CcfaSystemProp e) { f.applyTo(e); }
    @Override protected Object toListView(CcfaSystemProp e) { return SystemPropRow.of(e); }
    @Override protected Object toDetailView(CcfaSystemProp e) { return SystemPropRow.of(e); }

    /** 경로 조각으로 쓰이는 키라 '/' 는 받지 않는다. */
    @Override protected void validate(SystemPropForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getPropKey() != null && form.getPropKey().contains("/")) {
            binding.rejectValue("propKey", "path", "키에 '/' 는 쓸 수 없습니다.");
        }
    }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companies", companies.all());
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateFormModel(Model model) {
        model.addAttribute("companies", companies.all());
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(CcfaSystemProp e, Model model) {
        model.addAttribute("companyName", companies.name(e.getId().getCompanyIdx()));
    }
}
```

- [ ] **Step 16: 템플릿 작성**

`src/main/resources/templates/system/props/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>시스템 설정</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">시스템 설정</h1>
    <a th:href="@{/system/props/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/system/props}" class="row g-2 align-items-end mb-3">
    <div class="col-auto">
      <label class="form-label small mb-0">키</label>
      <input name="propKey" th:value="${search.propKey}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="|${c.companyName} (${c.idx})|" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/props}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>키</th><th>고객사</th><th>값</th><th>공유</th><th>수정일시</th></tr>
      </thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/system/props/{id}(id=${r.pathValue})}" th:text="${r.propKey}">키</a></td>
          <td th:text="|${companyNames.get(r.companyIdx) ?: '#' + r.companyIdx} (${r.companyIdx})|"></td>
          <td th:text="${#strings.abbreviate(r.propValue, 60)}"></td>
          <td th:text="${r.shareType}"></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm')}"></td>
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

`src/main/resources/templates/system/props/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>시스템 설정 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|시스템 설정 ${item.propKey}|">시스템 설정</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/props/{id}/edit(id=${item.pathValue})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/props/{id}/delete(id=${item.pathValue})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/props}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>키</th><td class="fa-mono" th:text="${item.propKey}"></td></tr>
        <tr><th>고객사</th><td th:text="|${companyName} (${item.companyIdx})|"></td></tr>
        <tr><th>값</th><td class="fa-pre" th:text="${item.propValue}"></td></tr>
        <tr><th>공유</th><td th:text="${item.shareType}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/props/form.html` — 수정 화면에서는 식별자를 바꿀 수 없으므로 키는 `readonly`, 고객사는 표시용 텍스트 + hidden 값으로 둔다. `id` 는 `CcfaSystemPropId` 객체이므로 `id.toPathValue()` 로 경로를 만든다.

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '시스템 설정 등록' : '시스템 설정 수정'">시스템 설정</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '시스템 설정 등록' : '시스템 설정 수정'">시스템 설정</h1>
  <form th:action="${isNew} ? @{/system/props} : @{/system/props/{id}(id=${id.toPathValue()})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">키 <span class="text-danger">*</span></label>
        <input th:field="*{propKey}" class="form-control" th:readonly="${!isNew}"
               th:classappend="${#fields.hasErrors('propKey')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{propKey}"></div>
      </div>
      <div class="col-md-6" th:if="${isNew}">
        <label class="form-label">고객사 <span class="text-danger">*</span></label>
        <select th:field="*{companyIdx}" class="form-select">
          <option th:each="c : ${companies}" th:value="${c.idx}" th:text="|${c.companyName} (${c.idx})|"></option>
        </select>
        <div class="invalid-feedback d-block" th:errors="*{companyIdx}"></div>
      </div>
      <div class="col-md-6" th:unless="${isNew}">
        <label class="form-label">고객사</label>
        <input class="form-control" readonly th:value="|${companyNames.get(form.companyIdx) ?: '#' + form.companyIdx} (${form.companyIdx})|">
        <input type="hidden" name="companyIdx" th:value="*{companyIdx}">
      </div>
      <div class="col-md-3">
        <label class="form-label">공유 <span class="text-danger">*</span></label>
        <select th:field="*{shareType}" class="form-select"><option value="NO">NO</option><option value="YES">YES</option></select>
      </div>
      <div class="col-12"><label class="form-label">값</label><textarea th:field="*{propValue}" rows="4" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/props} : @{/system/props/{id}(id=${id.toPathValue()})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 17: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemPropControllerWebTest'`
Expected: PASS.

- [ ] **Step 18: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/props src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 시스템 설정 화면 (CCFA_SYSTEM_PROP 복합키, 경로 값 변환, 삽입 전용 등록)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 2: 시스템 정보 (CCFA_SYSTEM_INFO)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaSystemInfoRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/SystemInfoService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoController.java`
- Create: `src/main/resources/templates/system/info/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/SystemInfoServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/SystemInfoControllerWebTest.java`

**Interfaces:**
- Consumes: `AssignedIdCrudService`, `CrudController`, 1부 엔티티 `CcfaSystemInfo(propKey, propValue, updatedtime)`.
- Produces: `CcfaSystemInfoRepository extends AdminRepository<CcfaSystemInfo, String>`; `SystemInfoService.idOf(e)` = `propKey`; 경로 `/system/info/{propKey}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/SystemInfoServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.repository.CcfaSystemInfoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SystemInfoServiceTest {

    CcfaSystemInfoRepository repo = mock(CcfaSystemInfoRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    SystemInfoService service = new SystemInfoService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemInfo info(String key) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue("v"); return i; }

    @Test void createRejectsDuplicateKey() {
        when(repo.existsById("VERSION")).thenReturn(true);
        assertThatThrownBy(() -> service.create(info("VERSION"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
    }

    @Test void createPersistsAndAudits() {
        when(repo.existsById("BUILD_NO")).thenReturn(false);
        CcfaSystemInfo saved = service.create(info("BUILD_NO"));
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_INFO CREATE BUILD_NO");
    }

    @Test void updateTouchesTimestamp() {
        when(repo.findById("VERSION")).thenReturn(java.util.Optional.of(info("VERSION")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaSystemInfo out = service.update("VERSION", i -> i.setPropValue("1.1.0"));
        assertThat(out.getPropValue()).isEqualTo("1.1.0");
        assertThat(out.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.UPDATE, "CCFA_SYSTEM_INFO UPDATE VERSION");
    }

    @Test void sortDefaultsToKey() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "propKey"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("propKey", "updatedtime");
    }

    /** COMPANY_IDX 가 없는 테이블: COMPANY 역할은 서비스 계층에서도 거부된다. */
    @Test void companyRoleIsDeniedAtServiceLayer() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        assertThatThrownBy(() -> service.get("VERSION"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.SystemInfoServiceTest'`
Expected: FAIL — 리포지토리·서비스·검색 폼 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaSystemInfoRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;

public interface CcfaSystemInfoRepository extends AdminRepository<CcfaSystemInfo, String> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SystemInfoSearchForm extends SearchForm {
    private String propKey;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propKey", propKey);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/SystemInfoService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.repository.CcfaSystemInfoRepository;
import com.crosscert.fidoadmin.system.web.SystemInfoSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_SYSTEM_INFO — 문자열 PK(PROP_KEY). COMPANY_IDX 없음 → SUPER 전용. */
@Service
public class SystemInfoService extends AssignedIdCrudService<CcfaSystemInfo, String, SystemInfoSearchForm> {

    public SystemInfoService(CcfaSystemInfoRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaSystemInfo> toSpecification(SystemInfoSearchForm f) {
        return Specs.all(Specs.like("propKey", f.getPropKey()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaSystemInfo e) { return null; }
    @Override protected void setCompanyIdx(CcfaSystemInfo e, Long c) {}
    @Override public String idOf(CcfaSystemInfo e) { return e.getPropKey(); }
    @Override protected String assignedId(CcfaSystemInfo e) { return e.getPropKey(); }
    @Override protected String tableName() { return "CCFA_SYSTEM_INFO"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "propKey"); }
    @Override public Set<String> sortableProperties() { return Set.of("propKey", "updatedtime"); }
    @Override protected void touchCreated(CcfaSystemInfo e, LocalDateTime now) { e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaSystemInfo e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.SystemInfoServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/SystemInfoControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

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
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.service.SystemInfoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SystemInfoController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class SystemInfoControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SystemInfoService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaSystemInfo info(String key, String value) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue(value); return i; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/info").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("propKey"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(info("VERSION", "1.0.0"))));
        mvc.perform(get("/system/info").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/list"))
            .andExpect(content().string(containsString("VERSION")))
            .andExpect(content().string(containsString("/system/info/VERSION")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("VERSION")).thenReturn(info("VERSION", "1.0.0"));
        mvc.perform(get("/system/info/VERSION").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/detail"))
            .andExpect(content().string(containsString("1.0.0")));
    }

    @Test void blankKeyShowsFormAgain() throws Exception {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(info("NEW_KEY", "x"));
        when(service.idOf(any())).thenReturn("NEW_KEY");
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "NEW_KEY").param("propValue", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/info/NEW_KEY"));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemInfoControllerWebTest'`
Expected: FAIL — `SystemInfoController`, `SystemInfoForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_SYSTEM_INFO 입력 폼. propKey 는 등록 시에만 쓰인다. */
@Getter @Setter
public class SystemInfoForm {
    @NotBlank @ByteSize(max = 128) private String propKey;
    @ByteSize(max = 1024) private String propValue;

    public static SystemInfoForm from(CcfaSystemInfo i) {
        SystemInfoForm f = new SystemInfoForm();
        f.propKey = i.getPropKey(); f.propValue = i.getPropValue();
        return f;
    }

    public CcfaSystemInfo toNewEntity() {
        CcfaSystemInfo i = new CcfaSystemInfo();
        i.setPropKey(propKey == null ? null : propKey.trim());
        applyTo(i);
        return i;
    }

    /** 식별자는 바꾸지 않는다. */
    public void applyTo(CcfaSystemInfo i) { i.setPropValue(propValue); }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/SystemInfoController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.service.SystemInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/info")
@RequiredArgsConstructor
public class SystemInfoController extends CrudController<CcfaSystemInfo, String, SystemInfoForm, SystemInfoSearchForm> {

    private final SystemInfoService service;

    @Override protected CrudService<CcfaSystemInfo, String, SystemInfoSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/info"; }
    @Override protected String viewDir() { return "system/info"; }
    @Override protected SystemInfoSearchForm newSearchForm() { return new SystemInfoSearchForm(); }
    @Override protected SystemInfoForm newForm() { return new SystemInfoForm(); }
    @Override protected SystemInfoForm toForm(CcfaSystemInfo e) { return SystemInfoForm.from(e); }
    @Override protected CcfaSystemInfo toEntity(SystemInfoForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(SystemInfoForm f, CcfaSystemInfo e) { f.applyTo(e); }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/info/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>시스템 정보</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">시스템 정보</h1>
    <a th:href="@{/system/info/new}" class="btn btn-primary btn-sm">등록</a>
  </div>
  <form method="get" th:action="@{/system/info}" class="row g-2 align-items-end mb-3">
    <div class="col-auto">
      <label class="form-label small mb-0">키</label>
      <input name="propKey" th:value="${search.propKey}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/info}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>키</th><th>값</th><th>수정일시</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/system/info/{id}(id=${r.propKey})}" th:text="${r.propKey}">키</a></td>
          <td th:text="${#strings.abbreviate(r.propValue, 80)}"></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="3" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
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

`src/main/resources/templates/system/info/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>시스템 정보 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|시스템 정보 ${item.propKey}|">시스템 정보</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/info/{id}/edit(id=${item.propKey})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/info/{id}/delete(id=${item.propKey})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/info}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>키</th><td class="fa-mono" th:text="${item.propKey}"></td></tr>
      <tr><th>값</th><td class="fa-pre" th:text="${item.propValue}"></td></tr>
      <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/info/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '시스템 정보 등록' : '시스템 정보 수정'">시스템 정보</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '시스템 정보 등록' : '시스템 정보 수정'">시스템 정보</h1>
  <form th:action="${isNew} ? @{/system/info} : @{/system/info/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 640px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-12">
        <label class="form-label">키 <span class="text-danger">*</span></label>
        <input th:field="*{propKey}" class="form-control" th:readonly="${!isNew}"
               th:classappend="${#fields.hasErrors('propKey')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{propKey}"></div>
      </div>
      <div class="col-12"><label class="form-label">값</label><textarea th:field="*{propValue}" rows="3" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/info} : @{/system/info/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.SystemInfoControllerWebTest'`
Expected: PASS.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/info src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 시스템 정보 화면 (CCFA_SYSTEM_INFO 문자열 PK, 삽입 전용 등록)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 3: 에러 코드 (CCFA_ERROR_TABLE)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaErrorTableRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/ErrorCodeService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeController.java`
- Create: `src/main/resources/templates/system/error-codes/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/ErrorCodeServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/ErrorCodeControllerWebTest.java`

**Interfaces:**
- Consumes: `AssignedIdCrudService`, `CrudController`, 엔티티 `CcfaErrorTable(errorCode, errorMessage, errorComment, errorType)` — 타임스탬프 컬럼이 없어 `touchCreated/touchUpdated` 는 재정의하지 않는다.
- Produces: `CcfaErrorTableRepository extends AdminRepository<CcfaErrorTable, String>`; `ErrorCodeService.idOf(e)` = `errorCode`; 경로 `/system/error-codes/{errorCode}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/ErrorCodeServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ErrorCodeServiceTest {

    CcfaErrorTableRepository repo = mock(CcfaErrorTableRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    ErrorCodeService service = new ErrorCodeService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaErrorTable code(String c) { CcfaErrorTable e = new CcfaErrorTable(); e.setErrorCode(c); e.setErrorMessage("m"); return e; }

    @Test void createRejectsDuplicateCode() {
        when(repo.existsById("1200")).thenReturn(true);
        assertThatThrownBy(() -> service.create(code("1200"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
    }

    @Test void createPersistsAndAudits() {
        when(repo.existsById("1499")).thenReturn(false);
        CcfaErrorTable saved = service.create(code("1499"));
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_ERROR_TABLE CREATE 1499");
    }

    @Test void deleteAudits() {
        when(repo.findById("1498")).thenReturn(java.util.Optional.of(code("1498")));
        service.delete("1498");
        verify(repo).delete(any(CcfaErrorTable.class));
        verify(audit).log(AuditType.DELETE, "CCFA_ERROR_TABLE DELETE 1498");
    }

    @Test void sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "errorCode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("errorCode", "errorType");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.ErrorCodeServiceTest'`
Expected: FAIL — 리포지토리·서비스·검색 폼 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaErrorTableRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;

public interface CcfaErrorTableRepository extends AdminRepository<CcfaErrorTable, String> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ErrorCodeSearchForm extends SearchForm {
    private String errorCode;
    private String errorType;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errorCode", errorCode); m.put("errorType", errorType);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/ErrorCodeService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository;
import com.crosscert.fidoadmin.system.web.ErrorCodeSearchForm;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_ERROR_TABLE — 문자열 PK(ERROR_CODE). 타임스탬프 컬럼 없음. SUPER 전용. */
@Service
public class ErrorCodeService extends AssignedIdCrudService<CcfaErrorTable, String, ErrorCodeSearchForm> {

    public ErrorCodeService(CcfaErrorTableRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaErrorTable> toSpecification(ErrorCodeSearchForm f) {
        return Specs.all(
            Specs.like("errorCode", f.getErrorCode()),
            Specs.like("errorType", f.getErrorType()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaErrorTable e) { return null; }
    @Override protected void setCompanyIdx(CcfaErrorTable e, Long c) {}
    @Override public String idOf(CcfaErrorTable e) { return e.getErrorCode(); }
    @Override protected String assignedId(CcfaErrorTable e) { return e.getErrorCode(); }
    @Override protected String tableName() { return "CCFA_ERROR_TABLE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "errorCode"); }
    @Override public Set<String> sortableProperties() { return Set.of("errorCode", "errorType"); }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.ErrorCodeServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/ErrorCodeControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

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
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.service.ErrorCodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorCodeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class ErrorCodeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean ErrorCodeService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaErrorTable code(String c, String msg) { CcfaErrorTable e = new CcfaErrorTable(); e.setErrorCode(c); e.setErrorMessage(msg); e.setErrorType("ERROR"); return e; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/error-codes").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("errorCode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(code("1498", "INVALID_SIGNATURE"))));
        mvc.perform(get("/system/error-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/list"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")))
            .andExpect(content().string(containsString("/system/error-codes/1498")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("1498")).thenReturn(code("1498", "INVALID_SIGNATURE"));
        mvc.perform(get("/system/error-codes/1498").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/detail"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")));
    }

    @Test void codeOverByteLimitShowsFormAgain() throws Exception {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf())
                .param("errorCode", "1".repeat(21)).param("errorMessage", "x"))   // VARCHAR2(20)
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(code("1499", "x"));
        when(service.idOf(any())).thenReturn("1499");
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "1499").param("errorMessage", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/error-codes/1499"));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.ErrorCodeControllerWebTest'`
Expected: FAIL — `ErrorCodeController`, `ErrorCodeForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_ERROR_TABLE 입력 폼. errorCode 는 등록 시에만 쓰인다. */
@Getter @Setter
public class ErrorCodeForm {
    @NotBlank @ByteSize(max = 20) private String errorCode;
    @ByteSize(max = 1024) private String errorMessage;
    @ByteSize(max = 4000) private String errorComment;
    @ByteSize(max = 20) private String errorType;

    public static ErrorCodeForm from(CcfaErrorTable e) {
        ErrorCodeForm f = new ErrorCodeForm();
        f.errorCode = e.getErrorCode(); f.errorMessage = e.getErrorMessage();
        f.errorComment = e.getErrorComment(); f.errorType = e.getErrorType();
        return f;
    }

    public CcfaErrorTable toNewEntity() {
        CcfaErrorTable e = new CcfaErrorTable();
        e.setErrorCode(errorCode == null ? null : errorCode.trim());
        applyTo(e);
        return e;
    }

    /** 식별자는 바꾸지 않는다. */
    public void applyTo(CcfaErrorTable e) {
        e.setErrorMessage(errorMessage); e.setErrorComment(errorComment); e.setErrorType(errorType);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/ErrorCodeController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.service.ErrorCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/error-codes")
@RequiredArgsConstructor
public class ErrorCodeController extends CrudController<CcfaErrorTable, String, ErrorCodeForm, ErrorCodeSearchForm> {

    private final ErrorCodeService service;

    @Override protected CrudService<CcfaErrorTable, String, ErrorCodeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/error-codes"; }
    @Override protected String viewDir() { return "system/error-codes"; }
    @Override protected ErrorCodeSearchForm newSearchForm() { return new ErrorCodeSearchForm(); }
    @Override protected ErrorCodeForm newForm() { return new ErrorCodeForm(); }
    @Override protected ErrorCodeForm toForm(CcfaErrorTable e) { return ErrorCodeForm.from(e); }
    @Override protected CcfaErrorTable toEntity(ErrorCodeForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(ErrorCodeForm f, CcfaErrorTable e) { f.applyTo(e); }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/error-codes/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>에러 코드</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">에러 코드</h1>
    <a th:href="@{/system/error-codes/new}" class="btn btn-primary btn-sm">등록</a>
  </div>
  <form method="get" th:action="@{/system/error-codes}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">코드</label><input name="errorCode" th:value="${search.errorCode}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">유형</label><input name="errorType" th:value="${search.errorType}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/error-codes}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>코드</th><th>메시지</th><th>유형</th><th>설명</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/system/error-codes/{id}(id=${r.errorCode})}" th:text="${r.errorCode}">코드</a></td>
          <td th:text="${r.errorMessage}"></td>
          <td><span class="badge text-bg-secondary" th:text="${r.errorType}"></span></td>
          <td th:text="${#strings.abbreviate(r.errorComment, 60)}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="4" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
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

`src/main/resources/templates/system/error-codes/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>에러 코드 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|에러 코드 ${item.errorCode}|">에러 코드</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/error-codes/{id}/edit(id=${item.errorCode})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/error-codes/{id}/delete(id=${item.errorCode})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/error-codes}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>코드</th><td class="fa-mono" th:text="${item.errorCode}"></td></tr>
      <tr><th>메시지</th><td th:text="${item.errorMessage}"></td></tr>
      <tr><th>설명</th><td class="fa-pre" th:text="${item.errorComment}"></td></tr>
      <tr><th>유형</th><td th:text="${item.errorType}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/error-codes/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '에러 코드 등록' : '에러 코드 수정'">에러 코드</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '에러 코드 등록' : '에러 코드 수정'">에러 코드</h1>
  <form th:action="${isNew} ? @{/system/error-codes} : @{/system/error-codes/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-4">
        <label class="form-label">코드 <span class="text-danger">*</span></label>
        <input th:field="*{errorCode}" class="form-control" th:readonly="${!isNew}"
               th:classappend="${#fields.hasErrors('errorCode')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{errorCode}"></div>
      </div>
      <div class="col-md-4"><label class="form-label">유형</label><input th:field="*{errorType}" class="form-control"></div>
      <div class="col-12"><label class="form-label">메시지</label><input th:field="*{errorMessage}" class="form-control"></div>
      <div class="col-12"><label class="form-label">설명</label><textarea th:field="*{errorComment}" rows="4" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/error-codes} : @{/system/error-codes/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.ErrorCodeControllerWebTest'`
Expected: PASS.

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/error-codes src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 에러 코드 화면 (CCFA_ERROR_TABLE 문자열 PK, 삽입 전용 등록)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 4: FIDO 서버 (CCFA_FIDOCLIENT)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFidoclientRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/FidoClientService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FidoClientSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FidoClientForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FidoClientController.java`
- Create: `src/main/resources/templates/system/fido-clients/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/FidoClientServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/FidoClientControllerWebTest.java`

**Interfaces:**
- Consumes: `AssignedIdCrudService`, `CrudController`, 엔티티 `CcfaFidoclient(servercode, servername, serverurl, status, createdtime, updatedtime)`.
- Produces: `CcfaFidoclientRepository extends AdminRepository<CcfaFidoclient, String>`; `FidoClientService.idOf(e)` = `servercode`; 기본값 `status = "ON"`; 경로 `/system/fido-clients/{servercode}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/FidoClientServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FidoClientServiceTest {

    CcfaFidoclientRepository repo = mock(CcfaFidoclientRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    FidoClientService service = new FidoClientService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaFidoclient client(String code) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServername("n"); c.setServerurl("https://x"); return c;
    }

    @Test void createRejectsDuplicateCode() {
        when(repo.existsById("FIDO01")).thenReturn(true);
        assertThatThrownBy(() -> service.create(client("FIDO01"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
    }

    @Test void createFillsDefaultsAndAudits() {
        when(repo.existsById("FIDO03")).thenReturn(false);
        CcfaFidoclient saved = service.create(client("FIDO03"));
        assertThat(saved.getStatus()).isEqualTo("ON");
        assertThat(saved.getCreatedtime()).isNotNull();
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_FIDOCLIENT CREATE FIDO03");
    }

    @Test void createKeepsGivenStatus() {
        when(repo.existsById("FIDO04")).thenReturn(false);
        CcfaFidoclient c = client("FIDO04"); c.setStatus("OFF");
        assertThat(service.create(c).getStatus()).isEqualTo("OFF");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        CcfaFidoclient existing = client("FIDO01");
        existing.setCreatedtime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById("FIDO01")).thenReturn(java.util.Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaFidoclient out = service.update("FIDO01", c -> c.setStatus("OFF"));
        assertThat(out.getCreatedtime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(out.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.UPDATE, "CCFA_FIDOCLIENT UPDATE FIDO01");
    }

    @Test void sortDefaultsToCode() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "servercode"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("servercode", "servername", "status", "updatedtime");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.FidoClientServiceTest'`
Expected: FAIL — 리포지토리·서비스·검색 폼 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFidoclientRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;

public interface CcfaFidoclientRepository extends AdminRepository<CcfaFidoclient, String> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/FidoClientSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FidoClientSearchForm extends SearchForm {
    private String servercode;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("servercode", servercode); m.put("status", status);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/FidoClientService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import com.crosscert.fidoadmin.system.web.FidoClientSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_FIDOCLIENT — 문자열 PK(SERVERCODE). STATUS 기본 'ON'. SUPER 전용. */
@Service
public class FidoClientService extends AssignedIdCrudService<CcfaFidoclient, String, FidoClientSearchForm> {

    public FidoClientService(CcfaFidoclientRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaFidoclient> toSpecification(FidoClientSearchForm f) {
        return Specs.all(
            Specs.like("servercode", f.getServercode()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaFidoclient e) { return null; }
    @Override protected void setCompanyIdx(CcfaFidoclient e, Long c) {}
    @Override public String idOf(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String assignedId(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String tableName() { return "CCFA_FIDOCLIENT"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "servercode"); }
    @Override public Set<String> sortableProperties() { return Set.of("servercode", "servername", "status", "updatedtime"); }

    @Override protected void applyDefaults(CcfaFidoclient e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("ON");
    }
    @Override protected void touchCreated(CcfaFidoclient e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaFidoclient e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.FidoClientServiceTest'`
Expected: PASS.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/FidoClientControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

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
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.service.FidoClientService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FidoClientController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class FidoClientControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FidoClientService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFidoclient client(String code, String name) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServername(name);
        c.setServerurl("https://fido1.internal:8443"); c.setStatus("ON"); return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/fido-clients").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("servercode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(client("FIDO01", "FIDO 서버 1"))));
        mvc.perform(get("/system/fido-clients").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/list"))
            .andExpect(content().string(containsString("FIDO 서버 1")))
            .andExpect(content().string(containsString("/system/fido-clients/FIDO01")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("FIDO01")).thenReturn(client("FIDO01", "FIDO 서버 1"));
        mvc.perform(get("/system/fido-clients/FIDO01").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/detail"))
            .andExpect(content().string(containsString("https://fido1.internal:8443")));
    }

    /** SERVERNAME·SERVERURL 은 NOT NULL. 비우면 저장 전에 폼에서 잡는다. */
    @Test void missingRequiredFieldsShowFormAgain() throws Exception {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "").param("serverurl", "").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fido-clients/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(client("FIDO03", "x"));
        when(service.idOf(any())).thenReturn("FIDO03");
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "FIDO03").param("servername", "FIDO 서버 3").param("serverurl", "https://fido3.internal:8443").param("status", "ON"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fido-clients/FIDO03"));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.FidoClientControllerWebTest'`
Expected: FAIL — `FidoClientController`, `FidoClientForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/FidoClientForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_FIDOCLIENT 입력 폼. servercode 는 등록 시에만 쓰인다. */
@Getter @Setter
public class FidoClientForm {
    @NotBlank @ByteSize(max = 64) private String servercode;
    @NotBlank @ByteSize(max = 1024) private String servername;
    @NotBlank @ByteSize(max = 2048) private String serverurl;
    @NotBlank @ByteSize(max = 4) private String status = "ON";

    public static FidoClientForm from(CcfaFidoclient c) {
        FidoClientForm f = new FidoClientForm();
        f.servercode = c.getServercode(); f.servername = c.getServername();
        f.serverurl = c.getServerurl(); f.status = c.getStatus();
        return f;
    }

    public CcfaFidoclient toNewEntity() {
        CcfaFidoclient c = new CcfaFidoclient();
        c.setServercode(servercode == null ? null : servercode.trim());
        applyTo(c);
        return c;
    }

    /** 식별자는 바꾸지 않는다. */
    public void applyTo(CcfaFidoclient c) {
        c.setServername(servername); c.setServerurl(serverurl); c.setStatus(status);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/FidoClientController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.service.FidoClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/fido-clients")
@RequiredArgsConstructor
public class FidoClientController extends CrudController<CcfaFidoclient, String, FidoClientForm, FidoClientSearchForm> {

    private final FidoClientService service;

    @Override protected CrudService<CcfaFidoclient, String, FidoClientSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/fido-clients"; }
    @Override protected String viewDir() { return "system/fido-clients"; }
    @Override protected FidoClientSearchForm newSearchForm() { return new FidoClientSearchForm(); }
    @Override protected FidoClientForm newForm() { return new FidoClientForm(); }
    @Override protected FidoClientForm toForm(CcfaFidoclient e) { return FidoClientForm.from(e); }
    @Override protected CcfaFidoclient toEntity(FidoClientForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(FidoClientForm f, CcfaFidoclient e) { f.applyTo(e); }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/fido-clients/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO 서버</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">FIDO 서버</h1>
    <a th:href="@{/system/fido-clients/new}" class="btn btn-primary btn-sm">등록</a>
  </div>
  <form method="get" th:action="@{/system/fido-clients}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">코드</label><input name="servercode" th:value="${search.servercode}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">상태</label>
      <select name="status" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="ON" th:selected="${search.status == 'ON'}">ON</option>
        <option value="OFF" th:selected="${search.status == 'OFF'}">OFF</option>
      </select>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/fido-clients}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>코드</th><th>이름</th><th>URL</th><th>상태</th><th>수정일시</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/system/fido-clients/{id}(id=${r.servercode})}" th:text="${r.servercode}">코드</a></td>
          <td th:text="${r.servername}"></td>
          <td class="fa-mono" th:text="${#strings.abbreviate(r.serverurl, 60)}"></td>
          <td><span class="badge" th:classappend="${r.status == 'ON'} ? 'text-bg-success' : 'text-bg-secondary'" th:text="${r.status}"></span></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm')}"></td>
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

`src/main/resources/templates/system/fido-clients/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>FIDO 서버 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|FIDO 서버 ${item.servercode}|">FIDO 서버</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/fido-clients/{id}/edit(id=${item.servercode})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/fido-clients/{id}/delete(id=${item.servercode})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/fido-clients}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>코드</th><td class="fa-mono" th:text="${item.servercode}"></td></tr>
      <tr><th>이름</th><td th:text="${item.servername}"></td></tr>
      <tr><th>URL</th><td class="fa-mono" th:text="${item.serverurl}"></td></tr>
      <tr><th>상태</th><td th:text="${item.status}"></td></tr>
      <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/fido-clients/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? 'FIDO 서버 등록' : 'FIDO 서버 수정'">FIDO 서버</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? 'FIDO 서버 등록' : 'FIDO 서버 수정'">FIDO 서버</h1>
  <form th:action="${isNew} ? @{/system/fido-clients} : @{/system/fido-clients/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-4">
        <label class="form-label">코드 <span class="text-danger">*</span></label>
        <input th:field="*{servercode}" class="form-control" th:readonly="${!isNew}"
               th:classappend="${#fields.hasErrors('servercode')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{servercode}"></div>
      </div>
      <div class="col-md-3">
        <label class="form-label">상태 <span class="text-danger">*</span></label>
        <select th:field="*{status}" class="form-select"><option value="ON">ON</option><option value="OFF">OFF</option></select>
      </div>
      <div class="col-12">
        <label class="form-label">이름 <span class="text-danger">*</span></label>
        <input th:field="*{servername}" class="form-control" th:classappend="${#fields.hasErrors('servername')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{servername}"></div>
      </div>
      <div class="col-12">
        <label class="form-label">URL <span class="text-danger">*</span></label>
        <input th:field="*{serverurl}" class="form-control" th:classappend="${#fields.hasErrors('serverurl')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{serverurl}"></div>
      </div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/fido-clients} : @{/system/fido-clients/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.FidoClientControllerWebTest'`
Expected: PASS.

- [ ] **Step 10: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/fido-clients src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: FIDO 서버 화면 (CCFA_FIDOCLIENT 문자열 PK, 삽입 전용 등록, 상태 기본 ON)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 5: 어드민 기준 (CCFA_CRITERIA, JSONDATA 편집)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaCriteriaRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/AdminCriteriaService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaRow.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaView.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaController.java`
- Create: `src/main/resources/templates/system/criteria/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/AdminCriteriaServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/AdminCriteriaControllerWebTest.java`

**Interfaces:**
- Consumes: `CrudService`, `CrudController.validate(F, boolean, BindingResult)`, `JsonPretty.pretty(String)`, `JsonPretty.isValidJson(String)`, 1부 엔티티 `CcfaCriteria(idx, aaid, metahash, jsondata, createtime, updatedtime)`.
- Produces: `CcfaCriteriaRepository extends AdminRepository<CcfaCriteria, Long>`; `AdminCriteriaService` (SUPER 전용, `companyIdxAttribute()` null); 경로 `/system/criteria`; `AdminCriteriaRow(idx, aaid, metahash, updatedtime)`, `AdminCriteriaView(idx, aaid, metahash, jsondataPretty, createtime, updatedtime)`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/AdminCriteriaServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository;
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm;
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

class AdminCriteriaServiceTest {

    CcfaCriteriaRepository repo = mock(CcfaCriteriaRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    AdminCriteriaService service = new AdminCriteriaService(repo, audit);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaCriteria criteria(long idx) { CcfaCriteria c = new CcfaCriteria(); c.setIdx(idx); c.setAaid("0012#0001"); return c; }

    @Test void createSetsBothTimestampsAndAudits() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaCriteria c = inv.getArgument(0); c.setIdx(7L); return c; });
        CcfaCriteria out = service.create(criteria(0L));
        assertThat(out.getCreatetime()).isNotNull();
        assertThat(out.getUpdatedtime()).isEqualTo(out.getCreatetime());
        verify(audit).log(AuditType.CREATE, "CCFA_CRITERIA CREATE 7");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        login(0L);
        CcfaCriteria existing = criteria(7L);
        existing.setCreatetime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById(7L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(7L, c -> c.setMetahash("h2"));
        assertThat(existing.getCreatetime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(existing.getUpdatedtime()).isAfter(existing.getCreatetime());
        verify(audit).log(AuditType.UPDATE, "CCFA_CRITERIA UPDATE 7");
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용(설계 3.3). */
    @Test void companyRoleIsDenied() {
        login(1L);
        assertThatThrownBy(() -> service.search(new AdminCriteriaSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(7L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void superSearchesWithoutTenantFilter() {
        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(java.util.List.of()));
        AdminCriteriaSearchForm f = new AdminCriteriaSearchForm();
        f.setAaid("0012");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
        assertThat(service.sortableProperties()).contains("idx", "aaid", "updatedtime");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.AdminCriteriaServiceTest'`
Expected: FAIL — `AdminCriteriaService`, `CcfaCriteriaRepository`, `AdminCriteriaSearchForm` 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaCriteriaRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;

public interface CcfaCriteriaRepository extends AdminRepository<CcfaCriteria, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AdminCriteriaSearchForm extends SearchForm {
    private String aaid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aaid", aaid);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/AdminCriteriaService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository;
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_CRITERIA — 어드민 인증기기 정책. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class AdminCriteriaService extends CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm> {

    public AdminCriteriaService(CcfaCriteriaRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<CcfaCriteria> toSpecification(AdminCriteriaSearchForm f) {
        return Specs.all(Specs.like("aaid", f.getAaid()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaCriteria e) { return null; }
    @Override protected void setCompanyIdx(CcfaCriteria e, Long c) {}
    @Override public String idOf(CcfaCriteria e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_CRITERIA"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "aaid", "updatedtime"); }

    @Override protected void touchCreated(CcfaCriteria e, LocalDateTime now) { e.setCreatetime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaCriteria e, LocalDateTime now) { e.setUpdatedtime(now); }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.AdminCriteriaServiceTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/AdminCriteriaControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.service.AdminCriteriaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminCriteriaController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class AdminCriteriaControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminCriteriaService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaCriteria criteria(long idx, String json) {
        CcfaCriteria c = new CcfaCriteria(); c.setIdx(idx); c.setAaid("0012#0001"); c.setMetahash("hash-0001"); c.setJsondata(json);
        return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/criteria").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록에는 CLOB(JSONDATA) 이 실리지 않는다. */
    @Test void listRendersRowsWithoutJson() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(criteria(1L, "{\"marker\":\"JSON-LIST-MARKER\"}"))));
        mvc.perform(get("/system/criteria").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(not(containsString("JSON-LIST-MARKER"))));
    }

    /** th:text 는 따옴표를 &quot; 로 이스케이프한다. */
    @Test void detailShowsPrettyJson() throws Exception {
        when(service.get(1L)).thenReturn(criteria(1L, "{\"aaid\":\"0012#0001\"}"));
        mvc.perform(get("/system/criteria/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")));
    }

    @Test void invalidJsonShowsFormAgain() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0001").param("jsondata", "{not json"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"))
            .andExpect(content().string(containsString("올바른 JSON(객체 또는 배열)이어야 합니다.")));
        verify(service, never()).create(any());
    }

    @Test void blankAaidShowsFormAgain() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf()).param("aaid", "").param("jsondata", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/criteria/form"));
    }

    @Test void validJsonCreateRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(criteria(9L, "{}"));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/criteria").with(user(superUser)).with(csrf())
                .param("aaid", "0012#0009").param("metahash", "h").param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/criteria/9"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/criteria").with(user(superUser)).param("aaid", "x")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.AdminCriteriaControllerWebTest'`
Expected: FAIL — `AdminCriteriaController`, `AdminCriteriaForm` 없음.

- [ ] **Step 7: 폼, DTO, 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_CRITERIA 입력 폼. JSONDATA 는 CLOB 이라 길이 제한이 없고 형식만 컨트롤러가 검증한다. */
@Getter @Setter
public class AdminCriteriaForm {
    @NotBlank @ByteSize(max = 64) private String aaid;
    @ByteSize(max = 512) private String metahash;
    private String jsondata;

    public static AdminCriteriaForm from(CcfaCriteria c) {
        AdminCriteriaForm f = new AdminCriteriaForm();
        f.aaid = c.getAaid(); f.metahash = c.getMetahash(); f.jsondata = c.getJsondata();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaCriteria toNewEntity() {
        CcfaCriteria c = new CcfaCriteria();
        applyTo(c);
        return c;
    }

    public void applyTo(CcfaCriteria c) {
        c.setAaid(aaid); c.setMetahash(metahash);
        c.setJsondata(jsondata == null || jsondata.isBlank() ? null : jsondata); // EMPTY_CLOB 과 null 을 같게 본다
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaRow.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import java.time.LocalDateTime;

/** 목록용 DTO. CLOB(JSONDATA) 제외. */
public record AdminCriteriaRow(Long idx, String aaid, String metahash, LocalDateTime updatedtime) {
    public static AdminCriteriaRow of(CcfaCriteria c) {
        return new AdminCriteriaRow(c.getIdx(), c.getAaid(), c.getMetahash(), c.getUpdatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaView.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import java.time.LocalDateTime;

/** 상세용 DTO. JSONDATA 는 정리해서 보여준다. */
public record AdminCriteriaView(Long idx, String aaid, String metahash, String jsondataPretty,
                                LocalDateTime createtime, LocalDateTime updatedtime) {
    public static AdminCriteriaView of(CcfaCriteria c) {
        return new AdminCriteriaView(c.getIdx(), c.getAaid(), c.getMetahash(), JsonPretty.pretty(c.getJsondata()),
            c.getCreatetime(), c.getUpdatedtime());
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/AdminCriteriaController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.service.AdminCriteriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/criteria")
@RequiredArgsConstructor
public class AdminCriteriaController extends CrudController<CcfaCriteria, Long, AdminCriteriaForm, AdminCriteriaSearchForm> {

    private final AdminCriteriaService service;

    @Override protected CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/criteria"; }
    @Override protected String viewDir() { return "system/criteria"; }
    @Override protected AdminCriteriaSearchForm newSearchForm() { return new AdminCriteriaSearchForm(); }
    @Override protected AdminCriteriaForm newForm() { return new AdminCriteriaForm(); }
    @Override protected AdminCriteriaForm toForm(CcfaCriteria e) { return AdminCriteriaForm.from(e); }
    @Override protected CcfaCriteria toEntity(AdminCriteriaForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(AdminCriteriaForm f, CcfaCriteria e) { f.applyTo(e); }
    @Override protected Object toListView(CcfaCriteria e) { return AdminCriteriaRow.of(e); }
    @Override protected Object toDetailView(CcfaCriteria e) { return AdminCriteriaView.of(e); }

    @Override protected void validate(AdminCriteriaForm form, boolean isNew, BindingResult binding) {
        String json = form.getJsondata();
        if (json != null && !json.isBlank() && !JsonPretty.isValidJson(json)) {
            binding.rejectValue("jsondata", "json", "올바른 JSON(객체 또는 배열)이어야 합니다.");
        }
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/criteria/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>어드민 기준</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">어드민 기준</h1>
    <a th:href="@{/system/criteria/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/system/criteria}" class="row g-2 align-items-end mb-3">
    <div class="col-auto">
      <label class="form-label small mb-0">AAID</label>
      <input name="aaid" th:value="${search.aaid}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/criteria}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>AAID</th><th>메타 해시</th><th>수정일시</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/system/criteria/{id}(id=${r.idx})}" th:text="${r.aaid}">AAID</a></td>
          <td class="fa-mono" th:text="${#strings.abbreviate(r.metahash, 40)}"></td>
          <td th:text="${#temporals.format(r.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="4" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
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

`src/main/resources/templates/system/criteria/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>어드민 기준 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|어드민 기준 #${item.idx}|">어드민 기준</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/criteria/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/criteria/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/criteria}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>AAID</th><td th:text="${item.aaid}"></td></tr>
      <tr><th>메타 해시</th><td class="fa-mono" th:text="${item.metahash}"></td></tr>
      <tr><th>JSONDATA</th><td><pre class="fa-pre fa-mono mb-0" th:text="${item.jsondataPretty}"></pre></td></tr>
      <tr><th>등록일시</th><td th:text="${#temporals.format(item.createtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/criteria/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '어드민 기준 등록' : '어드민 기준 수정'">어드민 기준</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '어드민 기준 등록' : '어드민 기준 수정'">어드민 기준</h1>
  <form th:action="${isNew} ? @{/system/criteria} : @{/system/criteria/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">AAID <span class="text-danger">*</span></label>
        <input th:field="*{aaid}" class="form-control" th:classappend="${#fields.hasErrors('aaid')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{aaid}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">메타 해시</label><input th:field="*{metahash}" class="form-control"></div>
      <div class="col-12">
        <label class="form-label">JSONDATA</label>
        <textarea th:field="*{jsondata}" rows="12" class="form-control fa-mono" th:classappend="${#fields.hasErrors('jsondata')} ? 'is-invalid'"></textarea>
        <div class="invalid-feedback" th:errors="*{jsondata}"></div>
      </div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/criteria} : @{/system/criteria/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.AdminCriteriaControllerWebTest' --tests 'com.crosscert.fidoadmin.system.service.AdminCriteriaServiceTest'`
Expected: PASS (7 + 4 tests).

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/criteria src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 어드민 기준 화면 (CCFA_CRITERIA, JSONDATA 형식 검증·정리 출력)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 6: 메뉴 정의 (CCFA_MENU, 부모 메뉴 선택)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaMenuRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/MenuService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/MenuSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/MenuForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/MenuController.java`
- Create: `src/main/resources/templates/system/menus/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/MenuServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/MenuControllerWebTest.java`

**Interfaces:**
- Consumes: `CrudService`, `CrudController`, 1부 엔티티 `CcfaMenu(idx, menuName, menuCode, menuParentIdx, menuIcon, menuUrl, menuSeq, tblName, pk, visible, openType, statistics, readonly)`.
- Produces: `CcfaMenuRepository.countByMenuParentIdx(Long)`, `CcfaMenuRepository.findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc()`; `MenuService.allForSelect(): List<CcfaMenu>`; 경로 `/system/menus`. `CCFA_MENU` 는 데이터로만 다루며 사이드바(`MenuRegistry`)와 무관하다.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/MenuServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.repository.CcfaMenuRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MenuServiceTest {

    CcfaMenuRepository repo = mock(CcfaMenuRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    MenuService service = new MenuService(repo, audit);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaMenu menu(long idx, String name) { CcfaMenu m = new CcfaMenu(); m.setIdx(idx); m.setMenuName(name); return m; }

    @Test void defaultsFilledOnCreate() {
        when(repo.save(any())).thenAnswer(inv -> { CcfaMenu m = inv.getArgument(0); m.setIdx(10L); return m; });
        CcfaMenu out = service.create(menu(0L, "새 메뉴"));
        assertThat(out.getMenuParentIdx()).isZero();
        assertThat(out.getVisible()).isEqualTo("true");
        assertThat(out.getOpenType()).isEqualTo("open");
        assertThat(out.getStatistics()).isEqualTo("N");
        assertThat(out.getReadonly()).isEqualTo("N");
        verify(audit).log(AuditType.CREATE, "CCFA_MENU CREATE 10");
    }

    @Test void explicitValuesAreKept() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaMenu in = menu(0L, "하위"); in.setMenuParentIdx(1L); in.setVisible("false"); in.setReadonly("Y");
        CcfaMenu out = service.create(in);
        assertThat(out.getMenuParentIdx()).isEqualTo(1L);
        assertThat(out.getVisible()).isEqualTo("false");
        assertThat(out.getReadonly()).isEqualTo("Y");
    }

    @Test void deleteBlockedWhenChildrenExist() {
        when(repo.findById(1L)).thenReturn(Optional.of(menu(1L, "FIDO")));
        when(repo.countByMenuParentIdx(1L)).thenReturn(3L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("하위 메뉴가 3건");
        verify(repo, never()).delete(any(CcfaMenu.class));
        verify(audit, never()).log(any(), any());
    }

    @Test void deleteLeafSucceeds() {
        when(repo.findById(2L)).thenReturn(Optional.of(menu(2L, "앱 ID")));
        when(repo.countByMenuParentIdx(2L)).thenReturn(0L);
        service.delete(2L);
        verify(repo).delete(any(CcfaMenu.class));
        verify(audit).log(AuditType.DELETE, "CCFA_MENU DELETE 2");
    }

    @Test void selectListIsOrderedByRepositoryQuery() {
        when(repo.findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc()).thenReturn(List.of(menu(1L, "FIDO"), menu(2L, "앱 ID")));
        assertThat(service.allForSelect()).extracting(CcfaMenu::getMenuName).containsExactly("FIDO", "앱 ID");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Order.asc("menuParentIdx"), Sort.Order.asc("menuSeq"), Sort.Order.asc("idx")));
        assertThat(service.sortableProperties()).contains("idx", "menuName", "menuCode", "menuParentIdx", "menuSeq");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.MenuServiceTest'`
Expected: FAIL — `MenuService`, `CcfaMenuRepository` 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaMenuRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import java.util.List;

public interface CcfaMenuRepository extends AdminRepository<CcfaMenu, Long> {
    long countByMenuParentIdx(Long menuParentIdx);
    List<CcfaMenu> findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc();
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/MenuSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MenuSearchForm extends SearchForm {
    private String menuName;
    private String menuCode;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("menuName", menuName); m.put("menuCode", menuCode);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/MenuService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.repository.CcfaMenuRepository;
import com.crosscert.fidoadmin.system.web.MenuSearchForm;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_MENU — 어드민 메뉴 트리(데이터로만 다룬다). COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class MenuService extends CrudService<CcfaMenu, Long, MenuSearchForm> {

    private final CcfaMenuRepository menus;

    public MenuService(CcfaMenuRepository repository, AuditLogger audit) {
        super(repository, audit);
        this.menus = repository;
    }

    @Override protected Specification<CcfaMenu> toSpecification(MenuSearchForm f) {
        return Specs.all(Specs.like("menuName", f.getMenuName()), Specs.like("menuCode", f.getMenuCode()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaMenu e) { return null; }
    @Override protected void setCompanyIdx(CcfaMenu e, Long c) {}
    @Override public String idOf(CcfaMenu e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MENU"; }
    @Override public Sort defaultSort() {
        return Sort.by(Sort.Order.asc("menuParentIdx"), Sort.Order.asc("menuSeq"), Sort.Order.asc("idx"));
    }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "menuName", "menuCode", "menuParentIdx", "menuSeq"); }

    @Override protected void applyDefaults(CcfaMenu e) {
        if (e.getMenuParentIdx() == null) e.setMenuParentIdx(0L);
        if (e.getVisible() == null) e.setVisible("true");
        if (e.getOpenType() == null) e.setOpenType("open");
        if (e.getStatistics() == null) e.setStatistics("N");
        if (e.getReadonly() == null) e.setReadonly("N");
    }

    @Override protected void beforeDelete(CcfaMenu e) {
        long children = menus.countByMenuParentIdx(e.getIdx());
        if (children > 0) throw new IllegalStateException("하위 메뉴가 " + children + "건 있어 삭제할 수 없습니다.");
    }

    /** 부모 메뉴 select 와 목록의 부모 이름 표시용. 트리 순서(부모, 순번, IDX). */
    @Transactional(readOnly = true)
    public List<CcfaMenu> allForSelect() {
        requireSuperForGlobalTable();
        return menus.findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc();
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.MenuServiceTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/MenuControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

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
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MenuController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class MenuControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean MenuService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaMenu menu(long idx, String name, long parent) {
        CcfaMenu m = new CcfaMenu(); m.setIdx(idx); m.setMenuName(name); m.setMenuCode("C" + idx); m.setMenuParentIdx(parent);
        m.setVisible("true"); m.setOpenType("open"); m.setStatistics("N"); m.setReadonly("N");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/menus").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 부모 IDX 대신 부모 이름을 보여준다. */
    @Test void listRendersRowsWithParentName() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L)));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L))));
        mvc.perform(get("/system/menus").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/list"))
            .andExpect(content().string(containsString("앱 ID")))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("/system/menus/2")));
    }

    /** 수정 폼의 부모 select 에는 자기 자신이 없고 "최상위" 는 있다. */
    @Test void editFormExcludesSelfFromParentSelect() throws Exception {
        when(service.get(2L)).thenReturn(menu(2L, "앱 ID", 1L));
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L)));
        mvc.perform(get("/system/menus/2/edit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("value=\"1\"")))
            .andExpect(content().string(not(containsString("value=\"2\">#2 앱 ID"))));
    }

    @Test void blankNameShowsFormAgain() throws Exception {
        when(service.allForSelect()).thenReturn(List.of());
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "").param("menuParentIdx", "0").param("visible", "true")
                .param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(menu(9L, "신규", 0L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "신규").param("menuCode", "NEW").param("menuParentIdx", "0").param("menuSeq", "5")
                .param("visible", "true").param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/menus/9"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/menus").with(user(superUser)).param("menuName", "x")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.MenuControllerWebTest'`
Expected: FAIL — `MenuController`, `MenuForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/MenuForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_MENU 입력 폼. 길이 제한은 ERD 값. */
@Getter @Setter
public class MenuForm {
    @NotBlank @ByteSize(max = 32) private String menuName;
    @ByteSize(max = 32) private String menuCode;
    @NotNull private Long menuParentIdx = 0L;
    @ByteSize(max = 64) private String menuIcon;
    @ByteSize(max = 512) private String menuUrl;
    private Long menuSeq;
    @ByteSize(max = 128) private String tblName;
    @ByteSize(max = 128) private String pk;
    @NotBlank @ByteSize(max = 20) private String visible = "true";
    @NotBlank @ByteSize(max = 20) private String openType = "open";
    @NotBlank @ByteSize(max = 20) private String statistics = "N";
    @NotBlank @ByteSize(max = 20) private String readonly = "N";

    public static MenuForm from(CcfaMenu m) {
        MenuForm f = new MenuForm();
        f.menuName = m.getMenuName(); f.menuCode = m.getMenuCode(); f.menuParentIdx = m.getMenuParentIdx();
        f.menuIcon = m.getMenuIcon(); f.menuUrl = m.getMenuUrl(); f.menuSeq = m.getMenuSeq();
        f.tblName = m.getTblName(); f.pk = m.getPk(); f.visible = m.getVisible(); f.openType = m.getOpenType();
        f.statistics = m.getStatistics(); f.readonly = m.getReadonly();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaMenu toNewEntity() {
        CcfaMenu m = new CcfaMenu();
        applyTo(m);
        return m;
    }

    public void applyTo(CcfaMenu m) {
        m.setMenuName(menuName); m.setMenuCode(menuCode); m.setMenuParentIdx(menuParentIdx);
        m.setMenuIcon(menuIcon); m.setMenuUrl(menuUrl); m.setMenuSeq(menuSeq);
        m.setTblName(tblName); m.setPk(pk); m.setVisible(visible); m.setOpenType(openType);
        m.setStatistics(statistics); m.setReadonly(readonly);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/MenuController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.service.MenuService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class MenuController extends CrudController<CcfaMenu, Long, MenuForm, MenuSearchForm> {

    private final MenuService service;

    @Override protected CrudService<CcfaMenu, Long, MenuSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/menus"; }
    @Override protected String viewDir() { return "system/menus"; }
    @Override protected MenuSearchForm newSearchForm() { return new MenuSearchForm(); }
    @Override protected MenuForm newForm() { return new MenuForm(); }
    @Override protected MenuForm toForm(CcfaMenu e) { return MenuForm.from(e); }
    @Override protected CcfaMenu toEntity(MenuForm f) { return f.toNewEntity(); }

    /**
     * 폼의 select 는 자기 자신을 빼고 그리지만(템플릿), 요청을 조작해 자기 자신을 부모로 보내면
     * 트리가 순환한다. 여기서 막으면 500 으로 끝나지만 데이터는 지켜진다.
     */
    @Override protected void applyForm(MenuForm f, CcfaMenu e) {
        if (f.getMenuParentIdx() != null && f.getMenuParentIdx().equals(e.getIdx())) {
            throw new IllegalArgumentException("자기 자신을 부모로 지정할 수 없습니다.");
        }
        f.applyTo(e);
    }

    @Override protected void populateFormModel(Model model) { model.addAttribute("menus", service.allForSelect()); }
    @Override protected void populateListModel(Model model) { model.addAttribute("parentNames", parentNames()); }
    @Override protected void populateDetailModel(CcfaMenu e, Model model) { model.addAttribute("parentNames", parentNames()); }

    private Map<Long, String> parentNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(0L, "최상위");
        for (CcfaMenu m : service.allForSelect()) names.put(m.getIdx(), m.getMenuName());
        return names;
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/menus/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>메뉴 정의</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">메뉴 정의</h1>
    <a th:href="@{/system/menus/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/system/menus}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">이름</label><input name="menuName" th:value="${search.menuName}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">코드</label><input name="menuCode" th:value="${search.menuCode}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/menus}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>이름</th><th>코드</th><th>부모</th><th>순번</th><th>URL</th><th>표시</th><th>읽기전용</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/system/menus/{id}(id=${r.idx})}" th:text="${r.menuName}">이름</a></td>
          <td th:text="${r.menuCode}"></td>
          <td th:text="${parentNames.get(r.menuParentIdx)} ?: ${r.menuParentIdx}"></td>
          <td th:text="${r.menuSeq}"></td>
          <td th:text="${r.menuUrl}"></td>
          <td th:text="${r.visible}"></td>
          <td th:text="${r.readonly}"></td>
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

`src/main/resources/templates/system/menus/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>메뉴 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|메뉴 #${item.idx}|">메뉴</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/menus/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/menus/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/menus}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>이름</th><td th:text="${item.menuName}"></td></tr>
      <tr><th>코드</th><td th:text="${item.menuCode}"></td></tr>
      <tr><th>부모</th><td th:text="|${parentNames.get(item.menuParentIdx) ?: ''} (${item.menuParentIdx})|"></td></tr>
      <tr><th>아이콘</th><td th:text="${item.menuIcon}"></td></tr>
      <tr><th>URL</th><td th:text="${item.menuUrl}"></td></tr>
      <tr><th>순번</th><td th:text="${item.menuSeq}"></td></tr>
      <tr><th>테이블</th><td th:text="${item.tblName}"></td></tr>
      <tr><th>PK</th><td th:text="${item.pk}"></td></tr>
      <tr><th>표시</th><td th:text="${item.visible}"></td></tr>
      <tr><th>열림 유형</th><td th:text="${item.openType}"></td></tr>
      <tr><th>통계</th><td th:text="${item.statistics}"></td></tr>
      <tr><th>읽기전용</th><td th:text="${item.readonly}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/menus/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '메뉴 등록' : '메뉴 수정'">메뉴</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '메뉴 등록' : '메뉴 수정'">메뉴</h1>
  <form th:action="${isNew} ? @{/system/menus} : @{/system/menus/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">이름 <span class="text-danger">*</span></label>
        <input th:field="*{menuName}" class="form-control" th:classappend="${#fields.hasErrors('menuName')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{menuName}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">코드</label><input th:field="*{menuCode}" class="form-control"></div>
      <div class="col-md-6">
        <label class="form-label">부모 메뉴 <span class="text-danger">*</span></label>
        <!-- 수정 중인 메뉴 자신은 부모 후보에서 뺀다. 등록 폼(id 없음)에서는 전부 보인다. -->
        <select th:field="*{menuParentIdx}" class="form-select">
          <option value="0">최상위</option>
          <option th:each="m : ${menus}" th:if="${id == null or m.idx != id}" th:value="${m.idx}" th:text="|#${m.idx} ${m.menuName}|"></option>
        </select>
      </div>
      <div class="col-md-3"><label class="form-label">순번</label><input type="number" th:field="*{menuSeq}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">아이콘</label><input th:field="*{menuIcon}" class="form-control"></div>
      <div class="col-12"><label class="form-label">URL</label><input th:field="*{menuUrl}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">테이블</label><input th:field="*{tblName}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">PK</label><input th:field="*{pk}" class="form-control"></div>
      <div class="col-md-3">
        <label class="form-label">표시</label>
        <select th:field="*{visible}" class="form-select"><option value="true">true</option><option value="false">false</option></select>
      </div>
      <div class="col-md-3">
        <label class="form-label">열림 유형</label>
        <select th:field="*{openType}" class="form-select"><option value="open">open</option><option value="close">close</option></select>
      </div>
      <div class="col-md-3">
        <label class="form-label">통계</label>
        <select th:field="*{statistics}" class="form-select"><option value="N">N</option><option value="Y">Y</option></select>
      </div>
      <div class="col-md-3">
        <label class="form-label">읽기전용</label>
        <select th:field="*{readonly}" class="form-select"><option value="N">N</option><option value="Y">Y</option></select>
      </div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/menus} : @{/system/menus/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.MenuControllerWebTest' --tests 'com.crosscert.fidoadmin.system.service.MenuServiceTest'`
Expected: PASS (6 + 5 tests).

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/menus src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 메뉴 정의 화면 (CCFA_MENU, 부모 메뉴 선택·하위 메뉴 삭제 차단)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 7: 코드 그룹/코드 (CCFA_OPTION + CCFA_OPTIONS 인라인 관리)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaOptionRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaOptionsRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/OptionService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/OptionSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/OptionForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/OptionItemForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/OptionController.java`
- Create: `src/main/resources/templates/system/options/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/OptionServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/OptionControllerWebTest.java`

**Interfaces:**
- Consumes: `CrudService`, `CrudController`, `AuditLogger`, `AuditType`, 1부 엔티티 `CcfaOption(idx, optionName, optionNote, optionTitle)`, `CcfaOptions(idx, optionIdx, optionValue, optionTitle, optionNote)`.
- Produces: `CcfaOptionRepository extends AdminRepository<CcfaOption, Long>`(Task 8 의 select 목록에서도 사용); `CcfaOptionsRepository.findByOptionIdxOrderByIdxAsc(Long)`, `countByOptionIdx(Long)`; `OptionService.items(Long): List<CcfaOptions>`, `addItem(Long optionIdx, CcfaOptions): CcfaOptions`, `removeItem(Long optionIdx, Long itemIdx)`; 경로 `/system/options`, `POST /system/options/{id}/items`, `POST /system/options/{id}/items/{itemIdx}/delete`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/OptionServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

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
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OptionServiceTest {

    CcfaOptionRepository groups = mock(CcfaOptionRepository.class);
    CcfaOptionsRepository items = mock(CcfaOptionsRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    OptionService service = new OptionService(groups, audit, items);

    @BeforeEach void loginSuper() { login(0L); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaOption group(long idx) { CcfaOption g = new CcfaOption(); g.setIdx(idx); g.setOptionName("STATUS"); return g; }
    private CcfaOptions item(long idx, long optionIdx) { CcfaOptions i = new CcfaOptions(); i.setIdx(idx); i.setOptionIdx(optionIdx); i.setOptionValue("use"); return i; }

    @Test void addItemAssignsGroupAndAudits() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.save(any())).thenAnswer(inv -> { CcfaOptions i = inv.getArgument(0); i.setIdx(5L); return i; });
        CcfaOptions in = new CcfaOptions(); in.setOptionValue("unuse"); in.setOptionIdx(999L); // 폼이 무엇을 보내든 경로의 그룹이 이긴다
        CcfaOptions out = service.addItem(1L, in);
        assertThat(out.getOptionIdx()).isEqualTo(1L);
        verify(audit).log(AuditType.CREATE, "CCFA_OPTIONS CREATE 5");
    }

    @Test void addItemToMissingGroupFails() {
        when(groups.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(9L, new CcfaOptions())).isInstanceOf(EntityNotFoundException.class);
        verify(items, never()).save(any());
    }

    @Test void removeItemDeletesAndAudits() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.findById(5L)).thenReturn(Optional.of(item(5L, 1L)));
        service.removeItem(1L, 5L);
        verify(items).delete(any(CcfaOptions.class));
        verify(audit).log(AuditType.DELETE, "CCFA_OPTIONS DELETE 5");
    }

    /** 다른 그룹의 코드를 경로만 바꿔 지우지 못한다. */
    @Test void removeItemOfOtherGroupIsRejected() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.findById(5L)).thenReturn(Optional.of(item(5L, 2L)));
        assertThatThrownBy(() -> service.removeItem(1L, 5L)).isInstanceOf(EntityNotFoundException.class);
        verify(items, never()).delete(any(CcfaOptions.class));
        verify(audit, never()).log(any(), any());
    }

    @Test void deleteGroupBlockedWhenItemsExist() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.countByOptionIdx(1L)).thenReturn(2L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("코드 2건");
        verify(groups, never()).delete(any(CcfaOption.class));
    }

    @Test void deleteEmptyGroupSucceeds() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.countByOptionIdx(1L)).thenReturn(0L);
        service.delete(1L);
        verify(groups).delete(any(CcfaOption.class));
        verify(audit).log(AuditType.DELETE, "CCFA_OPTION DELETE 1");
    }

    @Test void itemsAreListedInIdxOrder() {
        when(items.findByOptionIdxOrderByIdxAsc(1L)).thenReturn(List.of(item(1L, 1L), item(2L, 1L)));
        assertThat(service.items(1L)).extracting(CcfaOptions::getIdx).containsExactly(1L, 2L);
    }

    @Test void companyRoleIsDeniedForItemsToo() {
        login(1L);
        assertThatThrownBy(() -> service.items(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addItem(1L, new CcfaOptions())).isInstanceOf(AccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.OptionServiceTest'`
Expected: FAIL — `OptionService`, 리포지토리 2개 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaOptionRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaOption;

public interface CcfaOptionRepository extends AdminRepository<CcfaOption, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaOptionsRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import java.util.List;

public interface CcfaOptionsRepository extends AdminRepository<CcfaOptions, Long> {
    List<CcfaOptions> findByOptionIdxOrderByIdxAsc(Long optionIdx);
    long countByOptionIdx(Long optionIdx);
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/OptionSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OptionSearchForm extends SearchForm {
    private String optionName;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("optionName", optionName);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/OptionService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository;
import com.crosscert.fidoadmin.system.web.OptionSearchForm;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CCFA_OPTION(코드 그룹) CRUD + CCFA_OPTIONS(코드) 인라인 관리. COMPANY_IDX 가 없으므로 SUPER 전용.
 * 코드는 그룹 상세 안에서만 추가·삭제하며 별도 화면이 없다.
 */
@Service
public class OptionService extends CrudService<CcfaOption, Long, OptionSearchForm> {

    private final CcfaOptionsRepository items;

    public OptionService(CcfaOptionRepository repository, AuditLogger audit, CcfaOptionsRepository items) {
        super(repository, audit);
        this.items = items;
    }

    @Override protected Specification<CcfaOption> toSpecification(OptionSearchForm f) {
        return Specs.all(Specs.like("optionName", f.getOptionName()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaOption e) { return null; }
    @Override protected void setCompanyIdx(CcfaOption e, Long c) {}
    @Override public String idOf(CcfaOption e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_OPTION"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "optionName"); }

    @Override protected void beforeDelete(CcfaOption e) {
        long count = items.countByOptionIdx(e.getIdx());
        if (count > 0) throw new IllegalStateException("하위 코드 " + count + "건이 있어 삭제할 수 없습니다. 코드를 먼저 삭제하세요.");
    }

    @Transactional(readOnly = true)
    public List<CcfaOptions> items(Long optionIdx) {
        requireSuperForGlobalTable();
        return items.findByOptionIdxOrderByIdxAsc(optionIdx);
    }

    /** 그룹 존재 확인 후 코드를 추가한다. OPTION_IDX 는 폼이 아니라 경로의 그룹으로 강제한다. */
    @Transactional
    public CcfaOptions addItem(Long optionIdx, CcfaOptions item) {
        CcfaOption group = get(optionIdx);
        item.setIdx(null);
        item.setOptionIdx(group.getIdx());
        CcfaOptions saved = items.save(item);
        audit.log(AuditType.CREATE, "CCFA_OPTIONS CREATE " + saved.getIdx());
        return saved;
    }

    /** 경로의 그룹에 속한 코드만 지운다. 다른 그룹의 코드 IDX 를 넣으면 존재하지 않는 것으로 본다. */
    @Transactional
    public void removeItem(Long optionIdx, Long itemIdx) {
        CcfaOption group = get(optionIdx);
        CcfaOptions item = items.findById(itemIdx)
            .filter(i -> group.getIdx().equals(i.getOptionIdx()))
            .orElseThrow(() -> new EntityNotFoundException("CCFA_OPTIONS " + itemIdx));
        items.delete(item);
        audit.log(AuditType.DELETE, "CCFA_OPTIONS DELETE " + itemIdx);
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.OptionServiceTest'`
Expected: PASS (8 tests). `deleteGroupBlockedWhenItemsExist` 의 메시지 검사 문자열 `코드 2건` 이 서비스 메시지 `하위 코드 2건이 있어…` 에 포함된다.

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/OptionControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.service.OptionService;
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

@WebMvcTest(controllers = OptionController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class OptionControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean OptionService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaOption group(long idx, String name) { CcfaOption g = new CcfaOption(); g.setIdx(idx); g.setOptionName(name); g.setOptionTitle("상태"); return g; }
    private CcfaOptions item(long idx, String value, String title) {
        CcfaOptions i = new CcfaOptions(); i.setIdx(idx); i.setOptionIdx(1L); i.setOptionValue(value); i.setOptionTitle(title); return i;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/options").with(user(companyUser))).andExpect(status().isForbidden());
        mvc.perform(post("/system/options/1/items").with(user(companyUser)).with(csrf()).param("optionValue", "x"))
            .andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(group(1L, "STATUS"))));
        mvc.perform(get("/system/options").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/list"))
            .andExpect(content().string(containsString("STATUS")))
            .andExpect(content().string(containsString("/system/options/1")));
    }

    /** 상세는 그룹 정보 + 코드 목록 + 인라인 추가 폼 + 코드별 삭제 폼을 함께 그린다. */
    @Test void detailListsItemsWithInlineForms() throws Exception {
        when(service.get(1L)).thenReturn(group(1L, "STATUS"));
        when(service.items(1L)).thenReturn(List.of(item(11L, "use", "사용"), item(12L, "unuse", "미사용")));
        mvc.perform(get("/system/options/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/detail"))
            .andExpect(content().string(containsString("사용")))
            .andExpect(content().string(containsString("미사용")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items\"")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items/12/delete\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"delItem12\"")));
    }

    @Test void addItemRedirectsToDetailAndCallsService() throws Exception {
        when(service.addItem(eq(1L), any())).thenReturn(item(13L, "hold", "보류"));
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf())
                .param("optionValue", "hold").param("optionTitle", "보류").param("optionNote", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 추가되었습니다."));
        ArgumentCaptor<CcfaOptions> captor = ArgumentCaptor.forClass(CcfaOptions.class);
        verify(service).addItem(eq(1L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOptionValue()).isEqualTo("hold");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOptionTitle()).isEqualTo("보류");
    }

    /** 코드 값이 비면 저장하지 않고 상세로 돌아가 오류 플래시를 보인다. */
    @Test void addItemWithBlankValueRedirectsWithError() throws Exception {
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf()).param("optionValue", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashError", "코드 값은 필수입니다(128바이트 이내)."));
        verify(service, never()).addItem(any(), any());
    }

    @Test void removeItemRedirectsToDetail() throws Exception {
        mvc.perform(post("/system/options/1/items/12/delete").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 삭제되었습니다."));
        verify(service).removeItem(1L, 12L);
    }

    @Test void blankGroupNameShowsFormAgain() throws Exception {
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(group(9L, "NEW"));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", "NEW").param("optionTitle", "신규"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/9"));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.OptionControllerWebTest'`
Expected: FAIL — `OptionController`, `OptionForm`, `OptionItemForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/OptionForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_OPTION(코드 그룹) 입력 폼. */
@Getter @Setter
public class OptionForm {
    @NotBlank @ByteSize(max = 64) private String optionName;
    @ByteSize(max = 64) private String optionNote;
    @ByteSize(max = 64) private String optionTitle;

    public static OptionForm from(CcfaOption o) {
        OptionForm f = new OptionForm();
        f.optionName = o.getOptionName(); f.optionNote = o.getOptionNote(); f.optionTitle = o.getOptionTitle();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaOption toNewEntity() {
        CcfaOption o = new CcfaOption();
        applyTo(o);
        return o;
    }

    public void applyTo(CcfaOption o) {
        o.setOptionName(optionName); o.setOptionNote(optionNote); o.setOptionTitle(optionTitle);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/OptionItemForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_OPTIONS(코드) 인라인 추가 폼. OPTION_IDX 는 경로에서 오므로 폼에 없다. */
@Getter @Setter
public class OptionItemForm {
    @NotBlank @ByteSize(max = 128) private String optionValue;
    @ByteSize(max = 128) private String optionTitle;
    @ByteSize(max = 128) private String optionNote;

    public CcfaOptions toNewEntity() {
        CcfaOptions i = new CcfaOptions();
        i.setOptionValue(optionValue); i.setOptionTitle(optionTitle); i.setOptionNote(optionNote);
        return i;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/OptionController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.OptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/system/options")
@RequiredArgsConstructor
public class OptionController extends CrudController<CcfaOption, Long, OptionForm, OptionSearchForm> {

    private final OptionService service;

    @Override protected CrudService<CcfaOption, Long, OptionSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/options"; }
    @Override protected String viewDir() { return "system/options"; }
    @Override protected OptionSearchForm newSearchForm() { return new OptionSearchForm(); }
    @Override protected OptionForm newForm() { return new OptionForm(); }
    @Override protected OptionForm toForm(CcfaOption e) { return OptionForm.from(e); }
    @Override protected CcfaOption toEntity(OptionForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(OptionForm f, CcfaOption e) { f.applyTo(e); }

    @Override protected void populateDetailModel(CcfaOption e, Model model) {
        model.addAttribute("items", service.items(e.getIdx()));
        if (!model.containsAttribute("itemForm")) model.addAttribute("itemForm", new OptionItemForm());
    }

    /** 코드 인라인 추가. 검증 실패는 상세로 돌아가 플래시로 알린다(상세 화면을 폼처럼 다시 그리지 않는다). */
    @PostMapping("/{id}/items")
    public String addItem(@PathVariable Long id, @Valid @ModelAttribute("itemForm") OptionItemForm itemForm,
                          BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("flashError", "코드 값은 필수입니다(128바이트 이내).");
            return "redirect:/system/options/" + id;
        }
        service.addItem(id, itemForm.toNewEntity());
        redirect.addFlashAttribute("flashSuccess", "코드가 추가되었습니다.");
        return "redirect:/system/options/" + id;
    }

    @PostMapping("/{id}/items/{itemIdx}/delete")
    public String removeItem(@PathVariable Long id, @PathVariable Long itemIdx, RedirectAttributes redirect) {
        service.removeItem(id, itemIdx);
        redirect.addFlashAttribute("flashSuccess", "코드가 삭제되었습니다.");
        return "redirect:/system/options/" + id;
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/options/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>코드 그룹/코드</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">코드 그룹/코드</h1>
    <a th:href="@{/system/options/new}" class="btn btn-primary btn-sm">그룹 등록</a>
  </div>

  <form method="get" th:action="@{/system/options}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">그룹 이름</label><input name="optionName" th:value="${search.optionName}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/options}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>그룹 이름</th><th>제목</th><th>비고</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td><a th:href="@{/system/options/{id}(id=${r.idx})}" th:text="${r.optionName}">이름</a></td>
          <td th:text="${r.optionTitle}"></td>
          <td th:text="${r.optionNote}"></td>
        </tr>
        <tr th:if="${page.totalElements == 0}"><td colspan="4" class="text-center text-secondary py-4">데이터가 없습니다.</td></tr>
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

`src/main/resources/templates/system/options/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>코드 그룹 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|코드 그룹 #${item.idx} ${item.optionName}|">코드 그룹</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/options/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/options/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm"
                data-confirm-message="그룹을 삭제하시겠습니까? 하위 코드가 있으면 삭제되지 않습니다.">삭제</button>
      </form>
      <a th:href="@{/system/options}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>

  <div class="bg-white border rounded mb-4">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>그룹 이름</th><td th:text="${item.optionName}"></td></tr>
      <tr><th>제목</th><td th:text="${item.optionTitle}"></td></tr>
      <tr><th>비고</th><td th:text="${item.optionNote}"></td></tr>
    </tbody></table>
  </div>

  <h2 class="h5 mb-2">코드 목록</h2>
  <div class="table-responsive bg-white border rounded mb-3">
    <table class="table table-sm fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>값</th><th>제목</th><th>비고</th><th></th></tr></thead>
      <tbody>
        <tr th:each="it : ${items}">
          <td th:text="${it.idx}"></td>
          <td class="fa-mono" th:text="${it.optionValue}"></td>
          <td th:text="${it.optionTitle}"></td>
          <td th:text="${it.optionNote}"></td>
          <td class="text-end">
            <!-- 행마다 고유한 form id 를 주고 버튼이 그 id 를 가리킨다. admin.js 는 어떤 id 든 찾아 제출한다. -->
            <form th:action="@{/system/options/{id}/items/{itemIdx}/delete(id=${item.idx}, itemIdx=${it.idx})}" method="post"
                  th:id="|delItem${it.idx}|" class="m-0 d-inline">
              <button type="button" class="btn btn-outline-danger btn-sm" th:attr="data-confirm-form=|delItem${it.idx}|"
                      data-confirm-message="코드를 삭제하시겠습니까?">삭제</button>
            </form>
          </td>
        </tr>
        <tr th:if="${#lists.isEmpty(items)}"><td colspan="5" class="text-center text-secondary py-3">코드가 없습니다.</td></tr>
      </tbody>
    </table>
  </div>

  <form th:action="@{/system/options/{id}/items(id=${item.idx})}" th:object="${itemForm}" method="post"
        class="bg-white border rounded p-3 row g-2 align-items-end" style="max-width: 760px;">
    <div class="col-md-3">
      <label class="form-label small mb-0">값 <span class="text-danger">*</span></label>
      <input th:field="*{optionValue}" class="form-control form-control-sm">
    </div>
    <div class="col-md-3"><label class="form-label small mb-0">제목</label><input th:field="*{optionTitle}" class="form-control form-control-sm"></div>
    <div class="col-md-4"><label class="form-label small mb-0">비고</label><input th:field="*{optionNote}" class="form-control form-control-sm"></div>
    <div class="col-md-2"><button class="btn btn-primary btn-sm w-100">코드 추가</button></div>
  </form>
</main>
</body>
</html>
```

`src/main/resources/templates/system/options/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '코드 그룹 등록' : '코드 그룹 수정'">코드 그룹</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '코드 그룹 등록' : '코드 그룹 수정'">코드 그룹</h1>
  <form th:action="${isNew} ? @{/system/options} : @{/system/options/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 640px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">그룹 이름 <span class="text-danger">*</span></label>
        <input th:field="*{optionName}" class="form-control" th:classappend="${#fields.hasErrors('optionName')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{optionName}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">제목</label><input th:field="*{optionTitle}" class="form-control"></div>
      <div class="col-12"><label class="form-label">비고</label><input th:field="*{optionNote}" class="form-control"></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/options} : @{/system/options/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.OptionControllerWebTest' --tests 'com.crosscert.fidoadmin.system.service.OptionServiceTest'`
Expected: PASS (8 + 8 tests).

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/options src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 코드 그룹/코드 화면 (CCFA_OPTION CRUD + CCFA_OPTIONS 인라인 추가·삭제)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 8: 필드 정의 (CCFA_FIELDS, 코드 그룹 선택)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFieldsRepository.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/service/FieldService.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FieldSearchForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FieldForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/web/FieldController.java`
- Create: `src/main/resources/templates/system/fields/list.html`, `detail.html`, `form.html`
- Test: `src/test/java/com/crosscert/fidoadmin/system/service/FieldServiceTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/web/FieldControllerWebTest.java`

**Interfaces:**
- Consumes: `CrudService`, `CrudController`, Task 7 의 `CcfaOptionRepository`(코드 그룹 select 목록), 1부 엔티티 `CcfaFields(idx, fieldTable, fieldName, fieldType, fieldTitle, pk, fk, optionIdx, editable)`.
- Produces: `CcfaFieldsRepository extends AdminRepository<CcfaFields, Long>`; `FieldService.options(): List<CcfaOption>`; 경로 `/system/fields`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/service/FieldServiceTest.java`

```java
package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FieldServiceTest {

    CcfaFieldsRepository repo = mock(CcfaFieldsRepository.class);
    CcfaOptionRepository options = mock(CcfaOptionRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    FieldService service = new FieldService(repo, audit, options);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaFields field() {
        CcfaFields f = new CcfaFields(); f.setFieldTable("APPID"); f.setFieldName("STATUS"); f.setFieldType("select"); return f;
    }

    @Test void numericFlagsDefaultToZero() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaFields f = inv.getArgument(0); f.setIdx(3L); return f; });
        CcfaFields out = service.create(field());
        assertThat(out.getPk()).isZero();
        assertThat(out.getFk()).isZero();
        assertThat(out.getEditable()).isZero();
        assertThat(out.getOptionIdx()).isNull();
        verify(audit).log(AuditType.CREATE, "CCFA_FIELDS CREATE 3");
    }

    @Test void explicitFlagsAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaFields in = field(); in.setPk(1L); in.setEditable(1L); in.setOptionIdx(1L);
        CcfaFields out = service.create(in);
        assertThat(out.getPk()).isEqualTo(1L);
        assertThat(out.getEditable()).isEqualTo(1L);
        assertThat(out.getOptionIdx()).isEqualTo(1L);
    }

    @Test void optionsComeFromOptionRepositoryInIdxOrder() {
        login(0L);
        CcfaOption o = new CcfaOption(); o.setIdx(1L); o.setOptionName("STATUS");
        when(options.findAll(Sort.by("idx"))).thenReturn(List.of(o));
        assertThat(service.options()).extracting(CcfaOption::getOptionName).containsExactly("STATUS");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx")));
        assertThat(service.sortableProperties()).contains("idx", "fieldTable", "fieldName");
    }

    @Test void companyRoleIsDenied() {
        login(1L);
        assertThatThrownBy(() -> service.options()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(field())).isInstanceOf(AccessDeniedException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.FieldServiceTest'`
Expected: FAIL — `FieldService`, `CcfaFieldsRepository` 없음.

- [ ] **Step 3: 리포지토리, 검색 폼, 서비스 작성**

`src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFieldsRepository.java`

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaFields;

public interface CcfaFieldsRepository extends AdminRepository<CcfaFields, Long> {
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/FieldSearchForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FieldSearchForm extends SearchForm {
    private String fieldTable;
    private String fieldName;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldTable", fieldTable); m.put("fieldName", fieldName);
        return m;
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/service/FieldService.java`

```java
package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.web.FieldSearchForm;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_FIELDS — 화면 필드 메타 정의. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class FieldService extends CrudService<CcfaFields, Long, FieldSearchForm> {

    private final CcfaOptionRepository options;

    public FieldService(CcfaFieldsRepository repository, AuditLogger audit, CcfaOptionRepository options) {
        super(repository, audit);
        this.options = options;
    }

    @Override protected Specification<CcfaFields> toSpecification(FieldSearchForm f) {
        return Specs.all(Specs.like("fieldTable", f.getFieldTable()), Specs.like("fieldName", f.getFieldName()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaFields e) { return null; }
    @Override protected void setCompanyIdx(CcfaFields e, Long c) {}
    @Override public String idOf(CcfaFields e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_FIELDS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx")); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "fieldTable", "fieldName"); }

    @Override protected void applyDefaults(CcfaFields e) {
        if (e.getPk() == null) e.setPk(0L);
        if (e.getFk() == null) e.setFk(0L);
        if (e.getEditable() == null) e.setEditable(0L);
    }

    /** 폼의 코드 그룹 select 와 목록의 그룹 이름 표시용. */
    @Transactional(readOnly = true)
    public List<CcfaOption> options() {
        requireSuperForGlobalTable();
        return options.findAll(Sort.by("idx"));
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.service.FieldServiceTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: 컨트롤러 웹 실패 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/system/web/FieldControllerWebTest.java`

```java
package com.crosscert.fidoadmin.system.web;

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
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.FieldService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FieldController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class FieldControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FieldService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFields field(long idx, Long optionIdx) {
        CcfaFields f = new CcfaFields(); f.setIdx(idx); f.setFieldTable("APPID"); f.setFieldName("STATUS"); f.setFieldType("select");
        f.setFieldTitle("상태"); f.setPk(0L); f.setFk(0L); f.setEditable(1L); f.setOptionIdx(optionIdx);
        return f;
    }
    private CcfaOption option(long idx, String name) { CcfaOption o = new CcfaOption(); o.setIdx(idx); o.setOptionName(name); return o; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/fields").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 OPTION_IDX 대신 코드 그룹 이름을 보여준다. */
    @Test void listRendersRowsWithOptionName() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.options()).thenReturn(List.of(option(1L, "STATUS_GROUP")));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(field(1L, 1L))));
        mvc.perform(get("/system/fields").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/list"))
            .andExpect(content().string(containsString("APPID")))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("/system/fields/1")));
    }

    @Test void formOffersOptionGroups() throws Exception {
        when(service.options()).thenReturn(List.of(option(1L, "STATUS_GROUP")));
        mvc.perform(get("/system/fields/new").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("name=\"optionIdx\"")));
    }

    @Test void blankTableShowsFormAgain() throws Exception {
        when(service.options()).thenReturn(List.of());
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "0").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"));
    }

    @Test void negativeFlagShowsFormAgain() throws Exception {
        when(service.options()).thenReturn(List.of());
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "-1").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(field(9L, null));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "MEMO").param("fieldType", "text")
                .param("pk", "0").param("fk", "0").param("editable", "1").param("optionIdx", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fields/9"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/fields").with(user(superUser)).param("fieldTable", "x")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.FieldControllerWebTest'`
Expected: FAIL — `FieldController`, `FieldForm` 없음.

- [ ] **Step 7: 폼과 컨트롤러 작성**

`src/main/java/com/crosscert/fidoadmin/system/web/FieldForm.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_FIELDS 입력 폼. PK/FK/EDITABLE 은 0/1 플래그를 NUMBER 로 저장한다. */
@Getter @Setter
public class FieldForm {
    @NotBlank @ByteSize(max = 128) private String fieldTable;
    @NotBlank @ByteSize(max = 256) private String fieldName;
    @NotBlank @ByteSize(max = 128) private String fieldType;
    @ByteSize(max = 128) private String fieldTitle;
    @NotNull @Min(0) private Long pk = 0L;
    @NotNull @Min(0) private Long fk = 0L;
    @NotNull @Min(0) private Long editable = 0L;
    /** 코드 그룹(CCFA_OPTION.IDX). 비우면 null. */
    private Long optionIdx;

    public static FieldForm from(CcfaFields e) {
        FieldForm f = new FieldForm();
        f.fieldTable = e.getFieldTable(); f.fieldName = e.getFieldName(); f.fieldType = e.getFieldType();
        f.fieldTitle = e.getFieldTitle(); f.pk = e.getPk(); f.fk = e.getFk(); f.editable = e.getEditable();
        f.optionIdx = e.getOptionIdx();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaFields toNewEntity() {
        CcfaFields e = new CcfaFields();
        applyTo(e);
        return e;
    }

    public void applyTo(CcfaFields e) {
        e.setFieldTable(fieldTable); e.setFieldName(fieldName); e.setFieldType(fieldType); e.setFieldTitle(fieldTitle);
        e.setPk(pk); e.setFk(fk); e.setEditable(editable); e.setOptionIdx(optionIdx);
    }
}
```

`src/main/java/com/crosscert/fidoadmin/system/web/FieldController.java`

```java
package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.FieldService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/fields")
@RequiredArgsConstructor
public class FieldController extends CrudController<CcfaFields, Long, FieldForm, FieldSearchForm> {

    private final FieldService service;

    @Override protected CrudService<CcfaFields, Long, FieldSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/fields"; }
    @Override protected String viewDir() { return "system/fields"; }
    @Override protected FieldSearchForm newSearchForm() { return new FieldSearchForm(); }
    @Override protected FieldForm newForm() { return new FieldForm(); }
    @Override protected FieldForm toForm(CcfaFields e) { return FieldForm.from(e); }
    @Override protected CcfaFields toEntity(FieldForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(FieldForm f, CcfaFields e) { f.applyTo(e); }

    @Override protected void populateFormModel(Model model) { model.addAttribute("options", service.options()); }
    @Override protected void populateListModel(Model model) { model.addAttribute("optionNames", optionNames()); }
    @Override protected void populateDetailModel(CcfaFields e, Model model) { model.addAttribute("optionNames", optionNames()); }

    private Map<Long, String> optionNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (CcfaOption o : service.options()) names.put(o.getIdx(), o.getOptionName());
        return names;
    }
}
```

- [ ] **Step 8: 템플릿 작성**

`src/main/resources/templates/system/fields/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>필드 정의</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">필드 정의</h1>
    <a th:href="@{/system/fields/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/system/fields}" class="row g-2 align-items-end mb-3">
    <div class="col-auto"><label class="form-label small mb-0">테이블</label><input name="fieldTable" th:value="${search.fieldTable}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">필드 이름</label><input name="fieldName" th:value="${search.fieldName}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/system/fields}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>테이블</th><th>필드 이름</th><th>유형</th><th>제목</th><th>PK</th><th>FK</th><th>편집</th><th>코드 그룹</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td th:text="${r.idx}">0</td>
          <td th:text="${r.fieldTable}"></td>
          <td><a th:href="@{/system/fields/{id}(id=${r.idx})}" th:text="${r.fieldName}">이름</a></td>
          <td th:text="${r.fieldType}"></td>
          <td th:text="${r.fieldTitle}"></td>
          <td th:text="${r.pk}"></td>
          <td th:text="${r.fk}"></td>
          <td th:text="${r.editable}"></td>
          <td th:text="${r.optionIdx == null} ? '' : (${optionNames.get(r.optionIdx)} ?: ${r.optionIdx})"></td>
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

`src/main/resources/templates/system/fields/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>필드 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|필드 #${item.idx}|">필드</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/system/fields/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/system/fields/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm">삭제</button>
      </form>
      <a th:href="@{/system/fields}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>테이블</th><td th:text="${item.fieldTable}"></td></tr>
      <tr><th>필드 이름</th><td th:text="${item.fieldName}"></td></tr>
      <tr><th>유형</th><td th:text="${item.fieldType}"></td></tr>
      <tr><th>제목</th><td th:text="${item.fieldTitle}"></td></tr>
      <tr><th>PK</th><td th:text="${item.pk}"></td></tr>
      <tr><th>FK</th><td th:text="${item.fk}"></td></tr>
      <tr><th>편집 가능</th><td th:text="${item.editable}"></td></tr>
      <tr><th>코드 그룹</th><td th:text="${item.optionIdx == null} ? '' : |${optionNames.get(item.optionIdx) ?: ''} (${item.optionIdx})|"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

`src/main/resources/templates/system/fields/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '필드 등록' : '필드 수정'">필드</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '필드 등록' : '필드 수정'">필드</h1>
  <form th:action="${isNew} ? @{/system/fields} : @{/system/fields/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasAnyErrors()}">
      <div th:each="e : ${#fields.allErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-4">
        <label class="form-label">테이블 <span class="text-danger">*</span></label>
        <input th:field="*{fieldTable}" class="form-control" th:classappend="${#fields.hasErrors('fieldTable')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{fieldTable}"></div>
      </div>
      <div class="col-md-4">
        <label class="form-label">필드 이름 <span class="text-danger">*</span></label>
        <input th:field="*{fieldName}" class="form-control" th:classappend="${#fields.hasErrors('fieldName')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{fieldName}"></div>
      </div>
      <div class="col-md-4">
        <label class="form-label">유형 <span class="text-danger">*</span></label>
        <input th:field="*{fieldType}" class="form-control" th:classappend="${#fields.hasErrors('fieldType')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{fieldType}"></div>
      </div>
      <div class="col-md-6"><label class="form-label">제목</label><input th:field="*{fieldTitle}" class="form-control"></div>
      <div class="col-md-6">
        <label class="form-label">코드 그룹</label>
        <select th:field="*{optionIdx}" class="form-select">
          <option value="">없음</option>
          <option th:each="o : ${options}" th:value="${o.idx}" th:text="|#${o.idx} ${o.optionName}|"></option>
        </select>
      </div>
      <div class="col-md-4"><label class="form-label">PK(0/1)</label><input type="number" min="0" th:field="*{pk}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{pk}"></div></div>
      <div class="col-md-4"><label class="form-label">FK(0/1)</label><input type="number" min="0" th:field="*{fk}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{fk}"></div></div>
      <div class="col-md-4"><label class="form-label">편집 가능(0/1)</label><input type="number" min="0" th:field="*{editable}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{editable}"></div></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/system/fields} : @{/system/fields/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

- [ ] **Step 9: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.web.FieldControllerWebTest' --tests 'com.crosscert.fidoadmin.system.service.FieldServiceTest'`
Expected: PASS (7 + 4 tests).

- [ ] **Step 10: 전체 테스트와 커밋**

Run: `./gradlew test`
Expected: 전체 PASS.

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/fields src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: 필드 정의 화면 (CCFA_FIELDS, 코드 그룹 선택)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
## Task 9: 3부 최종 검증과 인도물

**Files:**
- Create: `src/test/java/com/crosscert/fidoadmin/integration/AssignedIdInsertIntegrationTest.java`
- Modify: `README.md` (문서 절, 실행 절, 알려진 제약)
- Modify: `tasks/todo.md` (3부 절과 Review)

**Interfaces:**
- Consumes: 1부 `OracleContainerSupport`(Docker 없으면 스킵), 3부 Task 2 `SystemInfoService`, 2부 Task 1 `AssignedIdCrudService`, 시드 `CCFA_SYSTEM_INFO('VERSION','1.0.0')`, 로컬 계정 `superuser/Admin1234!`, `kbadmin/Company1234!`.
- Produces: 설계 11 인도물 — 실행 가능한 프로젝트, 갱신된 README, 29개 화면 실측 기록.

- [ ] **Step 1: 할당형 PK 삽입 경로 통합 테스트 작성**

단위 테스트는 `EntityManager` 를 모의로 대체하므로, 실제 Oracle 에서 "기존 키는 거부되고 행이 바뀌지 않는다"를 한 번은 실측해야 한다.

`src/test/java/com/crosscert/fidoadmin/integration/AssignedIdInsertIntegrationTest.java`

```java
package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.service.SystemInfoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * AssignedIdCrudService 가 실제 Oracle 에서 기존 행을 덮어쓰지 않는지 확인한다.
 * save() 였다면 'VERSION' 등록이 MERGE 로 조용히 성공해 PROP_VALUE 가 바뀌었을 것이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SystemInfoService.class)
class AssignedIdInsertIntegrationTest extends OracleContainerSupport {

    @Autowired EntityManager em;
    @Autowired SystemInfoService service;
    @MockitoBean AuditLogger audit;

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemInfo info(String key, String value) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue(value); return i; }

    @Test void existingKeyIsRejectedAndRowUntouched() {
        assertThatThrownBy(() -> service.create(info("VERSION", "9.9.9")))
            .isInstanceOf(DataIntegrityViolationException.class);
        em.clear();
        assertThat(em.find(CcfaSystemInfo.class, "VERSION").getPropValue()).isEqualTo("1.0.0"); // 시드 값 그대로
    }

    @Test void newKeyIsInsertedAndReadable() {
        CcfaSystemInfo saved = service.create(info("IT_NEW_KEY", "v"));
        assertThat(saved.getUpdatedtime()).isNotNull();
        em.clear();
        CcfaSystemInfo found = em.find(CcfaSystemInfo.class, "IT_NEW_KEY");
        assertThat(found).isNotNull();
        assertThat(found.getPropValue()).isEqualTo("v");
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.integration.AssignedIdInsertIntegrationTest'`
Expected: Docker 가 있으면 PASS (2 tests), 없으면 SKIPPED. Docker 가 있는데 실패하면 `AssignedIdCrudService.insert()` 또는 `SystemInfoService.assignedId()` 를 의심한다. `Could not find a valid Docker environment` 면 README 의 `~/.docker-java.properties` 안내를 따른다.

- [ ] **Step 3: 전체 테스트 실측**

```bash
./gradlew clean test
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' build/test-results/test/*.xml \
  | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
```

Expected: `failures=0 errors=0`. 총 개수와 스킵 수를 Step 8 의 Review 에 적는다(Docker 가 있으면 `skipped=0`).

- [ ] **Step 4: 로컬 Oracle 과 애플리케이션 기동**

```bash
docker compose -f docker/docker-compose.yml up -d
until docker compose -f docker/docker-compose.yml ps | grep -q healthy; do sleep 5; done
./gradlew bootRun --args='--spring.profiles.active=local' > /tmp/fido-admin-bootrun.log 2>&1 &
until curl -s -o /dev/null http://localhost:8080/login; do sleep 2; done
echo "up"
```

Expected: `up` 이 출력되고 `/tmp/fido-admin-bootrun.log` 에 `Started FidoAdminApplication` 이 있다.

- [ ] **Step 5: 29개 화면 curl 실측 (SUPER 전부 200, COMPANY 시스템 403)**

로그인은 CSRF 토큰을 긁어 폼 POST 한다. 쿠키 병에 세션이 남는다.

```bash
login() { # $1 = user, $2 = pw, $3 = cookie jar
  rm -f "$3"
  token=$(curl -s -c "$3" http://localhost:8080/login | grep -o '_csrf" value="[^"]*' | sed 's/.*value="//')
  curl -s -b "$3" -c "$3" -o /dev/null -w "login %{http_code} -> %{redirect_url}\n" \
    -d "username=$1" -d "password=$2" -d "_csrf=$token" http://localhost:8080/login
}
code() { curl -s -b "$1" -o /dev/null -w "%{http_code}" "http://localhost:8080$2"; }

ALL="/ /companies /fds-policies /licenses /managers /me/password \
/appids /appservers /users /challenges /signs /transaction-hashes /transaction-confirmations /criteria \
/fido2/metadata /fido2/credential-params /fido2/demo-access-codes \
/logs/fido /logs/audit /logs/exceptions /logs/mailing \
/system/props /system/info /system/error-codes /system/fido-clients /system/criteria /system/menus /system/options /system/fields"
SYSTEM="/system/props /system/info /system/error-codes /system/fido-clients /system/criteria /system/menus /system/options /system/fields"

login superuser 'Admin1234!' /tmp/su.jar
for p in $ALL; do printf '%-32s %s\n' "$p" "$(code /tmp/su.jar $p)"; done | tee /tmp/super-routes.txt
echo "SUPER non-200: $(grep -vc ' 200$' /tmp/super-routes.txt)"

login kbadmin 'Company1234!' /tmp/kb.jar
for p in $SYSTEM; do printf '%-32s %s\n' "$p" "$(code /tmp/kb.jar $p)"; done | tee /tmp/company-system-routes.txt
echo "COMPANY non-403: $(grep -vc ' 403$' /tmp/company-system-routes.txt)"
```

Expected:

| 계정 | 경로 | 기대 |
|---|---|---|
| superuser | 위 29개 전부 | `200`, `SUPER non-200: 0` |
| kbadmin | `/system/*` 8개 | `403`, `COMPANY non-403: 0` |

`login` 출력의 `redirect_url` 이 `/login?error` 면 비밀번호·시드를 확인한다.

- [ ] **Step 6: 브라우저 확인 목록 (시스템 화면 8개)**

`http://localhost:8080` 에 `superuser / Admin1234!` 로 로그인해 아래를 확인하고 결과를 Step 8 의 Review 에 적는다.

- [ ] 시스템 설정: `/system/props/new` 에서 키 `PW_FAIL_LIMIT`, 고객사 `전역(시스템)` 으로 등록 → 폼 상단에 "이미 존재하는 값이거나 제약 조건에 어긋납니다." 가 보이고 `/system/props/PW_FAIL_LIMIT@0` 의 값이 `5` 그대로다.
- [ ] 시스템 설정: `/system/props/PW_FAIL_LIMIT@0` 상세가 열리고, 수정에서 값을 `6` 으로 바꾸면 상세로 돌아와 `6` 이 보인다. 다시 `5` 로 되돌린다.
- [ ] 시스템 설정: 키에 `a/b` 를 넣어 등록하면 "키에 '/' 는 쓸 수 없습니다." 가 보인다.
- [ ] 시스템 정보: `VERSION` 을 다시 등록하면 거부되고 값 `1.0.0` 이 그대로다. `IT_MANUAL` 등록 → 상세 → 삭제까지 된다.
- [ ] 에러 코드: `1200` 재등록 거부. `9999` 등록·수정·삭제.
- [ ] FIDO 서버: `FIDO01` 재등록 거부. `FIDO03` 등록 시 상태 기본값 `ON`.
- [ ] 어드민 기준: JSONDATA 에 `{not json` 을 넣으면 "올바른 JSON(객체 또는 배열)이어야 합니다." 가 보이고, `{"a":1}` 은 저장되며 상세에 들여쓰기된 JSON 이 보인다.
- [ ] 메뉴 정의: `/system/menus/1/edit` 의 부모 select 에 `#1 FIDO` 가 없고 `최상위` 가 있다. 하위가 있는 `#1 FIDO` 삭제 시 "하위 메뉴가 1건 있어 삭제할 수 없습니다." 플래시.
- [ ] 코드 그룹/코드: `/system/options/1` 에서 값 `hold` 코드 추가 → 목록에 보임 → 삭제 모달 → 삭제됨. 코드가 남아 있는 그룹 `#1 STATUS` 삭제 시 차단 플래시.
- [ ] 필드 정의: 등록 폼의 코드 그룹 select 에 `#1 STATUS` 가 있고, 선택해 저장하면 목록에 그룹 이름 `STATUS` 가 보인다.
- [ ] 감사 로그: 위 작업 뒤 `/logs/audit` 에 `CCFA_SYSTEM_INFO CREATE IT_MANUAL`, `CCFA_OPTIONS CREATE …`, `CCFA_OPTIONS DELETE …`, `CCFA_CRITERIA CREATE …` 행이 `CREATE/UPDATE/DELETE` 유형으로 남아 있다.
- [ ] 사이드바: `kbadmin` 으로 로그인하면 시스템 그룹이 보이지 않고, 주소창에 `/system/menus` 를 치면 403 페이지가 뜬다.

- [ ] **Step 7: 정적 자원·JS 규칙 확인**

```bash
grep -rn "alert(\|confirm(\|prompt(" src/main/resources ; echo "alert/confirm/prompt: $?"
grep -rn "https\?://" src/main/resources/templates | grep -v "thymeleaf.org\|w3.org" ; echo "external urls: $?"
```

Expected: 첫 grep 은 아무것도 출력하지 않고 `alert/confirm/prompt: 1`, 둘째도 출력 없이 `external urls: 1` (grep 이 못 찾으면 종료 코드 1).

- [ ] **Step 8: README 와 tasks/todo.md 갱신**

`README.md` 의 `## 실행` 절 맨 아래(운영 환경 변수 문단 다음)에 한 줄을 더한다.

```markdown
설계 5.2 의 화면 29개(대시보드 1, 폼 1, CRUD 18, 조회 9)가 모두 구현되어 있다. 메뉴는 `MenuRegistry` 에 고정되어 있고 `CCFA_MENU` 는 데이터로만 다룬다.
```

`## 알려진 제약` 절 끝에 한 줄을 더한다.

```markdown
- `CCFA_SYSTEM_PROP` 화면은 복합키를 경로 한 조각 `{PROP_KEY}@{COMPANY_IDX}` 로 다루므로 `PROP_KEY` 에 `/` 가 들어간 키는 화면에서 지원하지 않는다(등록 폼에서 거부).
```

`## 문서` 절을 아래로 바꾼다(2부 Task 19 에서 2부·3부 줄을 이미 넣었다면 3부 줄만 확인한다).

```markdown
## 문서

- 설계: `docs/superpowers/specs/2026-09-16-fido-admin-design.md`
- 구현 계획 1부(기반): `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md`
- 구현 계획 2부(업무 화면): `docs/superpowers/plans/2026-09-17-fido-admin-part2-screens.md`
- 구현 계획 3부(시스템 화면·최종 검증): `docs/superpowers/plans/2026-09-17-fido-admin-part3-system.md`
```

`tasks/todo.md` 끝에 아래 절을 더하고 실측값을 채운다.

```markdown
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

## Review (YYYY-MM-DD)

- clean test: tests=N skipped=S failures=0 errors=0 (통합 테스트 실제 실행 여부: 예/아니오)
- SUPER 29개 경로 200, COMPANY /system/* 8개 403 (Step 5 출력 첨부)
- 브라우저 확인 목록 12항목 통과 (미통과 항목과 조치: …)
- 남은 위험: 시퀀스 이름 `<TABLE>_SEQ` 임시(README 절차대로 교체 전 운영 INSERT 보장 없음), 1부 Review 의 수용 위험 유지
```

- [ ] **Step 9: 애플리케이션 종료와 커밋**

```bash
pkill -f 'FidoAdminApplication' || true
git add README.md tasks/todo.md src/test/java/com/crosscert/fidoadmin/integration/AssignedIdInsertIntegrationTest.java
git commit -m "docs: README 와 3부 완료 검증

- 할당형 PK 삽입 경로 Oracle 통합 테스트
- 29개 화면 curl·브라우저 실측 기록
- 복합키 경로 제약 명시

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 10: 인계 메모**

작업 완료 보고에 아래를 그대로 적는다.

- 운영 반영 전 필수: README "시퀀스 이름 교체" 절차. 실제 시퀀스 이름을 받기 전까지 IDX 채번 테이블의 운영 INSERT 는 보장하지 않는다.
- `docker/init/*.sql` 은 로컬 검증 전용이며 운영 DB 에 적용하지 않는다.
- 설계 3.3 적용으로 CRITERIA·FIDO2 화면이 SUPER 전용이 되었다(2부 Task 1). COMPANY 에게도 열어야 하면 `MenuRegistry`, `SecurityConfig`, 각 서비스의 `requireSuperForGlobalTable()` 우회 방식을 함께 결정해야 한다.

---
