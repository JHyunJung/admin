# 이전 어드민 기능 보강 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이전 어드민에 있던 FIDO 서버 reload 신호, 입력 검증 3종, 고객사 생성·삭제 시 딸린 데이터, 외부 라이선스 API, 비밀번호 만료를 옮긴다.

**Architecture:** reload 는 설정 변경 서비스가 `FidoConfigChanged` 이벤트를 발행하고 커밋 후 리스너가 단일 스레드 실행기에서 JDK `HttpClient` 로 `ON` 서버마다 `GET {url}/api/command/reload` 를 보낸다. 수동 전송은 동기로 같은 클라이언트를 부른다. 비밀번호 만료는 JPA 엔티티에 매핑하지 않은 `LAST_PW_CHANGE_DATE` 를 JDBC 로만 다루고, 기동 시 열 존재를 탐지해 없으면 스스로 꺼진다. 나머지는 기존 `CrudService`/`CrudController` 훅과 Bean Validation 에 얹는다.

**Tech Stack:** Java 17, Spring Boot 3.5.3(Web MVC, Security, Data JPA, JDBC), Thymeleaf, Oracle(ojdbc11), JUnit 5, Mockito, Spring Security Test, Testcontainers(`gvenzl/oracle-free:23-slim`).

**Spec:** `docs/superpowers/specs/2026-10-01-legacy-feature-parity-design.md`

## Global Constraints

- 새 의존성을 추가하지 않는다. HTTP 호출은 JDK `java.net.http.HttpClient`, 테스트 서버는 JDK `com.sun.net.httpserver.HttpServer`.
- `CrudService` 생성자 시그니처를 바꾸지 않는다(하위 서비스 수십 개에 영향).
- `CcfaManager` 엔티티와 `src/test/resources/erd-columns.txt` 에 `LAST_PW_CHANGE_DATE` 를 넣지 않는다(`ErdConformanceTest` 는 그대로 통과해야 한다).
- `WebMvcConfig` 에 새 생성자 의존성을 넣지 않는다 — `@WebMvcTest` 약 39개가 `WebMvcConfig` 를 `@Import` 한다. 새 인터셉터는 상태 없이 `new` 로 만든다.
- reload 대상은 `CCFA_FIDOCLIENT.STATUS = 'ON'` 만. URL 은 `SERVERURL` 끝 `/` 를 뗀 뒤 `/api/command/reload`. 스킴은 `http`/`https` 만. 리다이렉트 따라가지 않음. 연결 제한 2초, 응답 제한 5초.
- 실행기: 스레드 1, 대기열 50, 넘치면 WARN 후 버림.
- 사용자에게 보이는 문구는 아래 그대로 쓴다.
  - `FIDO 서버 {n}대에 reload 를 보냈습니다.`
  - `reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다.`
  - `올바른 IP 형식이 아닙니다: {항목}`
  - `이미 등록된 코드와 ID값 입니다.`
  - `영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.`
  - `모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요.`
  - `비밀번호를 변경한 지 {기간}일이 지났습니다. 새 비밀번호로 변경하세요.`
- 비밀번호 만료 설정 키: `fido-admin.password-expiry.enabled` (`auto` 기본 | `false`), 기간 `CCFA_SYSTEM_PROP`(회사 0) `PW_EXPIRY_DAYS`, 기본 90.
- 커밋 메시지는 한국어 `feat:`/`fix:`/`docs:`/`test:` 접두어 + 마지막 줄 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 테스트 실행: `./gradlew test --tests '<FQCN or pattern>'`. 전체는 `./gradlew test`. Docker 가 없으면 Testcontainers 테스트는 건너뛴다. `EntityBootTest`, `AuditLogRoundTripTest`, `LoginLockLoadTest`, `SelectedTenantWiringTest` 4건은 로컬 Oracle 이 없으면 원래 실패한다 — 이 계획의 실패로 치지 않는다.

## Review Focus

- FIDO 서버 코드가 `reload` 인 행 — `POST /system/fido-clients/reload` 가 수정(`POST /{id}`) 대신 수동 전송으로 잡힌다. 등록 시 `reload` 를 예약어로 막아야 한다(Task 6).
- `SERVERURL` 이 공백·끝 `/`·빈 값·`ftp://` 인 행 — 예외 없이 실패 결과 하나로 끝나고 다음 서버 전송은 계속돼야 한다(Task 5).
- 만료 표시가 있는 세션에서 `/me/password` POST, 정적 자원, 로그아웃 — 막히면 운영자가 갇힌다(Task 10).
- FDS IP 입력 `" 10.0.0.1 , 10.0.0.0/8 "` 처럼 쉼표 주변 공백 — 통과해야 한다. `"10.0.0.1,"`(끝 쉼표) — 빈 항목으로 거부(Task 2).
- 외부 라이선스 파일명에 점이 있거나(`abc.lic`) 같은 `HASHVALUE` 행이 여러 개 — 점까지 통째로 잡고 IDX 가 가장 작은 행을 준다(Task 4).

---

### Task 1: 비밀번호 대소문자 필수

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/common/PasswordPolicy.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/auth/PasswordChangeForm.java:26`
- Modify: `src/main/resources/templates/auth/password.html:17`, `src/main/resources/templates/signup/form.html:41`
- Test: `src/test/java/com/crosscert/fidoadmin/common/PasswordPolicyTest.java`, `src/test/java/com/crosscert/fidoadmin/auth/PasswordChangeControllerWebTest.java`

**Interfaces:**
- Produces: `PasswordPolicy.PATTERN`(변경), `PasswordPolicy.MESSAGE`(신규 `String` 상수)

- [ ] **Step 1: 실패하는 테스트 추가** — `PasswordPolicyTest` 에 추가

```java
    @Test void rejectsMissingUppercase() {
        BindingResult b = bind("company1234!", "company1234!");
        assertThat(b.getFieldError("password").getDefaultMessage()).isEqualTo(PasswordPolicy.MESSAGE);
    }

    @Test void rejectsMissingLowercase() {
        BindingResult b = bind("COMPANY1234!", "COMPANY1234!");
        assertThat(b.getFieldError("password").getDefaultMessage()).isEqualTo(PasswordPolicy.MESSAGE);
    }

    @Test void messageNamesAllFourKinds() {
        assertThat(PasswordPolicy.MESSAGE).isEqualTo("영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.");
    }
```

`PasswordChangeControllerWebTest` 에 추가(기존 `change(pw, confirm)` 헬퍼와 검증 스타일을 따른다 — 파일의 `rejects...` 테스트 하나를 열어 폼 오류 검증 방식을 그대로 쓴다):

```java
    @Test void rejectsNewPasswordWithoutUppercase() throws Exception {
        mvc.perform(change("newpass5678!", "newpass5678!"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/password"))
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.PasswordPolicyTest' --tests 'com.crosscert.fidoadmin.auth.PasswordChangeControllerWebTest'`
Expected: 컴파일 오류 `cannot find symbol: MESSAGE`

- [ ] **Step 3: 구현** — `PasswordPolicy`

```java
    /** 영문 대문자·소문자·숫자·특수문자를 각각 하나 이상 포함해야 한다(이전 어드민 ManagerValidator 와 같다). */
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    /** 형식 위반 문구. 어노테이션 속성에서도 참조하므로 컴파일 상수다. */
    public static final String MESSAGE = "영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.";
```

`validatePassword` 의 `binding.rejectValue(pwField, "policy", "영문, 숫자, 특수문자를 모두 포함해야 합니다.")` 를 `binding.rejectValue(pwField, "policy", MESSAGE)` 로.

`PasswordChangeForm`:
```java
    @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
```

두 템플릿의 `<div class="form-text">8~64자, 영문·숫자·특수문자 포함</div>` 를 `<div class="form-text">8~64자, 영문 대문자·소문자·숫자·특수문자 포함</div>` 로.

- [ ] **Step 4: 전체 테스트로 예시 비밀번호 점검**

Run: `./gradlew test`
Expected: 새 테스트 PASS. 소문자만 쓰는 예시 비밀번호로 "통과"를 기대하던 기존 테스트가 있으면 FAIL — 그 예시 값을 대문자를 넣은 값(예: `Company1234!`)으로 고친다. 기대 문구를 `"영문, 숫자, 특수문자"` 로 검사하던 곳은 `PasswordPolicy.MESSAGE` 로 바꾼다. 다시 돌려 PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java/com/crosscert/fidoadmin/common/PasswordPolicy.java src/main/java/com/crosscert/fidoadmin/auth/PasswordChangeForm.java src/main/resources/templates/auth/password.html src/main/resources/templates/signup/form.html src/test/java
git commit -m "feat: 운영자 비밀번호에 대문자와 소문자를 모두 요구한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: FDS 정책 IP 형식 검증

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/common/IpRuleList.java`
- Create: `src/main/java/com/crosscert/fidoadmin/common/IpRuleListValidator.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java:14,18`
- Test: `src/test/java/com/crosscert/fidoadmin/common/IpRuleListValidatorTest.java`, `src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyControllerWebTest.java`

**Interfaces:**
- Produces: `@IpRuleList` 필드 어노테이션, `IpRuleListValidator.firstInvalid(String value): Optional<String>`(정적, 테스트·재사용용)

- [ ] **Step 1: 실패하는 테스트**

`IpRuleListValidatorTest`:
```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IpRuleListValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "0.0.0.0", "255.255.255.255", "10.0.0.0/8", "10.0.0.0/0", "10.0.0.0/32",
        "10.0.0.1~10.0.0.9", "10.0.0.1~10.0.0.1", "10.0.0.1,192.168.0.0/16,1.1.1.1~1.1.1.5",
        " 10.0.0.1 , 10.0.0.0/8 "})
    void accepts(String v) {
        assertThat(IpRuleListValidator.firstInvalid(v)).isEmpty();
    }

    @Test void blankIsValid() {
        assertThat(IpRuleListValidator.firstInvalid(null)).isEmpty();
        assertThat(IpRuleListValidator.firstInvalid("")).isEmpty();
        assertThat(IpRuleListValidator.firstInvalid("   ")).isEmpty();
    }

    @Test void rejectsOctetOver255() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.256")).contains("10.0.0.256"); }
    @Test void rejectsPrefixOver32() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.0/33")).contains("10.0.0.0/33"); }
    @Test void rejectsReversedRange() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.9~10.0.0.1")).contains("10.0.0.9~10.0.0.1"); }
    @Test void rejectsEmptyItem() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,,10.0.0.2")).contains(""); }
    @Test void rejectsTrailingComma() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,")).contains(""); }
    @Test void rejectsIpv6() { assertThat(IpRuleListValidator.firstInvalid("::1")).contains("::1"); }
    @Test void rejectsGarbage() { assertThat(IpRuleListValidator.firstInvalid("10.0.0.1,abc")).contains("abc"); }
    @Test void rejectsThreeOctets() { assertThat(IpRuleListValidator.firstInvalid("10.0.0")).contains("10.0.0"); }
    @Test void reportsFirstInvalidOnly() { assertThat(IpRuleListValidator.firstInvalid("x,y")).contains("x"); }
}
```

`FdsPolicyControllerWebTest` 에 추가(기존 `blankAndCountryShowsFormWithMessage` 와 같은 모양):
```java
    @Test void invalidIpShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fds-policies").with(user(companyUser)).with(csrf())
                .param("andCountry", "KR").param("orCountry", "KR").param("andIp", "10.0.0.256"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/fds-policy/form"))
            .andExpect(content().string(containsString("올바른 IP 형식이 아닙니다: 10.0.0.256")));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.IpRuleListValidatorTest' --tests 'com.crosscert.fidoadmin.company.web.FdsPolicyControllerWebTest'`
Expected: 컴파일 오류 `cannot find symbol: IpRuleListValidator`

- [ ] **Step 3: 구현**

`IpRuleList.java`:
```java
package com.crosscert.fidoadmin.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 쉼표로 구분한 IPv4 규칙 목록. 항목은 단일 IP, CIDR(a.b.c.d/n), 범위(a.b.c.d~e.f.g.h) 중 하나.
 * 이전 어드민 FDSPolicyValidator.checkIp 를 잇는다. 빈 값은 통과.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IpRuleListValidator.class)
public @interface IpRuleList {
    String message() default "올바른 IP 형식이 아닙니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`IpRuleListValidator.java`:
```java
package com.crosscert.fidoadmin.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;
import java.util.regex.Pattern;

public class IpRuleListValidator implements ConstraintValidator<IpRuleList, String> {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        Optional<String> bad = firstInvalid(value);
        if (bad.isEmpty()) return true;
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate("올바른 IP 형식이 아닙니다: " + escape(bad.get()))
            .addConstraintViolation();
        return false;
    }

    /** 첫 번째 잘못된 항목. 모두 맞으면 비어 있다. 공백은 모두 지운 뒤 쉼표로 나눈다. */
    public static Optional<String> firstInvalid(String value) {
        if (value == null) return Optional.empty();
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) return Optional.empty();
        for (String item : compact.split(",", -1)) {
            if (!validItem(item)) return Optional.of(item);
        }
        return Optional.empty();
    }

    private static boolean validItem(String item) {
        int slash = item.indexOf('/');
        if (slash >= 0) {
            String prefix = item.substring(slash + 1);
            if (!prefix.matches("\\d{1,2}")) return false;
            return toLong(item.substring(0, slash)) >= 0 && Integer.parseInt(prefix) <= 32;
        }
        int tilde = item.indexOf('~');
        if (tilde >= 0) {
            long start = toLong(item.substring(0, tilde));
            long end = toLong(item.substring(tilde + 1));
            return start >= 0 && end >= 0 && start <= end;
        }
        return toLong(item) >= 0;
    }

    /** IPv4 를 부호 없는 정수로. 형식이 틀리면 -1. */
    private static long toLong(String ip) {
        var m = IPV4.matcher(ip);
        if (!m.matches()) return -1;
        long v = 0;
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(m.group(i));
            if (octet > 255) return -1;
            v = (v << 8) | octet;
        }
        return v;
    }

    /** 메시지 템플릿의 EL·파라미터 기호를 무력화한다. 사용자가 넣은 값이 템플릿으로 해석되지 않게. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
```

`FdsPolicyForm` 의 두 필드:
```java
    @IpRuleList @ByteSize(max = 4000) private String andIp;
    ...
    @IpRuleList @ByteSize(max = 4000) private String orIp;
```
(import `com.crosscert.fidoadmin.common.IpRuleList`)

- [ ] **Step 4: 통과 확인**

Run: Step 2 와 같은 명령
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/common/IpRuleList.java src/main/java/com/crosscert/fidoadmin/common/IpRuleListValidator.java src/main/java/com/crosscert/fidoadmin/company/web/FdsPolicyForm.java src/test/java/com/crosscert/fidoadmin/common/IpRuleListValidatorTest.java src/test/java/com/crosscert/fidoadmin/company/web/FdsPolicyControllerWebTest.java
git commit -m "feat: FDS 정책 IP 목록을 단일·CIDR·범위 형식으로만 받는다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 멤버코드 중복 검사

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/repository/AppserverRepository.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/service/AppserverService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/web/AppserverController.java`
- Test: `src/test/java/com/crosscert/fidoadmin/fido/service/AppserverServiceTest.java`, `src/test/java/com/crosscert/fidoadmin/fido/web/AppserverControllerWebTest.java`

**Interfaces:**
- Produces: `AppserverService.existsDuplicate(String memberCode, String memberId, Long excludeIdx): boolean`

- [ ] **Step 1: 실패하는 테스트**

`AppserverServiceTest` 에 추가(파일의 기존 테넌트 준비 방식을 따른다 — 현재 테넌트 고객사가 `1L` 이라고 가정한 부분은 파일의 실제 값으로 맞춘다):
```java
    @Test void duplicateCheckIsScopedToTenantAndExcludesSelf() {
        when(repo.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(1L, "M01", "ID01", 5L)).thenReturn(true);
        assertThat(service.existsDuplicate("M01", "ID01", 5L)).isTrue();
    }

    @Test void duplicateCheckOnCreateUsesExistsWithoutExclusion() {
        when(repo.existsByCompanyIdxAndMemberCodeAndMemberId(1L, "M01", "ID01")).thenReturn(true);
        assertThat(service.existsDuplicate("M01", "ID01", null)).isTrue();
    }

    @Test void blankValuesAreNeverDuplicates() {
        assertThat(service.existsDuplicate(null, "ID01", null)).isFalse();
        assertThat(service.existsDuplicate("M01", " ", null)).isFalse();
    }
```

`AppserverControllerWebTest` 에 추가(파일의 기존 등록 POST 테스트의 파라미터 이름·사용자·세션 준비를 그대로 쓴다):
```java
    @Test void createRejectsDuplicateMemberCodeAndId() throws Exception {
        when(service.existsDuplicate("M01", "ID01", null)).thenReturn(true);
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "M01").param("memberId", "ID01").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
            .andExpect(content().string(containsString("이미 등록된 코드와 ID값 입니다.")));
        verify(service, never()).create(any());
    }

    @Test void updateExcludesSelfFromDuplicateCheck() throws Exception {
        when(service.existsDuplicate("M01", "ID01", 5L)).thenReturn(false);
        mvc.perform(post("/appservers/5").with(user(companyUser)).with(csrf())
                .param("memberCode", "M01").param("memberId", "ID01").param("type", "use"))
            .andExpect(status().is3xxRedirection());
        verify(service).existsDuplicate("M01", "ID01", 5L);
    }
```
뷰 이름이 다르면 `AppserverController.viewDir()` 값 + `/form` 으로 맞춘다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.AppserverServiceTest' --tests 'com.crosscert.fidoadmin.fido.web.AppserverControllerWebTest'`
Expected: 컴파일 오류 `cannot find symbol: existsDuplicate`

- [ ] **Step 3: 구현**

`AppserverRepository` 에 추가:
```java
    boolean existsByCompanyIdxAndMemberCodeAndMemberId(Long companyIdx, String memberCode, String memberId);
    boolean existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(Long companyIdx, String memberCode, String memberId, Long idx);
```

`AppserverService` 에 추가:
```java
    /**
     * 현재 고객사에 같은 MEMBER_CODE + MEMBER_ID 행이 있는가. 수정이면 자기 행({@code excludeIdx})은 뺀다.
     * 이전 어드민 FidoController 의 membercode 중복 검사를 잇는다. DB 유니크 제약은 없다.
     */
    @Transactional(readOnly = true)
    public boolean existsDuplicate(String memberCode, String memberId, Long excludeIdx) {
        if (memberCode == null || memberCode.isBlank() || memberId == null || memberId.isBlank()) return false;
        AppserverRepository r = (AppserverRepository) repository;
        Long company = tenant.companyIdx();
        return excludeIdx == null
            ? r.existsByCompanyIdxAndMemberCodeAndMemberId(company, memberCode, memberId)
            : r.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(company, memberCode, memberId, excludeIdx);
    }
```
(import `org.springframework.transaction.annotation.Transactional`, `com.crosscert.fidoadmin.fido.repository.AppserverRepository`)

`AppserverController` 에 추가:
```java
    @Override
    protected void validate(AppserverForm form, Long id, BindingResult binding) {
        if (service.existsDuplicate(form.getMemberCode(), form.getMemberId(), id)) {
            binding.rejectValue("memberId", "duplicate", "이미 등록된 코드와 ID값 입니다.");
        }
    }
```
(컨트롤러의 서비스 필드 이름이 `service` 가 아니면 맞춘다. import `org.springframework.validation.BindingResult`)

- [ ] **Step 4: 통과 확인** — Step 2 명령, Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/fido src/test/java/com/crosscert/fidoadmin/fido
git commit -m "feat: 같은 고객사에서 멤버코드와 멤버ID 조합이 겹치면 막는다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 외부 라이선스 조회 API

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/company/web/ExternalLicenseController.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/company/repository/CcfaLicenseRepository.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java:31,36`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/WebMvcConfig.java:35,42`
- Test: `src/test/java/com/crosscert/fidoadmin/company/web/ExternalLicenseControllerWebTest.java`

**Interfaces:**
- Produces: `CcfaLicenseRepository.findFirstByHashvalueOrderByIdxAsc(String hashvalue): Optional<CcfaLicense>`

- [ ] **Step 1: 실패하는 테스트**

```java
package com.crosscert.fidoadmin.company.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FIDO 서버가 로그인 없이 부르는 경로다. 인증·CSRF·테넌트 선택 어느 것에도 걸리면 안 된다. */
@WebMvcTest(controllers = ExternalLicenseController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class ExternalLicenseControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean CcfaLicenseRepository licenses;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    private CcfaLicense license(String body) { CcfaLicense l = new CcfaLicense(); l.setLicense(body); return l; }

    @Test void getWithoutLoginReturnsLicenseText() throws Exception {
        when(licenses.findFirstByHashvalueOrderByIdxAsc("abc123")).thenReturn(Optional.of(license("LICENSE-BODY")));
        mvc.perform(get("/external/license/abc123"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andExpect(content().string("LICENSE-BODY"));
    }

    @Test void postWithoutLoginOrCsrfAlsoWorks() throws Exception {
        when(licenses.findFirstByHashvalueOrderByIdxAsc("abc123")).thenReturn(Optional.of(license("LICENSE-BODY")));
        mvc.perform(post("/external/license/abc123"))
            .andExpect(status().isOk())
            .andExpect(content().string("LICENSE-BODY"));
    }

    @Test void unknownHashReturnsEmptyOk() throws Exception {
        when(licenses.findFirstByHashvalueOrderByIdxAsc("nope")).thenReturn(Optional.empty());
        mvc.perform(get("/external/license/nope"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test void nullLicenseColumnReturnsEmptyOk() throws Exception {
        when(licenses.findFirstByHashvalueOrderByIdxAsc("h")).thenReturn(Optional.of(license(null)));
        mvc.perform(get("/external/license/h")).andExpect(status().isOk()).andExpect(content().string(""));
    }

    @Test void filenameWithDotIsCapturedWhole() throws Exception {
        when(licenses.findFirstByHashvalueOrderByIdxAsc("abc.lic")).thenReturn(Optional.of(license("DOT")));
        mvc.perform(get("/external/license/abc.lic"))
            .andExpect(status().isOk())
            .andExpect(content().string("DOT"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.ExternalLicenseControllerWebTest'`
Expected: 컴파일 오류 `cannot find symbol: ExternalLicenseController`

- [ ] **Step 3: 구현**

`CcfaLicenseRepository`:
```java
    /** 외부 라이선스 조회. HASHVALUE 가 겹치면 먼저 만든 행(IDX 최소)을 준다. */
    java.util.Optional<CcfaLicense> findFirstByHashvalueOrderByIdxAsc(String hashvalue);
```

`ExternalLicenseController.java`:
```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * FIDO 서버가 라이선스 원문을 받아 가는 경로. 이전 어드민 ExternalController 와 같다 —
 * HASHVALUE 로 찾아 LICENSE 를 평문으로 주고, 없으면 200 빈 본문. 로그인·CSRF 없이 열려 있다.
 * 고객사 구분이 없다(해시가 곧 식별자).
 */
@RestController
@RequiredArgsConstructor
public class ExternalLicenseController {

    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final CcfaLicenseRepository licenses;

    @Transactional(readOnly = true)
    @RequestMapping(value = "/external/license/{filename}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> license(@PathVariable String filename) {
        String body = licenses.findFirstByHashvalueOrderByIdxAsc(filename)
            .map(CcfaLicense::getLicense)
            .orElse("");
        return ResponseEntity.ok().contentType(TEXT_UTF8).body(body == null ? "" : body);
    }
}
```

`SecurityConfig`:
```java
            .csrf(c -> c.ignoringRequestMatchers("/api/svc/**", "/external/**"))
```
permitAll 목록에 `"/external/**"` 추가, 주석 한 줄: `// /external/** 는 FIDO 서버가 라이선스를 받아 가는 경로다(ExternalLicenseController).`

`WebMvcConfig`: 두 `excludePathPatterns` 에 `"/external/**"` 추가.

- [ ] **Step 4: 통과 확인** — Step 2 명령, Expected: PASS. 점 포함 테스트가 실패하면(`abc` 만 잡힘) 매핑을 `"/external/license/{filename:.+}"` 로 바꾸고 다시 돌린다.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/company src/main/java/com/crosscert/fidoadmin/config src/test/java/com/crosscert/fidoadmin/company/web/ExternalLicenseControllerWebTest.java
git commit -m "feat: FIDO 서버용 외부 라이선스 조회 경로를 이전 어드민과 같게 연다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: reload 클라이언트·이벤트·리스너

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/ReloadResult.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/FidoConfigChanged.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/FidoReloadClient.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/FidoReloadListener.java`
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/FidoReloadConfig.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFidoclientRepository.java`
- Test: `src/test/java/com/crosscert/fidoadmin/system/reload/FidoReloadClientTest.java`, `src/test/java/com/crosscert/fidoadmin/system/reload/FidoReloadListenerTest.java`

**Interfaces:**
- Produces:
  - `record ReloadResult(String servercode, String serverurl, boolean ok, String detail, long elapsedMs)`
  - `record FidoConfigChanged(String reason)`
  - `FidoReloadClient.reloadAll(): List<ReloadResult>`
  - `FidoReloadClient(CcfaFidoclientRepository clients, HttpClient http, Duration responseTimeout)` (패키지 공개 생성자, 테스트용) + `@Autowired` 생성자 `FidoReloadClient(CcfaFidoclientRepository clients)`
  - `CcfaFidoclientRepository.findByStatusOrderByServercodeAsc(String status): List<CcfaFidoclient>`
  - 빈 이름 `fidoReloadExecutor` (`java.util.concurrent.Executor`)

- [ ] **Step 1: 실패하는 클라이언트 테스트**

```java
package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FidoReloadClientTest {

    HttpServer server;
    String base;
    List<String> hits = new CopyOnWriteArrayList<>();
    CcfaFidoclientRepository repo = mock(CcfaFidoclientRepository.class);
    FidoReloadClient client;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/api/command/reload", ex -> { hits.add(ex.getRequestMethod() + " " + ex.getRequestURI());
            byte[] b = "reloaded".getBytes(); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        server.createContext("/fail/api/command/reload", ex -> { hits.add("fail"); ex.sendResponseHeaders(500, -1); ex.close(); });
        server.createContext("/slow/api/command/reload", ex -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/redirect/api/command/reload", ex -> {
            ex.getResponseHeaders().add("Location", base + "/ok/api/command/reload"); ex.sendResponseHeaders(302, -1); ex.close(); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        client = new FidoReloadClient(repo, http, Duration.ofMillis(500));
    }

    @AfterEach void stop() { server.stop(0); }

    private CcfaFidoclient c(String code, String url) {
        CcfaFidoclient c = new CcfaFidoclient(); c.setServercode(code); c.setServerurl(url); c.setStatus("ON"); return c;
    }

    @Test void sendsGetToEachOnServerAndReportsSuccess() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(hits).containsExactly("GET /ok/api/command/reload");
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.servercode()).isEqualTo("A");
            assertThat(x.ok()).isTrue();
            assertThat(x.detail()).isEqualTo("HTTP 200");
        });
    }

    @Test void trimsTrailingSlashAndWhitespace() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", "  " + base + "/ok/  ")));
        assertThat(client.reloadAll().get(0).ok()).isTrue();
        assertThat(hits).containsExactly("GET /ok/api/command/reload");
    }

    @Test void non2xxIsFailureAndNextServerStillRuns() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/fail"), c("B", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(r).extracting(ReloadResult::ok).containsExactly(false, true);
        assertThat(r.get(0).detail()).isEqualTo("HTTP 500");
    }

    @Test void timeoutIsFailure() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/slow")));
        ReloadResult r = client.reloadAll().get(0);
        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).containsIgnoringCase("timed out");
    }

    @Test void redirectIsNotFollowed() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(c("A", base + "/redirect")));
        ReloadResult r = client.reloadAll().get(0);
        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).isEqualTo("HTTP 302");
        assertThat(hits).isEmpty();
    }

    @Test void badSchemeBlankAndMalformedUrlsAreFailuresWithoutThrowing() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of(
            c("A", "ftp://x/"), c("B", ""), c("C", null), c("D", "http://bad host"), c("E", base + "/ok")));
        List<ReloadResult> r = client.reloadAll();
        assertThat(r).extracting(ReloadResult::ok).containsExactly(false, false, false, false, true);
        assertThat(r.get(0).detail()).isEqualTo("지원하지 않는 URL: ftp://x/");
    }

    @Test void noOnServersMeansNoResults() {
        when(repo.findByStatusOrderByServercodeAsc("ON")).thenReturn(List.of());
        assertThat(client.reloadAll()).isEmpty();
    }

    @Test void detailIsSingleLineAndCapped() {
        assertThat(FidoReloadClient.oneLine("a\nb\r\nc")).isEqualTo("a b c");
        assertThat(FidoReloadClient.oneLine("x".repeat(300))).hasSize(200);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.reload.FidoReloadClientTest'`
Expected: 컴파일 오류 `cannot find symbol: FidoReloadClient`

- [ ] **Step 3: 클라이언트 구현**

`CcfaFidoclientRepository` 에 추가:
```java
    java.util.List<CcfaFidoclient> findByStatusOrderByServercodeAsc(String status);
```

`ReloadResult.java`:
```java
package com.crosscert.fidoadmin.system.reload;

/** FIDO 서버 한 대에 reload 를 보낸 결과. detail 은 성공이면 "HTTP 200", 실패면 상태나 예외 한 줄(200자 이내). */
public record ReloadResult(String servercode, String serverurl, boolean ok, String detail, long elapsedMs) {}
```

`FidoConfigChanged.java`:
```java
package com.crosscert.fidoadmin.system.reload;

/**
 * FIDO 서버가 다시 읽어야 하는 데이터가 바뀌었다. 커밋 뒤 {@link FidoReloadListener} 가 reload 를 보낸다.
 * reason 은 로그용 한 줄(예: "APPID 수정 12").
 */
public record FidoConfigChanged(String reason) {}
```

`FidoReloadClient.java`:
```java
package com.crosscert.fidoadmin.system.reload;

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 등록된 FIDO 서버(STATUS='ON')마다 {@code GET {SERVERURL}/api/command/reload} 를 보낸다.
 * 이전 어드민 CCFIDOClientInfo.sendSignal("reload") 를 잇는다.
 *
 * <p>서버 목록은 인증 없는 자가등록(/api/svc/reg)으로 채워지므로 http/https 만 보내고 리다이렉트는 따라가지 않는다.
 * 한 서버의 실패가 다음 서버를 막지 않는다. 어떤 경우에도 예외를 던지지 않고 결과로 돌려준다.
 */
@Component
public class FidoReloadClient {

    static final String STATUS_ON = "ON";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private final CcfaFidoclientRepository clients;
    private final HttpClient http;
    private final Duration responseTimeout;

    @Autowired
    public FidoReloadClient(CcfaFidoclientRepository clients) {
        this(clients, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build(), RESPONSE_TIMEOUT);
    }

    FidoReloadClient(CcfaFidoclientRepository clients, HttpClient http, Duration responseTimeout) {
        this.clients = clients;
        this.http = http;
        this.responseTimeout = responseTimeout;
    }

    public List<ReloadResult> reloadAll() {
        List<ReloadResult> results = new ArrayList<>();
        for (CcfaFidoclient c : clients.findByStatusOrderByServercodeAsc(STATUS_ON)) {
            results.add(send(c));
        }
        return results;
    }

    private ReloadResult send(CcfaFidoclient c) {
        long start = System.nanoTime();
        String raw = c.getServerurl();
        String base = raw == null ? "" : raw.trim().replaceAll("/+$", "");
        if (!(base.startsWith("http://") || base.startsWith("https://"))) {
            return new ReloadResult(c.getServercode(), raw, false, oneLine("지원하지 않는 URL: " + (raw == null ? "" : raw.trim())), 0);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/command/reload"))
                .timeout(responseTimeout).GET().build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            int code = res.statusCode();
            return new ReloadResult(c.getServercode(), raw, code >= 200 && code < 300, "HTTP " + code, millisSince(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ReloadResult(c.getServercode(), raw, false, "중단됨", millisSince(start));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
            return new ReloadResult(c.getServercode(), raw, false, oneLine(msg), millisSince(start));
        }
    }

    private static long millisSince(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }

    static String oneLine(String s) {
        String flat = s.replaceAll("[\\r\\n]+", " ");
        return flat.length() > 200 ? flat.substring(0, 200) : flat;
    }
}
```
(`HttpTimeoutException` 의 메시지는 `"request timed out"` 이다 — 테스트의 `containsIgnoringCase("timed out")` 가 이것을 본다.)

- [ ] **Step 4: 클라이언트 통과 확인** — Step 2 명령, Expected: PASS

- [ ] **Step 5: 실패하는 리스너 테스트**

```java
package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mockito;

/** 커밋 뒤에만, 한 번, 실행기에서 돈다. 롤백이면 보내지 않는다. 실패는 호출자에게 새지 않는다. */
@SpringJUnitConfig(FidoReloadListenerTest.Config.class)
class FidoReloadListenerTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean FidoReloadClient client() { return mock(FidoReloadClient.class); }
        @Bean Executor fidoReloadExecutor() { return Runnable::run; }
        @Bean FidoReloadListener listener(FidoReloadClient c, Executor fidoReloadExecutor) { return new FidoReloadListener(c, fidoReloadExecutor); }
        @Bean PlatformTransactionManager txManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition def) {}
                @Override protected void doCommit(DefaultTransactionStatus s) {}
                @Override protected void doRollback(DefaultTransactionStatus s) {}
            };
        }
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager tx;
    @Autowired FidoReloadClient client;

    @BeforeEach void reset() { Mockito.reset(client); when(client.reloadAll()).thenReturn(List.of()); }

    @Test void sendsOnceAfterCommit() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            publisher.publishEvent(new FidoConfigChanged("APPID 수정 1"));
            verify(client, never()).reloadAll();   // 커밋 전에는 보내지 않는다
        });
        verify(client, times(1)).reloadAll();
    }

    @Test void doesNotSendOnRollback() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            publisher.publishEvent(new FidoConfigChanged("APPID 수정 1"));
            s.setRollbackOnly();
        });
        verify(client, never()).reloadAll();
    }

    @Test void sendsWhenPublishedOutsideTransaction() {
        publisher.publishEvent(new FidoConfigChanged("수동"));
        verify(client, times(1)).reloadAll();
    }

    @Test void clientFailureDoesNotPropagate() {
        when(client.reloadAll()).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> publisher.publishEvent(new FidoConfigChanged("x"))).doesNotThrowAnyException();
    }

    @Test void rejectedExecutionDoesNotPropagate() {
        Executor rejecting = r -> { throw new TaskRejectedException("full"); };
        FidoReloadListener l = new FidoReloadListener(client, rejecting);
        assertThatCode(() -> l.on(new FidoConfigChanged("x"))).doesNotThrowAnyException();
        verify(client, never()).reloadAll();
    }
}
```

- [ ] **Step 6: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.reload.FidoReloadListenerTest'`
Expected: 컴파일 오류 `cannot find symbol: FidoReloadListener`

- [ ] **Step 7: 리스너·설정 구현**

`FidoReloadListener.java`:
```java
package com.crosscert.fidoadmin.system.reload;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 설정 변경이 커밋된 뒤 FIDO 서버들에 reload 를 보낸다. 이전 어드민은 insert 전에 보내 FIDO 서버가
 * 바뀌기 전 값을 다시 읽을 수 있었다. 여기서는 커밋 뒤에만, 저장을 기다리게 하지 않도록 전용 실행기에서 보낸다.
 *
 * <p>감사 로그는 남기지 않는다. 비동기 스레드에는 로그인 사용자가 없고, 원인이 된 변경은 이미 감사 로그에 있다.
 */
@Slf4j
@Component
public class FidoReloadListener {

    private final FidoReloadClient client;
    private final Executor executor;

    public FidoReloadListener(FidoReloadClient client, @Qualifier("fidoReloadExecutor") Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(FidoConfigChanged event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            // reload 는 멱등이다. 다음 변경이나 수동 전송이 대신한다.
            log.warn("FIDO 서버 reload 대기열이 가득 차 버립니다: reason={}", event.reason());
        }
    }

    private void send(FidoConfigChanged event) {
        try {
            for (ReloadResult r : client.reloadAll()) {
                if (r.ok()) {
                    log.info("FIDO 서버 reload 성공: reason={} server={} {} {}ms", event.reason(), r.servercode(), r.detail(), r.elapsedMs());
                } else {
                    log.warn("FIDO 서버 reload 실패: reason={} server={} url={} {}", event.reason(), r.servercode(), r.serverurl(), r.detail());
                }
            }
        } catch (RuntimeException e) {
            log.warn("FIDO 서버 reload 중 오류: reason={}", event.reason(), e);
        }
    }
}
```
(`TaskRejectedException` 은 `RejectedExecutionException` 의 하위 클래스다.)

`FidoReloadConfig.java`:
```java
package com.crosscert.fidoadmin.system.reload;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class FidoReloadConfig {

    /** 스레드 1, 대기열 50. 넘치면 execute 가 거부 예외를 던지고 리스너가 WARN 후 버린다. */
    @Bean(name = "fidoReloadExecutor")
    public Executor fidoReloadExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("fido-reload-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }
}
```

참고: `Executor` 빈이 생기면 Spring Boot 의 기본 `applicationTaskExecutor` 가 만들어지지 않는다. 이 앱에는 `@EnableAsync`·`@Async`·비동기 MVC 반환이 없어 영향이 없다(`grep -rn "@Async\|EnableAsync\|Callable<\|DeferredResult" src/main` 로 확인하고, 결과가 있으면 이 빈에 `@Bean(name = "fidoReloadExecutor", defaultCandidate = false)` 를 쓴다).

- [ ] **Step 8: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.reload.*'` 그리고 `./gradlew test`
Expected: PASS (위 로컬 Oracle 4건 제외)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/system/reload src/main/java/com/crosscert/fidoadmin/system/repository/CcfaFidoclientRepository.java src/test/java/com/crosscert/fidoadmin/system/reload
git commit -m "feat: 설정 변경이 커밋되면 FIDO 서버들에 reload 를 보낸다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: reload 수동 전송

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/system/reload/FidoReloadService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/system/web/FidoClientController.java`
- Modify: `src/main/resources/templates/system/fido-clients/list.html:6-9`
- Test: `src/test/java/com/crosscert/fidoadmin/system/reload/FidoReloadServiceTest.java`, `src/test/java/com/crosscert/fidoadmin/system/web/FidoClientControllerWebTest.java`

**Interfaces:**
- Consumes: `FidoReloadClient.reloadAll()`, `ReloadResult` (Task 5)
- Produces: `FidoReloadService.reloadNow(): List<ReloadResult>`

- [ ] **Step 1: 실패하는 테스트**

`FidoReloadServiceTest`:
```java
package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FidoReloadServiceTest {
    FidoReloadClient client = mock(FidoReloadClient.class);
    AuditLogger audit = mock(AuditLogger.class);
    FidoReloadService service = new FidoReloadService(client, audit);

    @Test void auditsSuccessAndFailureCounts() {
        when(client.reloadAll()).thenReturn(List.of(
            new ReloadResult("A", "http://a", true, "HTTP 200", 3),
            new ReloadResult("B", "http://b", false, "HTTP 500", 4)));
        assertThat(service.reloadNow()).hasSize(2);
        verify(audit).log(AuditType.UPDATE, "FIDO 서버 reload 수동 전송 | 성공 1 / 실패 1");
    }
}
```

`FidoClientControllerWebTest` 에 `@MockitoBean com.crosscert.fidoadmin.system.reload.FidoReloadService reload;` 필드를 추가하고 아래 테스트를 추가한다(import `com.crosscert.fidoadmin.system.reload.ReloadResult`, `static ...MockMvcResultMatchers.flash`):
```java
    @Test void manualReloadAllOk() throws Exception {
        when(reload.reloadNow()).thenReturn(List.of(new ReloadResult("FIDO01", "https://a", true, "HTTP 200", 5),
            new ReloadResult("FIDO02", "https://b", true, "HTTP 200", 5)));
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(redirectedUrl("/system/fido-clients"))
            .andExpect(flash().attribute("flashSuccess", "FIDO 서버 2대에 reload 를 보냈습니다."));
    }

    @Test void manualReloadPartialFailureListsFailures() throws Exception {
        when(reload.reloadNow()).thenReturn(List.of(new ReloadResult("FIDO01", "https://a", true, "HTTP 200", 5),
            new ReloadResult("FIDO02", "https://b", false, "HTTP 500", 5)));
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(redirectedUrl("/system/fido-clients"))
            .andExpect(flash().attribute("flashError", "FIDO 서버 2대 중 1대 reload 실패 — FIDO02: HTTP 500"));
    }

    @Test void manualReloadWithNoOnServers() throws Exception {
        when(reload.reloadNow()).thenReturn(List.of());
        mvc.perform(post("/system/fido-clients/reload").with(user(superUser)).with(csrf()))
            .andExpect(flash().attribute("flashError", "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다."));
    }

    @Test void manualReloadForbiddenForCompanyUser() throws Exception {
        mvc.perform(post("/system/fido-clients/reload").with(user(companyUser)).with(csrf()))
            .andExpect(status().isForbidden());
        verify(reload, never()).reloadNow();
    }

    @Test void listShowsReloadButton() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("servercode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/system/fido-clients").with(user(superUser)))
            .andExpect(content().string(containsString("/system/fido-clients/reload")))
            .andExpect(content().string(containsString("지금 reload 보내기")));
    }

    @Test void reloadIsReservedServerCode() throws Exception {
        mvc.perform(post("/system/fido-clients").with(user(superUser)).with(csrf())
                .param("servercode", "reload").param("servername", "x").param("serverurl", "https://x").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코드로 쓸 수 없는 값입니다: reload")));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.system.reload.FidoReloadServiceTest' --tests 'com.crosscert.fidoadmin.system.web.FidoClientControllerWebTest'`
Expected: 컴파일 오류 `cannot find symbol: FidoReloadService`

- [ ] **Step 3: 구현**

`FidoReloadService.java`:
```java
package com.crosscert.fidoadmin.system.reload;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** FIDO 서버 목록 화면의 수동 reload. 동기로 보내고 결과를 돌려주며 감사 로그를 남긴다. */
@Service
@RequiredArgsConstructor
public class FidoReloadService {

    private final FidoReloadClient client;
    private final AuditLogger audit;

    public List<ReloadResult> reloadNow() {
        List<ReloadResult> results = client.reloadAll();
        long ok = results.stream().filter(ReloadResult::ok).count();
        audit.log(AuditType.UPDATE, "FIDO 서버 reload 수동 전송 | 성공 " + ok + " / 실패 " + (results.size() - ok));
        return results;
    }
}
```

`FidoClientController` — 필드·메서드 추가, 예약어 확장:
```java
    private final FidoReloadService reload;

    @PostMapping("/reload")
    public String reload(RedirectAttributes redirect) {
        List<ReloadResult> results = reload.reloadNow();
        List<ReloadResult> failed = results.stream().filter(r -> !r.ok()).toList();
        if (results.isEmpty()) {
            redirect.addFlashAttribute("flashError", "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다.");
        } else if (failed.isEmpty()) {
            redirect.addFlashAttribute("flashSuccess", "FIDO 서버 " + results.size() + "대에 reload 를 보냈습니다.");
        } else {
            String detail = failed.stream().map(r -> r.servercode() + ": " + r.detail())
                .collect(java.util.stream.Collectors.joining(", "));
            redirect.addFlashAttribute("flashError",
                "FIDO 서버 " + results.size() + "대 중 " + failed.size() + "대 reload 실패 — " + detail);
        }
        return "redirect:/system/fido-clients";
    }
```
예약어 조건: `if (code.equalsIgnoreCase("new") || code.equalsIgnoreCase("reload") || code.matches("\\.+"))` — 주석에 "`reload` 는 수동 전송 경로(POST /reload)와 겹친다" 한 줄 추가.
(imports: `com.crosscert.fidoadmin.system.reload.FidoReloadService`, `ReloadResult`, `java.util.List`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.servlet.mvc.support.RedirectAttributes`)

`list.html` 제목 줄의 오른쪽을 버튼 묶음으로:
```html
    <div class="d-flex gap-2">
      <form method="post" th:action="@{/system/fido-clients/reload}" class="m-0"
            onsubmit="return confirm('STATUS=ON 인 모든 FIDO 서버에 reload 를 보냅니다.');">
        <button class="btn btn-outline-primary btn-sm"><i class="bi bi-arrow-repeat"></i> 지금 reload 보내기</button>
      </form>
      <a th:href="@{/system/fido-clients/new}" class="btn btn-primary btn-sm">등록</a>
    </div>
```
(Thymeleaf + Spring Security 가 `th:action` 폼에 CSRF 숨은 필드를 자동으로 넣는다.)

- [ ] **Step 4: 통과 확인** — Step 2 명령, Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/system src/main/resources/templates/system/fido-clients/list.html src/test/java/com/crosscert/fidoadmin/system
git commit -m "feat: FIDO 서버 목록에서 reload 를 바로 보내고 서버별 결과를 본다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: 설정 변경 지점에서 reload 이벤트 발행

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/common/CrudService.java` (훅 `afterChange`)
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/service/AppidService.java`, `AppserverService.java`, `CriteriaQueryService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/fido2/service/Fido2MetadataService.java`, `Fido2CredentialParamsService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/system/service/FidoSettingService.java`
- Test: 위 서비스들의 기존 테스트(`AppidServiceTest`, `AppserverServiceTest`, `CriteriaQueryServiceTest`, `Fido2MetadataServiceTest`, `Fido2CredentialParamsServiceTest`, `system/FidoSettingServiceTest`)

**Interfaces:**
- Consumes: `FidoConfigChanged(String reason)` (Task 5)
- Produces:
  - `CrudService.afterChange(String action, E entity)` — `protected`, 기본 no-op. `action` 은 `"CREATE"`/`"UPDATE"`/`"DELETE"`.
  - 새 생성자 인자 `ApplicationEventPublisher events` 를 **맨 끝**에 추가: `AppidService`, `AppserverService`, `Fido2MetadataService`, `Fido2CredentialParamsService`, `CriteriaQueryService`, `FidoSettingService`

- [ ] **Step 1: 실패하는 테스트** — 각 테스트의 `new XService(...)` 호출 끝에 `events` 를 넘기고 필드 `ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);` 를 추가한다. 그다음 아래를 추가한다(기존 파일의 저장 목 설정·테넌트 준비를 재사용).

`AppidServiceTest`:
```java
    @Test void createUpdateDeletePublishReload() {
        when(repo.save(any())).thenAnswer(inv -> { Appid a = inv.getArgument(0); if (a.getIdx() == null) a.setIdx(12L); return a; });
        Appid created = service.create(new Appid());
        verify(events).publishEvent(new FidoConfigChanged("APPID CREATE 12"));

        when(repo.findById(12L)).thenReturn(Optional.of(created));
        service.update(12L, a -> a.setMemo("m"));
        verify(events).publishEvent(new FidoConfigChanged("APPID UPDATE 12"));

        service.delete(12L);
        verify(events).publishEvent(new FidoConfigChanged("APPID DELETE 12"));
    }
```
`AppserverServiceTest`, `Fido2MetadataServiceTest`, `Fido2CredentialParamsServiceTest` 에도 같은 모양의 테스트를 둔다. 기대 reason 은 각각 `"APPSERVER CREATE {idx}"`, `"FIDO2_METADATA CREATE {idx}"`, `tableName()` 값 + `" CREATE {idx}"` 이다(`Fido2CredentialParamsService.tableName()` 의 실제 문자열을 확인해 쓴다). FIDO2 두 서비스는 SUPER 전용이므로 테스트에서 슈퍼 사용자로 로그인시킨다(`CompanyServiceTest.loginSuper()` 와 같은 코드).

`CriteriaQueryServiceTest` (생성자: `new CriteriaQueryService(repo, audit, new TenantContext(new SelectedTenant()), jdbc, events)`, 필드 `ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);`):
```java
    @Test void toggleThatChangesPublishesReload() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        assertThat(service.changeStatus(1L, false)).isTrue();
        verify(events).publishEvent(new FidoConfigChanged("AAID 상태 변경 0012#0001"));
    }

    @Test void toggleWithNoChangeDoesNotPublish() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        assertThat(service.changeStatus(1L, false)).isFalse();
        verify(events, never()).publishEvent(any());
    }

    @Test void bulkToggleWithChangesPublishes() {
        login(7L);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(4);
        assertThat(service.changeStatusAll(false)).isEqualTo(4);
        verify(events).publishEvent(new FidoConfigChanged("AAID 전체 비활성 4건"));
    }

    @Test void bulkToggleWithoutChangesDoesNotPublish() {
        login(7L);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        assertThat(service.changeStatusAll(true)).isZero();
        verify(events, never()).publishEvent(any());
    }
```

`FidoSettingServiceTest`:
```java
    @Test void saveWithChangesPublishesOnce() {
        // 기존 "값이 바뀌면 저장한다" 테스트와 같은 준비로 두 키를 바꾼다
        ...
        verify(events, times(1)).publishEvent(new FidoConfigChanged("FIDO 서버 설정 저장 2건"));
    }

    @Test void saveWithoutChangesDoesNotPublish() {
        // 기존 "같은 값이면 저장하지 않는다" 테스트와 같은 준비
        ...
        verify(events, never()).publishEvent(any());
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.fido.service.*' --tests 'com.crosscert.fidoadmin.fido2.service.*' --tests 'com.crosscert.fidoadmin.system.FidoSettingServiceTest'`
Expected: 컴파일 오류 — 생성자 인자 수 불일치

- [ ] **Step 3: 구현**

`CrudService` — 훅과 호출 3곳:
```java
    /**
     * 등록·수정·삭제가 끝난 뒤(같은 트랜잭션 안) 부른다. 기본은 아무것도 하지 않는다.
     * FIDO 서버가 읽는 데이터를 다루는 서비스가 reload 이벤트를 발행하는 자리다.
     */
    protected void afterChange(String action, E entity) {}
```
`create` 의 `log.info(...)` 다음 `afterChange("CREATE", saved);`, `update` 의 `log.info(...)` 다음 `afterChange("UPDATE", saved);`, `delete` 의 `log.info(...)` 다음 `afterChange("DELETE", e);`.

`AppidService` (다른 세 CrudService 하위 클래스도 같은 모양 — 각 파일에 그대로 적는다):
```java
    private final ApplicationEventPublisher events;

    public AppidService(AppidRepository repository, AuditLogger audit, TenantContext tenant,
                        ApplicationEventPublisher events) {
        super(repository, audit, tenant);
        this.events = events;
    }

    /** FIDO 서버가 AppID 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다(이전 어드민 sendAllSignal). */
    @Override protected void afterChange(String action, Appid e) {
        events.publishEvent(new FidoConfigChanged(tableName() + " " + action + " " + idOf(e)));
    }
```
`AppserverService`: 타입만 `Appserver`, 주석 "멤버코드". `Fido2MetadataService`: `Fido2Metadata`, 주석 "FIDO2 메타데이터". `Fido2CredentialParamsService`: `Fido2CredentialParams`, 주석 "크리덴셜 파라미터".
(imports: `org.springframework.context.ApplicationEventPublisher`, `com.crosscert.fidoadmin.system.reload.FidoConfigChanged`)

`CriteriaQueryService` — 생성자 끝에 `ApplicationEventPublisher events` 추가, `changeStatus` 의 `audit.log(...)` 다음:
```java
        events.publishEvent(new FidoConfigChanged("AAID 상태 변경 " + aaid));
```
`changeStatusAll` 의 `if (changed > 0) { audit.log(...); }` 블록 안에:
```java
            events.publishEvent(new FidoConfigChanged("AAID 전체 " + (enabled ? "활성" : "비활성") + " " + changed + "건"));
```

`FidoSettingService` — `@RequiredArgsConstructor` 이므로 필드 `private final ApplicationEventPublisher events;` 를 마지막 필드로 추가(생성자 인자 순서 = 필드 순서). `save` 에서 바뀐 행 수를 센다:
```java
        int changed = 0;
        for (...) {
            ...
            AuditChanges.record(audit, "CCFA_SYSTEM_PROP", saved.getId().toPathValue(), before, saved);
            changed++;
        }
        if (changed > 0) events.publishEvent(new FidoConfigChanged("FIDO 서버 설정 저장 " + changed + "건"));
```

`SignupApprovalEscalationIntegrationTest` 등 `new` 로 위 서비스를 만드는 다른 테스트가 있으면 같은 방식으로 `mock(ApplicationEventPublisher.class)` 를 넘긴다. 찾기: `grep -rn "new AppidService\|new AppserverService\|new CriteriaQueryService\|new FidoSettingService\|new Fido2MetadataService\|new Fido2CredentialParamsService" src/test`.

- [ ] **Step 4: 통과 확인** — Step 2 명령 후 `./gradlew test`, Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: AppID·멤버코드·AAID·FIDO2·서버 설정이 바뀌면 reload 이벤트를 낸다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 고객사 생성·삭제 시 AAID 차단·FDS 기본 정책

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryService.java` (`disableAllFor` 분리, `deleteAllFor` 추가)
- Modify: `src/main/java/com/crosscert/fidoadmin/company/service/CompanyService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/CrudController.java` (`createdMessage` 훅)
- Modify: `src/main/java/com/crosscert/fidoadmin/company/web/CompanyController.java`
- Test: `src/test/java/com/crosscert/fidoadmin/company/service/CompanyServiceTest.java`, `src/test/java/com/crosscert/fidoadmin/fido/service/CriteriaQueryServiceTest.java`, `src/test/java/com/crosscert/fidoadmin/company/web/CompanyControllerWebTest.java`

**Interfaces:**
- Consumes: `FidoConfigChanged` (Task 5), `CriteriaQueryService(..., ApplicationEventPublisher events)` (Task 7)
- Produces:
  - `CriteriaQueryService.disableAllFor(Long companyIdx): int` — 잠금·감사·이벤트 없이 insert 만
  - `CriteriaQueryService.deleteAllFor(Long companyIdx): int` — 그 고객사 차단 행 전부 삭제
  - `CrudController.createdMessage(E saved): String` — 기본 `"등록되었습니다."`
  - `CompanyService` 생성자 끝에 `CcfaFdsPolicyRepository fdsPolicies, CriteriaQueryService criteria, ApplicationEventPublisher events` 추가

- [ ] **Step 1: 실패하는 테스트**

`CompanyServiceTest` — 필드 추가, 생성자 호출 갱신:
```java
    CcfaFdsPolicyRepository fdsPolicies = mock(CcfaFdsPolicyRepository.class);
    CriteriaQueryService criteria = mock(CriteriaQueryService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CompanyService service = new CompanyService(companies, audit, appids, users, managers, props,
        new TenantContext(new SelectedTenant()), fdsPolicies, criteria, events);
```
테스트 추가:
```java
    @SuppressWarnings("unchecked")
    @Test void createDisablesAllAaidsAndAddsDefaultFdsPolicy() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.disableAllFor(7L)).thenReturn(3);
        when(fdsPolicies.existsById(7L)).thenReturn(false);

        service.create(new CcfaCompany());

        verify(criteria).disableAllFor(7L);
        ArgumentCaptor<CcfaFdsPolicy> saved = ArgumentCaptor.forClass(CcfaFdsPolicy.class);
        verify(fdsPolicies).save(saved.capture());
        assertThat(saved.getValue().getCompanyIdx()).isEqualTo(7L);
        assertThat(saved.getValue().getAndCountry()).isEqualTo("NO");
        assertThat(saved.getValue().getOrCountry()).isEqualTo("NO");
        assertThat(saved.getValue().getCreatedtime()).isNotNull();
        assertThat(saved.getValue().getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (3건)");
        verify(audit).log(AuditType.CREATE, "CCFA_FDS_POLICY 기본값 고객사 7");
        verify(events).publishEvent(new FidoConfigChanged("고객사 생성 7"));
    }

    @SuppressWarnings("unchecked")
    @Test void createKeepsExistingFdsPolicy() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(fdsPolicies.existsById(7L)).thenReturn(true);
        service.create(new CcfaCompany());
        verify(fdsPolicies, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test void createWithNoCriteriaSkipsAaidAudit() {
        when(companies.save(any())).thenAnswer(inv -> { CcfaCompany c = inv.getArgument(0); c.setIdx(7L); return c; });
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.disableAllFor(7L)).thenReturn(0);
        service.create(new CcfaCompany());
        verify(audit, never()).log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 7 (0건)");
    }

    @SuppressWarnings("unchecked")
    @Test void deleteRemovesAaidRowsAndFdsPolicy() {
        when(companies.findById(7L)).thenReturn(Optional.of(company(7L)));
        when(appids.countByCompanyIdx(7L)).thenReturn(0L);
        when(users.countByCompanyIdx(7L)).thenReturn(0L);
        when(managers.countByCompanyIdx(7L)).thenReturn(0L);
        when(props.findAll(any(Specification.class))).thenReturn(List.of());
        when(criteria.deleteAllFor(7L)).thenReturn(5);
        when(fdsPolicies.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(criteria).deleteAllFor(7L);
        verify(fdsPolicies).deleteById(7L);
        verify(audit).log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 7 (5건)");
        verify(audit).log(AuditType.DELETE, "CCFA_FDS_POLICY DELETE 고객사 7");
    }

    @Test void blockedDeleteLeavesAaidAndFdsAlone() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(appids.countByCompanyIdx(1L)).thenReturn(2L);
        when(users.countByCompanyIdx(1L)).thenReturn(0L);
        when(managers.countByCompanyIdx(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
        verify(criteria, never()).deleteAllFor(any());
        verify(fdsPolicies, never()).deleteById(any());
    }
```
(imports: `CcfaFdsPolicy`, `CcfaFdsPolicyRepository`, `CriteriaQueryService`, `ApplicationEventPublisher`, `FidoConfigChanged`, `AuditType`)

`CriteriaQueryServiceTest` 에 추가:
```java
    @Test void disableAllForInsertsForGivenCompanyWithoutAuditOrEvent() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(4);
        assertThat(service.disableAllFor(9L)).isEqualTo(4);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("INSERT INTO CCFA_COMPANY_AAID");
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(9L);
        verify(audit, never()).log(any(), anyString());
        verify(events, never()).publishEvent(any());
    }

    @Test void deleteAllForDeletesGivenCompanyRows() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(2);
        assertThat(service.deleteAllFor(9L)).isEqualTo(2);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("DELETE FROM CCFA_COMPANY_AAID");
    }
```

`CompanyControllerWebTest` 에 추가(기존 등록 테스트의 준비를 따른다):
```java
    @Test void createMessageWarnsAllAaidsDisabled() throws Exception {
        // 기존 "등록 후 상세로 이동" 테스트의 when(service.create(...)) 준비 재사용
        ...
        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                /* 기존 테스트의 필수 파라미터 */)
            .andExpect(flash().attribute("flashSuccess",
                "등록되었습니다. 모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요."));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.*' --tests 'com.crosscert.fidoadmin.fido.service.CriteriaQueryServiceTest'`
Expected: 컴파일 오류 — `disableAllFor`, 생성자 인자 수

- [ ] **Step 3: 구현**

`CriteriaQueryService` — `changeStatusAll` 의 비활성 분기를 새 메서드 호출로 바꾸고 두 메서드 추가:
```java
        } else {
            changed = disableAllFor(companyIdx);
        }
```
```java
    /**
     * 주어진 고객사에서 모든 AAID 를 차단한다. 잠금·감사 로그·이벤트는 호출자 몫이다.
     * 고객사 생성({@code CompanyService.create})과 전체 비활성({@link #changeStatusAll})이 같이 쓴다.
     */
    @Transactional
    public int disableAllFor(Long companyIdx) {
        return jdbc.update("""
            INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
            SELECT :companyIdx, c.AAID
              FROM CRITERIA c
             WHERE c.AAID IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM CCFA_COMPANY_AAID b
                    WHERE b.COMPANY_IDX = :companyIdx AND b.AAID = c.AAID
               )
            """, new MapSqlParameterSource("companyIdx", companyIdx));
    }

    /** 주어진 고객사의 차단 행을 모두 지운다. 고객사 삭제 때 쓴다. */
    @Transactional
    public int deleteAllFor(Long companyIdx) {
        return jdbc.update("""
            DELETE FROM CCFA_COMPANY_AAID
             WHERE COMPANY_IDX = :companyIdx
            """, new MapSqlParameterSource("companyIdx", companyIdx));
    }
```

`CompanyService` — 필드·생성자·`create`·`delete`:
```java
    private final CcfaFdsPolicyRepository fdsPolicies;
    private final CriteriaQueryService criteria;
    private final ApplicationEventPublisher events;

    public CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids,
                          UserinfoRepository users, CcfaManagerRepository managers,
                          CcfaSystemPropRepository props, TenantContext tenant,
                          CcfaFdsPolicyRepository fdsPolicies, CriteriaQueryService criteria,
                          ApplicationEventPublisher events) {
        super(repository, audit, tenant);
        this.appids = appids;
        this.users = users;
        this.managers = managers;
        this.props = props;
        this.fdsPolicies = fdsPolicies;
        this.criteria = criteria;
        this.events = events;
    }
```
`create` 의 `return saved;` 앞에:
```java
        // 이전 어드민 disableCompanyAllAAID: 새 고객사는 인증기를 하나씩 허용하기 전까지 FIDO 등록이 되지 않는다.
        int disabled = criteria.disableAllFor(saved.getIdx());
        if (disabled > 0) {
            audit.log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 " + saved.getIdx() + " (" + disabled + "건)");
        }
        // 이전 어드민 fds.insert: 국가 조건 'NO' 인 빈 정책을 둔다.
        if (!fdsPolicies.existsById(saved.getIdx())) {
            LocalDateTime now = LocalDateTime.now();
            CcfaFdsPolicy policy = new CcfaFdsPolicy();
            policy.setCompanyIdx(saved.getIdx());
            policy.setAndCountry("NO");
            policy.setOrCountry("NO");
            policy.setCreatedtime(now);
            policy.setUpdatedtime(now);
            fdsPolicies.save(policy);
            audit.log(AuditType.CREATE, "CCFA_FDS_POLICY 기본값 고객사 " + saved.getIdx());
        }
        events.publishEvent(new FidoConfigChanged("고객사 생성 " + saved.getIdx()));
```
`delete` 의 기존 설정 삭제 블록 다음에:
```java
        int aaidRows = criteria.deleteAllFor(id);
        if (aaidRows > 0) {
            audit.log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 " + id + " (" + aaidRows + "건)");
        }
        if (fdsPolicies.existsById(id)) {
            fdsPolicies.deleteById(id);
            audit.log(AuditType.DELETE, "CCFA_FDS_POLICY DELETE 고객사 " + id);
        }
```
(imports: `CcfaFdsPolicy`, `CcfaFdsPolicyRepository`, `CriteriaQueryService`, `ApplicationEventPublisher`, `FidoConfigChanged`)

`CrudController`:
```java
    /** 등록 성공 메시지. 등록이 딸린 효과를 알려야 하는 화면이 바꾼다. */
    protected String createdMessage(E saved) { return "등록되었습니다."; }
```
`create` 의 `redirect.addFlashAttribute("flashSuccess", "등록되었습니다.");` 를 `redirect.addFlashAttribute("flashSuccess", createdMessage(saved));` 로.

`CompanyController`:
```java
    @Override protected String createdMessage(CcfaCompany saved) {
        return "등록되었습니다. 모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요.";
    }
```

- [ ] **Step 4: 통과 확인** — Step 2 명령 후 `./gradlew test`, Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: 새 고객사는 AAID 전체 차단·FDS 기본 정책으로 시작하고 삭제 때 함께 지운다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: 비밀번호 만료 — 저장소와 정책(열 탐지)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/auth/PasswordAgeStore.java`
- Create: `src/main/java/com/crosscert/fidoadmin/auth/PasswordExpiryPolicy.java`
- Modify: `src/main/resources/application.yml:41-` (`fido-admin.password-expiry.enabled: auto`)
- Modify: `docker/init/01-schema.sql:400-418` (`LAST_PW_CHANGE_DATE TIMESTAMP`)
- Modify: `docs/erd/kbfido-columns.txt` (`## CCFA_MANAGER` 절 끝에 주석 한 줄)
- Test: `src/test/java/com/crosscert/fidoadmin/auth/PasswordExpiryPolicyTest.java`

**Interfaces:**
- Produces:
  - `PasswordAgeStore(JdbcTemplate jdbc)`: `boolean columnExists()`, `Optional<LocalDateTime> lastChanged(String userId)`, `void touch(String userId)`
  - `PasswordExpiryPolicy(PasswordAgeStore store, CcfaSystemPropRepository props, @Value("${fido-admin.password-expiry.enabled:auto}") String mode, Clock clock)`
    - `boolean enabled()`, `int expiryDays()`, `boolean isExpiredOnLogin(String userId)`, `void touch(String userId)`
  - 상수 `PasswordExpiryPolicy.PROP_EXPIRY_DAYS = "PW_EXPIRY_DAYS"`, `DEFAULT_DAYS = 90`
  - `Clock` 빈이 없으면 이 클래스의 `@Autowired` 생성자가 `Clock.systemDefaultZone()` 를 쓴다(아래 구현 참고)

- [ ] **Step 1: 실패하는 테스트**

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordExpiryPolicyTest {

    static final ZoneId Z = ZoneId.of("Asia/Seoul");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    Clock clock = Clock.fixed(NOW.atZone(Z).toInstant(), Z);
    PasswordAgeStore store = mock(PasswordAgeStore.class);
    CcfaSystemPropRepository props = mock(CcfaSystemPropRepository.class);

    private PasswordExpiryPolicy policy(String mode) { return new PasswordExpiryPolicy(store, props, mode, clock); }

    @Test void autoEnablesWhenColumnExists() {
        when(store.columnExists()).thenReturn(true);
        assertThat(policy("auto").enabled()).isTrue();
    }

    @Test void autoDisablesWhenColumnMissing() {
        when(store.columnExists()).thenReturn(false);
        PasswordExpiryPolicy p = policy("auto");
        assertThat(p.enabled()).isFalse();
        assertThat(p.isExpiredOnLogin("u")).isFalse();
        p.touch("u");
        verify(store, never()).lastChanged(any());
        verify(store, never()).touch(any());
    }

    @Test void falseDisablesWithoutProbing() {
        PasswordExpiryPolicy p = policy("false");
        assertThat(p.enabled()).isFalse();
        verify(store, never()).columnExists();
    }

    @Test void expiryBoundaryAt90Days() {
        when(store.columnExists()).thenReturn(true);
        PasswordExpiryPolicy p = policy("auto");
        when(store.lastChanged("u89")).thenReturn(Optional.of(NOW.minusDays(89)));
        when(store.lastChanged("u90")).thenReturn(Optional.of(NOW.minusDays(90)));
        when(store.lastChanged("u91")).thenReturn(Optional.of(NOW.minusDays(91)));
        assertThat(p.isExpiredOnLogin("u89")).isFalse();
        assertThat(p.isExpiredOnLogin("u90")).isTrue();
        assertThat(p.isExpiredOnLogin("u91")).isTrue();
    }

    @Test void nullDateIsNotExpiredAndGetsTouched() {
        when(store.columnExists()).thenReturn(true);
        when(store.lastChanged("legacy")).thenReturn(Optional.empty());
        assertThat(policy("auto").isExpiredOnLogin("legacy")).isFalse();
        verify(store).touch("legacy");
    }

    @Test void expiryDaysFromSystemProp() {
        when(store.columnExists()).thenReturn(true);
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L));
        p.setPropValue(" 30 ");
        when(props.findById(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p));
        PasswordExpiryPolicy policy = policy("auto");
        assertThat(policy.expiryDays()).isEqualTo(30);
        when(store.lastChanged("u")).thenReturn(Optional.of(NOW.minusDays(30)));
        assertThat(policy.isExpiredOnLogin("u")).isTrue();
    }

    @Test void invalidOrNonPositiveExpiryDaysFallsBackTo90() {
        when(store.columnExists()).thenReturn(true);
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L));
        p.setPropValue("0");
        when(props.findById(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p));
        assertThat(policy("auto").expiryDays()).isEqualTo(90);
        p.setPropValue("abc");
        assertThat(policy("auto").expiryDays()).isEqualTo(90);
    }

    @Test void touchDelegatesWhenEnabled() {
        when(store.columnExists()).thenReturn(true);
        policy("auto").touch("u");
        verify(store).touch("u");
    }
}
```

`PasswordAgeStore` 의 탐지는 JDBC 목으로 확인한다 — 같은 파일에 추가:
```java
    @Test void storeReportsMissingColumnOnBadSqlGrammar() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("probe", "SELECT", new java.sql.SQLException("ORA-00904")));
        assertThat(new PasswordAgeStore(jdbc).columnExists()).isFalse();
    }

    @Test void storeReportsColumnWhenProbeSucceeds() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of());
        assertThat(new PasswordAgeStore(jdbc).columnExists()).isTrue();
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.PasswordExpiryPolicyTest'`
Expected: 컴파일 오류 `cannot find symbol: PasswordExpiryPolicy`

- [ ] **Step 3: 구현**

`PasswordAgeStore.java`:
```java
package com.crosscert.fidoadmin.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * CCFA_MANAGER.LAST_PW_CHANGE_DATE 를 다루는 유일한 곳.
 *
 * <p>이 열은 이전 어드민이 2025년에 더한 것으로 보이며 ERD 전사본에 없다. JPA 엔티티에 매핑하면 열이 없는 DB 에서
 * CCFA_MANAGER 조회가 모두 ORA-00904 로 실패해 로그인부터 막히므로, JDBC 로만 읽고 쓴다.
 */
@Component
@RequiredArgsConstructor
public class PasswordAgeStore {

    private final JdbcTemplate jdbc;

    /** 열이 있는가. 시노님으로 다른 스키마의 테이블을 보는 경우에도 맞도록 딕셔너리 대신 직접 조회해 본다. */
    public boolean columnExists() {
        try {
            jdbc.queryForList("SELECT LAST_PW_CHANGE_DATE FROM CCFA_MANAGER WHERE 1=0");
            return true;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }

    public Optional<LocalDateTime> lastChanged(String userId) {
        List<Timestamp> rows = jdbc.queryForList(
            "SELECT LAST_PW_CHANGE_DATE FROM CCFA_MANAGER WHERE USER_ID = ?", Timestamp.class, userId);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0).toLocalDateTime());
    }

    public void touch(String userId) {
        jdbc.update("UPDATE CCFA_MANAGER SET LAST_PW_CHANGE_DATE = SYSTIMESTAMP WHERE USER_ID = ?", userId);
    }
}
```

`PasswordExpiryPolicy.java`:
```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 만료. 이전 어드민은 LAST_PW_CHANGE_DATE 로부터 90일이 지나면 로그인 뒤 변경 화면으로 보냈다.
 *
 * <p>열이 없는 DB 에서는 스스로 꺼진다({@code fido-admin.password-expiry.enabled=auto}, 기본). 끄면 이 열에
 * 전혀 접근하지 않는다. {@code false} 로 두면 기동 시 탐지 쿼리도 돌리지 않는다.
 */
@Slf4j
@Component
public class PasswordExpiryPolicy {

    static final String PROP_EXPIRY_DAYS = "PW_EXPIRY_DAYS";
    static final int DEFAULT_DAYS = 90;

    private final PasswordAgeStore store;
    private final CcfaSystemPropRepository props;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public PasswordExpiryPolicy(PasswordAgeStore store, CcfaSystemPropRepository props,
                                @Value("${fido-admin.password-expiry.enabled:auto}") String mode) {
        this(store, props, mode, Clock.systemDefaultZone());
    }

    PasswordExpiryPolicy(PasswordAgeStore store, CcfaSystemPropRepository props, String mode, Clock clock) {
        this.store = store;
        this.props = props;
        this.clock = clock;
        if ("false".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            this.enabled = false;
            log.info("비밀번호 만료를 설정으로 껐습니다(fido-admin.password-expiry.enabled=false).");
        } else {
            this.enabled = store.columnExists();
            if (!enabled) log.warn("LAST_PW_CHANGE_DATE 열이 없어 비밀번호 만료를 끕니다.");
        }
    }

    public boolean enabled() { return enabled; }

    public int expiryDays() {
        return props.findById(new CcfaSystemPropId(PROP_EXPIRY_DAYS, 0L))
            .map(p -> {
                try {
                    int v = Integer.parseInt(p.getPropValue().trim());
                    return v > 0 ? v : DEFAULT_DAYS;
                } catch (RuntimeException e) {
                    return DEFAULT_DAYS;
                }
            })
            .orElse(DEFAULT_DAYS);
    }

    /**
     * 로그인 직후 판정. 날짜가 없으면(열 추가 전 계정) 만료가 아니고 지금으로 기록한다 —
     * 배포 직후 모두를 한꺼번에 변경 화면으로 보내지 않고, 이 로그인부터 기간을 센다.
     */
    public boolean isExpiredOnLogin(String userId) {
        if (!enabled) return false;
        Optional<LocalDateTime> last = store.lastChanged(userId);
        if (last.isEmpty()) {
            store.touch(userId);
            return false;
        }
        return !last.get().plusDays(expiryDays()).isAfter(LocalDateTime.now(clock));
    }

    /** 비밀번호가 바뀐 시각을 지금으로. 꺼져 있으면 아무것도 하지 않는다. */
    public void touch(String userId) {
        if (enabled) store.touch(userId);
    }
}
```

`application.yml` 의 `fido-admin:` 아래:
```yaml
  # 비밀번호 만료. auto: 기동 시 CCFA_MANAGER.LAST_PW_CHANGE_DATE 열이 있으면 켠다. false: 끈다.
  password-expiry:
    enabled: auto
```

`docker/init/01-schema.sql` `CCFA_MANAGER` 의 `UPDATEDTIME` 줄 다음에:
```sql
  -- 운영 DB 에 있다고 가정한 열(이전 어드민 2025 추가). 엔티티에 매핑하지 않고 PasswordAgeStore 가 JDBC 로만 쓴다.
  LAST_PW_CHANGE_DATE          TIMESTAMP,
```

`docs/erd/kbfido-columns.txt` `## CCFA_MANAGER` 표 바로 다음 줄에:
```
   ※ LAST_PW_CHANGE_DATE TIMESTAMP — 운영 DB 에 있다고 가정한 선택 열. 전사본 밖이며 엔티티에 매핑하지 않는다(PasswordAgeStore, JDBC 전용). 없으면 비밀번호 만료가 스스로 꺼진다.
```
주의: `src/test/resources/erd-columns.txt` 는 **고치지 않는다**(`ErdConformanceTest` 는 이 파일만 읽는다).

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.PasswordExpiryPolicyTest' --tests 'com.crosscert.fidoadmin.**.ErdConformanceTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/crosscert/fidoadmin/auth/PasswordAgeStore.java src/main/java/com/crosscert/fidoadmin/auth/PasswordExpiryPolicy.java src/main/resources/application.yml docker/init/01-schema.sql docs/erd/kbfido-columns.txt src/test/java/com/crosscert/fidoadmin/auth/PasswordExpiryPolicyTest.java
git commit -m "feat: 비밀번호 변경일 열을 JDBC 로만 다루고 열이 없으면 만료를 스스로 끈다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: 비밀번호 만료 — 기록 지점·로그인·강제 변경

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/auth/PasswordExpiredInterceptor.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/auth/LoginSuccessHandler.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/auth/PasswordChangeService.java`, `PasswordChangeController.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/manager/service/ManagerService.java`, `SuperManagerService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/signup/SignupService.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/WebMvcConfig.java`
- Modify: `src/main/resources/templates/auth/password.html`
- Test: `src/test/java/com/crosscert/fidoadmin/auth/PasswordExpiredInterceptorTest.java`, `LoginSuccessHandlerExpiryTest.java`(신규), 기존 `PasswordChangeServiceTest`, `PasswordChangeControllerWebTest`, `manager/service/ManagerServiceTest`, `SuperManagerServiceTest`, `signup/SignupServiceTest`, `signup/SignupApprovalServiceTest`, `integration/SignupApprovalEscalationIntegrationTest`

**Interfaces:**
- Consumes: `PasswordExpiryPolicy.isExpiredOnLogin(String)`, `touch(String)`, `expiryDays()` (Task 9)
- Produces:
  - `PasswordExpiredInterceptor.SESSION_ATTR = "PASSWORD_EXPIRED"` (세션 값 `Integer` = 기간 일수)
  - 생성자 끝에 `PasswordExpiryPolicy expiry` 추가: `LoginSuccessHandler`, `PasswordChangeService`, `ManagerService`, `SuperManagerService`, `SignupService`

- [ ] **Step 1: 실패하는 테스트**

`PasswordExpiredInterceptorTest`:
```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PasswordExpiredInterceptorTest {

    PasswordExpiredInterceptor interceptor = new PasswordExpiredInterceptor();

    private MockHttpServletRequest req(String method, String uri, boolean expired) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        if (expired) r.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        return r;
    }

    @Test void passesWhenNotExpired() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req("GET", "/", false), res, new Object())).isTrue();
    }

    @Test void passesWithoutSession() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/");
        assertThat(interceptor.preHandle(r, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/appids", "/select-tenant", "/system/fido-clients/reload"})
    void redirectsOtherPathsWhenExpired(String uri) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req("GET", uri, true), res, new Object())).isFalse();
        assertThat(res.getRedirectedUrl()).isEqualTo("/me/password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/me/password", "/logout", "/webjars/bootstrap/css/bootstrap.min.css", "/css/app.css",
        "/js/app.js", "/fonts/x.woff2", "/favicon.ico", "/error", "/error/500"})
    void allowsPasswordPageStaticAndErrorWhenExpired(String uri) throws Exception {
        assertThat(interceptor.preHandle(req("GET", uri, true), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test void allowsPasswordPost() throws Exception {
        assertThat(interceptor.preHandle(req("POST", "/me/password", true), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test void respectsContextPath() throws Exception {
        MockHttpServletRequest r = req("GET", "/admin/appids", true);
        r.setContextPath("/admin");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(r, res, new Object())).isFalse();
        assertThat(res.getRedirectedUrl()).isEqualTo("/admin/me/password");
    }
}
```

`LoginSuccessHandlerExpiryTest`:
```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class LoginSuccessHandlerExpiryTest {

    LoginAttemptService attempts = mock(LoginAttemptService.class);
    PasswordExpiryPolicy expiry = mock(PasswordExpiryPolicy.class);
    LoginSuccessHandler handler = new LoginSuccessHandler(attempts, mock(AuditLogger.class), expiry);
    ManagerUserDetails user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void expiredGoesToPasswordPageAndMarksSession() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin")).thenReturn(true);
        when(expiry.expiryDays()).thenReturn(90);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/me/password");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isEqualTo(90);
    }

    @Test void notExpiredGoesHomeWithoutMark() throws Exception {
        when(expiry.isExpiredOnLogin("kbadmin")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(req, res, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThat(res.getRedirectedUrl()).isEqualTo("/");
        assertThat(req.getSession().getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }
}
```

`PasswordChangeServiceTest` — 생성자에 `expiry` 추가(`PasswordExpiryPolicy expiry = mock(PasswordExpiryPolicy.class);`), 성공 테스트 끝에 `verify(expiry).touch("kbadmin");`, 실패 테스트 끝에 `verify(expiry, never()).touch(any());`.

`PasswordChangeControllerWebTest` 에 추가:
```java
    @Test void successClearsExpiredMarkAndShowsNoticeBefore() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(get("/me/password").session(session).with(user(/* 파일의 기존 사용자 */)))
            .andExpect(content().string(containsString("비밀번호를 변경한 지 90일이 지났습니다. 새 비밀번호로 변경하세요.")));
        mvc.perform(change("NewPass5678!", "NewPass5678!").session(session))
            .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }
```
(`change(...)` 헬퍼가 `MockHttpServletRequestBuilder` 를 돌려주면 `.session(session)` 을 붙일 수 있다. 사용자·CSRF 는 헬퍼가 이미 붙인다고 가정 — 아니면 파일의 방식대로 붙인다.)

`ManagerServiceTest`·`SuperManagerServiceTest` — 생성자에 `expiry` 추가 후:
```java
    @Test void createTouchesPasswordAge() {
        // 파일의 기존 create 성공 테스트 준비 재사용(userIdGuard.insert 가 저장 엔티티를 돌려줌)
        ...
        service.create(managerWithUserId("newbie"));
        verify(em).flush();
        verify(expiry).touch("newbie");
    }

    @Test void updateTouchesOnlyWhenPasswordChanged() {
        // 기존 update 테스트 준비 재사용. 비밀번호를 바꾸는 mutator 와 이름만 바꾸는 mutator 두 번
        ...
        service.update(5L, m -> m.setUserNm("새이름"));
        verify(expiry, never()).touch(any());
        service.update(5L, m -> m.setUserPw("new-hash"));
        verify(expiry).touch(/* 그 행의 userId */);
    }
```

`SignupServiceTest`/`SignupApprovalServiceTest` — 생성자에 `expiry` 추가, 승인 성공 테스트 끝에 `verify(expiry).touch(/* 승인된 userId */);`.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.*' --tests 'com.crosscert.fidoadmin.manager.*' --tests 'com.crosscert.fidoadmin.signup.*'`
Expected: 컴파일 오류 — `PasswordExpiredInterceptor` 없음, 생성자 인자 수

- [ ] **Step 3: 구현**

`PasswordExpiredInterceptor.java`:
```java
package com.crosscert.fidoadmin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 비밀번호가 만료된 세션은 변경 화면 밖으로 나가지 못한다. 표시는 {@link LoginSuccessHandler} 가 두고
 * 변경 성공 시 {@code PasswordChangeController} 가 지운다. 상태가 없어 WebMvcConfig 에서 new 로 만든다.
 * 로그아웃(POST /logout)은 Spring Security 필터가 먼저 처리하므로 여기까지 오지 않는다.
 */
public class PasswordExpiredInterceptor implements HandlerInterceptor {

    public static final String SESSION_ATTR = "PASSWORD_EXPIRED";

    private static final List<String> ALLOWED_PREFIXES = List.of(
        "/me/password", "/logout", "/error", "/webjars/", "/css/", "/js/", "/fonts/", "/favicon");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_ATTR) == null) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String allowed : ALLOWED_PREFIXES) {
            if (path.startsWith(allowed)) return true;
        }
        response.sendRedirect(request.getContextPath() + "/me/password");
        return false;
    }
}
```

`WebMvcConfig.addInterceptors` 맨 앞에(테넌트 선택보다 먼저 돈다):
```java
        // 비밀번호 만료 세션은 변경 화면에 가둔다. 상태가 없어 빈으로 두지 않는다
        // (@WebMvcTest 가 이 설정을 @Import 하므로 생성자 의존성을 늘리지 않는다).
        registry.addInterceptor(new PasswordExpiredInterceptor());
```

`LoginSuccessHandler` — 필드 `private final PasswordExpiryPolicy expiry;` 추가, `setDefaultTargetUrl("/");` 앞을 이렇게:
```java
        if (expiry.isExpiredOnLogin(user.getUserId())) {
            request.getSession().setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, expiry.expiryDays());
            log.info("비밀번호 만료: userId={} → 변경 화면", user.getUserId());
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, "/me/password");
            return;
        }
```

`PasswordChangeService` — 필드 `private final PasswordExpiryPolicy expiry;` 추가, `managers.save(m);` 다음 `expiry.touch(userId);`.

`PasswordChangeController`:
- `form(Model model, HttpSession session)` 에 `model.addAttribute("expiredDays", session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR));`
- `change(..., HttpSession session)` 의 두 `return "auth/password";` 앞에도 같은 `model` 속성이 필요하다 — 메서드 인자에 `Model model` 을 추가하고 동일하게 넣는다.
- 성공 후 `session.removeAttribute(PasswordExpiredInterceptor.SESSION_ATTR);`

`auth/password.html` 의 `<h1>` 다음에:
```html
  <div class="alert alert-warning" th:if="${expiredDays != null}"
       th:text="|비밀번호를 변경한 지 ${expiredDays}일이 지났습니다. 새 비밀번호로 변경하세요.|"></div>
```

`ManagerService`·`SuperManagerService` — 생성자 끝에 `PasswordExpiryPolicy expiry` 추가·필드 저장, 두 메서드 추가/수정:
```java
    /** 새 운영자의 비밀번호 변경일을 기록한다. JDBC 갱신이 행을 보도록 먼저 flush 한다. */
    @Override
    @Transactional
    public CcfaManager create(CcfaManager entity) {
        CcfaManager saved = super.create(entity);
        em.flush();
        expiry.touch(saved.getUserId());
        return saved;
    }
```
기존 `update` 오버라이드의 람다를 비밀번호 비교로 감싼다(두 서비스 모두):
```java
    public CcfaManager update(Long id, java.util.function.Consumer<CcfaManager> mutator) {
        String[] pwBefore = new String[1];
        CcfaManager saved = super.update(id, e -> {
            pwBefore[0] = e.getUserPw();
            /* 기존 람다 본문 그대로 */
        });
        if (!java.util.Objects.equals(pwBefore[0], saved.getUserPw())) {
            em.flush();
            expiry.touch(saved.getUserId());
        }
        return saved;
    }
```

`SignupService` — 생성자 끝에 `PasswordExpiryPolicy expiry`, `approve` 의 `audit.log(...)` 다음 `expiry.touch(saved.getUserId());` (행은 이미 있으므로 flush 불필요 — 단 `managers.save(m)` 가 병합만 하고 flush 전이어도 `LAST_PW_CHANGE_DATE` 는 다른 열이라 충돌하지 않는다).

`new ManagerService(...)`/`new SignupService(...)` 를 쓰는 통합 테스트(`SignupApprovalEscalationIntegrationTest`)는 스프링 빈 `PasswordExpiryPolicy` 를 `@Autowired` 해서 넘긴다.

- [ ] **Step 4: 통과 확인** — Step 2 명령 후 `./gradlew test`, Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/main/resources/templates/auth/password.html src/test/java
git commit -m "feat: 비밀번호가 만료되면 로그인 뒤 변경 화면에 가두고 바꾼 날을 기록한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Oracle 통합 테스트

**Files:**
- Create: `src/test/java/com/crosscert/fidoadmin/integration/LegacyParityIntegrationTest.java`

**Interfaces:**
- Consumes: `CompanyService.create`, `PasswordExpiryPolicy`, `PasswordAgeStore`, `ExternalLicenseController` 경로, `OracleContainerSupport`

- [ ] **Step 1: 테스트 작성**

```java
package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.PasswordAgeStore;
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Oracle 에서 insert·열 탐지·무인증 경로를 한 번씩 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyParityIntegrationTest extends OracleContainerSupport {

    @Autowired CompanyService companies;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordExpiryPolicy expiry;
    @Autowired PasswordAgeStore ages;
    @Autowired MockMvc mvc;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void newCompanyStartsWithAllAaidsBlockedAndDefaultFdsPolicy() {
        loginSuper();
        CcfaCompany c = new CcfaCompany();
        c.setCompanyName("IT 신규 고객사");
        c.setVendorCode("IT001");
        Long idx = companies.create(c).getIdx();

        Integer criteria = jdbc.queryForObject("SELECT COUNT(DISTINCT AAID) FROM CRITERIA WHERE AAID IS NOT NULL", Integer.class);
        Integer blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx);
        assertThat(blocked).isEqualTo(criteria);
        assertThat(jdbc.queryForObject("SELECT AND_COUNTRY || '/' || OR_COUNTRY FROM CCFA_FDS_POLICY WHERE COMPANY_IDX = ?",
            String.class, idx)).isEqualTo("NO/NO");

        companies.delete(idx);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_FDS_POLICY WHERE COMPANY_IDX = ?", Integer.class, idx)).isZero();
    }

    @Test void passwordExpiryIsEnabledOnLocalSchemaAndTouchWrites() {
        assertThat(expiry.enabled()).isTrue();
        String userId = jdbc.queryForObject("SELECT USER_ID FROM CCFA_MANAGER WHERE ROWNUM = 1", String.class);
        jdbc.update("UPDATE CCFA_MANAGER SET LAST_PW_CHANGE_DATE = NULL WHERE USER_ID = ?", userId);
        assertThat(ages.lastChanged(userId)).isEmpty();
        assertThat(expiry.isExpiredOnLogin(userId)).isFalse();
        assertThat(ages.lastChanged(userId)).isPresent();
    }

    @Test void externalLicenseIsReachableWithoutLogin() throws Exception {
        mvc.perform(get("/external/license/it-no-such-hash")).andExpect(status().isOk());
    }
}
```
`CcfaCompany` 에 필수값이 더 있으면(예: `companyType`) 시드(`docker/init/02-seed.sql`)의 고객사 행 값을 따라 채운다.

- [ ] **Step 2: 실행**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.integration.LegacyParityIntegrationTest'`
Expected: Docker 가 있으면 PASS, 없으면 SKIPPED. 메모리 부족(exit 77)이면 다른 컨테이너를 내리고 다시 돌린다.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/crosscert/fidoadmin/integration/LegacyParityIntegrationTest.java
git commit -m "test: 고객사 딸린 데이터·비밀번호 변경일·외부 라이선스를 실제 Oracle 에서 확인한다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: 이전 어드민 기능 기록과 작업 기록

**Files:**
- Create: `docs/legacy/admin-java-features.md`
- Modify: `tasks/todo.md`

- [ ] **Step 1: 기록 문서 작성**

원천: 영상 전사 노트 `/private/tmp/claude-501/-Users-jaehyunjung-Git-10-work-crosscert-fido-admin/f9a9b61a-1f76-4650-af5e-29fa8227b3e9/scratchpad/notes/part1.md` ~ `part6.md`. (세션이 끝나 사라졌으면 이 계획의 spec §1·§2.1·§3.5·§7 과 커밋 메시지를 원천으로 쓴다.)

구성:
```markdown
# 이전 어드민 Java 소스 기능 기록

원천: 이전 어드민(Eclipse `fidoadmin`) 소스 화면 녹화(IMG_9165.MOV, 11분)를 3초 간격으로 전사한 기록.
원본 파일이 아니라 영상 판독이라 오탈자가 있을 수 있다. SQL 쪽은 `mybatis-mappers.md` 를 본다.

## 패키지 구조
## 컨트롤러와 URL        (표: 클래스 | URL | 동작 | 새 어드민 대응)
## 서비스·DAO
## 스케줄러              (actionPerMin, BasicScheduler, FIDOLogScheduler(_old), ManagerPwPolicyScheduler)
## 검증기                (Company, Manager, FDSPolicy, Fido, Statistics, Basic)
## 설정 키               (SystemProp PROP_KEY 목록, AdminConfig 키 — 이름만)
## 로그 파이프라인        (FIDO_LOGS_yyyyMMdd JSONDATA base64url, .cfl = zip+JSON, 통계 집계)
## 옮기지 않은 것과 이유   (spec §7 요약)
## 하드코딩된 민감값      (종류만: 암호화 키·IV, 슈퍼 계정 비밀번호, 내부 IP 자동 로그인, 메일 수신자·CC, 전화번호, DB 접속 문자열)
```
"새 어드민 대응" 열에는 현재 URL(예: `/companies`, `/fido-clients`) 또는 "없음(이유)" 를 적는다.

**절대 옮기지 않는 값**: 암호화 키·IV 문자열, 계정 비밀번호, 자동 로그인 IP, 개인 메일 주소, 전화번호, DB 접속 문자열(ENC(...) 포함). 노트에 있어도 문서에는 "있었다"만 적는다. 작성 후 `grep -nE "asdasd|172\.16\.|@crosscert\.com|MNL19|V1e7|ENC\(" docs/legacy/admin-java-features.md` 가 아무것도 찾지 않아야 한다.

- [ ] **Step 2: `tasks/todo.md` 갱신** — 파일의 기존 형식(날짜 제목 + `[x]` 목록)을 따라 이번 작업 절을 추가한다: Task 1~11 항목을 `[x]` 로, 그리고 남은 확인 사항 두 줄을 `[ ]` 로.
```markdown
- [ ] 운영·QA DB 에서 `SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE TABLE_NAME='CCFA_MANAGER' AND COLUMN_NAME='LAST_PW_CHANGE_DATE'` 확인 — 없으면 기동 로그에 "LAST_PW_CHANGE_DATE 열이 없어 비밀번호 만료를 끕니다." 가 찍히는지 본다.
- [ ] FIDO 서버의 `/api/command/reload` 가 GET 을 받는지 운영 서버 한 대로 확인(수동 reload 버튼).
```

- [ ] **Step 3: 전체 테스트**

Run: `./gradlew test`
Expected: 로컬 Oracle 4건(`EntityBootTest`, `AuditLogRoundTripTest`, `LoginLockLoadTest`, `SelectedTenantWiringTest`) 외 실패 0

- [ ] **Step 4: Commit**

```bash
git add docs/legacy/admin-java-features.md tasks/todo.md
git commit -m "docs: 이전 어드민 Java 소스 기능 기록과 이번 보강 작업 기록

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
