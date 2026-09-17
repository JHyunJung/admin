# 운영자 가입 신청·승인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 스스로 가입을 신청하고 슈퍼 관리자가 승인해야 로그인할 수 있는 흐름을 추가한다.

**Architecture:** 신청은 `CCFA_MANAGER` 에 `STATUS = "승인대기"`, `COMPANY_IDX = -1` 로 저장한다. 승인 시 슈퍼 관리자가 실제 고객사를 지정하고 `STATUS = "활성"` 으로 바꾼다. 로그인 차단은 기존 `ManagerUserDetailsService` 의 상태 검사가 그대로 처리한다. 가입은 인증 없이 일어나므로 테넌트 컨텍스트에 의존하는 `CrudService` 를 쓰지 않고 별도 서비스로 분리한다.

**Tech Stack:** Spring Boot 3.5.3, Spring Security 6, Spring Data JPA, Thymeleaf, JUnit5 + MockMvc(@WebMvcTest), Testcontainers(oracle-free)

**Spec:** `docs/superpowers/specs/2026-09-17-fido-admin-signup-design.md`

## Global Constraints

- Oracle 스키마 동결. 테이블·컬럼을 추가하지 않는다 (`ddl-auto: none`).
- 신청 정보는 `CCFA_MANAGER` 에 저장한다. 새 테이블을 만들지 않는다.
- 상태값은 한글 표기를 쓴다: `승인대기`, `활성`, `거절`. `활성` 은 기존 `ManagerUserDetailsService.STATUS_ACTIVE` 와 정확히 같아야 한다.
- 미배정 소속 표식은 `-1L` 이다. `0L` 은 슈퍼 관리자를 뜻하므로 가입 경로에서 절대 쓰지 않는다.
- 비밀번호 정책: 8–64자, 영문·숫자·특수문자 모두 포함. 정규식 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$`.
- 외부 자원 호출 금지. CSS/JS/폰트는 jar 내부에서 서빙한다.
- 커밋 메시지는 한국어로 쓰고 다음 트레일러로 끝낸다:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`

## 범위 밖 (설계서 7장 — 구현하지 않는다)

아래는 의도적으로 뺀 것이다. 좋아 보이더라도 이 계획에서는 만들지 않는다.

- **신청 결과 알림(메일·SMS)** — `CCFA_MAILING` 이 있으나 실제 발송 주체가 이
  관리 도구가 아니다. 승인되면 로그인으로 확인 가능하다.
- **신청 횟수 제한(rate limit)** — 사내망 전용이라 실질 위험이 낮다.
- **가입 시 소속 자동 추론(이메일 도메인)** — 고객사별 도메인을 담을 컬럼이 없다.
- **거절 건 전용 삭제 기능** — 기존 운영자 관리 화면의 삭제를 쓴다.

## 사전 확인 사항 (구현자가 알아야 할 기존 코드 동작)

1. **`CrudService.create()` 는 가입에 쓸 수 없다.** 내부에서
   `TenantContext.isSuper()` → `TenantContext.companyIdx()` 를 호출하는데,
   이는 `require()` 로 이어져 로그인 사용자가 없으면 `IllegalStateException`
   을 던진다. 가입용 저장은 리포지터리를 직접 쓰는 별도 서비스로 만든다.

2. **감사 로그는 인증된 사용자가 없으면 기록되지 않는다.**
   `AuditLogger.log(type, message)` 는 `TenantContext.current()` 가 비어 있으면
   조용히 아무것도 하지 않는다. 따라서 **가입 신청은 감사 로그가 남지 않는다**
   (정상 동작이며 이 계획의 범위에서 바꾸지 않는다). 반대로 승인·거절은 슈퍼
   관리자가 로그인한 상태이므로 기록된다.

3. **`CCFA_MANAGER.IDX` 는 시퀀스로 채번된다.**
   `@GeneratedValue(SEQUENCE, generator = "ccfaManagerSeq")`, 시퀀스명
   `CCFA_MANAGER_SEQ`. 신청 저장 시 IDX 를 직접 넣지 않는다.

4. **`USER_ID` 에 DB 유니크 제약이 없다.** 중복은 코드로 막아야 하며, 기존
   `ManagerService.insert()` 가 `LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE`
   로 직렬화한다. 가입 저장도 같은 방식을 쓴다.

---

### Task 1: (선행 수정) COMPANY_IDX 가 null 이면 슈퍼 관리자가 되는 결함 차단

설계서 3.3. 가입 기능과 별개로 이미 존재하는 결함이다. 외부 입력이 계정 행을
만들기 전에 먼저 고친다.

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/auth/ManagerUserDetailsService.java`
- Test: `src/test/java/com/crosscert/fidoadmin/auth/ManagerUserDetailsServiceTest.java` (신규)

**Interfaces:**
- Consumes: 없음
- Produces: 없음 (동작 변경만). 이후 태스크는 `COMPANY_IDX = null` 계정이
  로그인 거부된다는 전제를 가진다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/auth/ManagerUserDetailsServiceTest.java` 생성:

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagerUserDetailsServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    ManagerUserDetailsService service = new ManagerUserDetailsService(managers, policies, companies);

    private CcfaManager manager(Long companyIdx) {
        CcfaManager m = new CcfaManager();
        m.setIdx(9L);
        m.setUserId("ghost");
        m.setUserPw("hash");
        m.setUserNm("유령");
        m.setCompanyIdx(companyIdx);
        m.setStatus("활성");
        return m;
    }

    /**
     * COMPANY_IDX 가 null 인 계정은 로그인이 거부되어야 한다.
     * null 을 0 으로 치환하면 isSuper() 가 참이 되어 슈퍼 관리자로 승격된다.
     */
    @Test void nullCompanyIdxIsRejectedInsteadOfBecomingSuper() {
        when(managers.findByUserId("ghost")).thenReturn(Optional.of(manager(null)));
        when(policies.findFirstByUserIdOrderByIdxDesc(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("COMPANY_IDX");
    }

    /** 정상 계정은 그대로 로그인된다(회귀 방지). */
    @Test void normalAccountStillLoads() {
        CcfaCompany c = new CcfaCompany();
        c.setIdx(1L);
        c.setCompanyName("KB국민은행");
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(1L)));
        when(policies.findFirstByUserIdOrderByIdxDesc(anyString())).thenReturn(Optional.empty());
        when(companies.findById(1L)).thenReturn(Optional.of(c));

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");
        assertThat(u.getCompanyIdx()).isEqualTo(1L);
        assertThat(u.isSuper()).isFalse();
        assertThat(u.isEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*ManagerUserDetailsServiceTest*"`
Expected: `nullCompanyIdxIsRejectedInsteadOfBecomingSuper` FAIL.
현재 코드는 예외를 던지지 않고 슈퍼 관리자 UserDetails 를 반환한다.

- [ ] **Step 3: 치환을 제거한다**

`ManagerUserDetailsService.loadUserByUsername` 에서 `null` → `0L` 치환을 없애고
`Long` 을 그대로 넘긴다. 회사명 조회는 `null` 일 때 DB 를 찌르지 않도록 막는다.

```java
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        CcfaManager m = managers.findByUserId(username)
            .orElseThrow(() -> new UsernameNotFoundException("운영자 없음: " + username));
        boolean locked = policies.findFirstByUserIdOrderByIdxDesc(username)
            .map(p -> "Y".equalsIgnoreCase(p.getAccountLock())).orElse(false);
        // COMPANY_IDX 가 null 이면 0 으로 치환하지 않는다. 0 은 SUPER 이므로 치환은 권한 상승이다.
        // null 은 ManagerUserDetails 생성자가 거부한다.
        Long companyIdx = m.getCompanyIdx();
        String companyName = companyIdx == null ? null
            : companies.findById(companyIdx).map(CcfaCompany::getCompanyName)
                .orElse(companyIdx == 0L ? "전역" : "고객사 " + companyIdx);
        String name = m.getUserNm() == null || m.getUserNm().isBlank() ? m.getUserId() : m.getUserNm();
        return new ManagerUserDetails(m.getIdx(), m.getUserId(), m.getUserPw(), name, companyIdx, companyName,
            STATUS_ACTIVE.equals(m.getStatus()), !locked);
    }
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*ManagerUserDetailsServiceTest*"`
Expected: PASS (2건).

- [ ] **Step 5: 전체 테스트로 회귀를 확인한다**

Run: `./gradlew test`
Expected: 기존 테스트 전부 통과.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/auth/ManagerUserDetailsService.java \
        src/test/java/com/crosscert/fidoadmin/auth/ManagerUserDetailsServiceTest.java
git commit -m "fix: COMPANY_IDX 가 null 인 계정이 슈퍼 관리자로 로그인되던 문제 차단

null 을 0 으로 치환하던 코드를 제거해 ManagerUserDetails 생성자의 방어가
실제로 동작하게 한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 가입 신청 상수와 비밀번호 정책 공통화

가입과 승인 양쪽이 쓰는 상수를 한 곳에 두고, 비밀번호 정책 검증을 컨트롤러
밖으로 뽑아 가입 화면과 운영자 등록 화면이 같은 규칙을 공유하게 한다.

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/signup/SignupPolicy.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/manager/web/ManagerController.java`
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupPolicyTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `SignupPolicy.STATUS_PENDING` = `"승인대기"`
  - `SignupPolicy.STATUS_ACTIVE` = `"활성"`
  - `SignupPolicy.STATUS_REJECTED` = `"거절"`
  - `SignupPolicy.UNASSIGNED_COMPANY_IDX` = `-1L`
  - `SignupPolicy.validatePassword(String pw, String confirm, BindingResult binding, String pwField, String confirmField)` — 정책 위반 시 `binding` 에 오류를 등록한다. 반환값 없음.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupPolicyTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

class SignupPolicyTest {

    record Form(String password, String passwordConfirm) {}

    private BindingResult bind(String pw, String confirm) {
        BindingResult b = new BeanPropertyBindingResult(new Form(pw, confirm), "form");
        SignupPolicy.validatePassword(pw, confirm, b, "password", "passwordConfirm");
        return b;
    }

    @Test void acceptsPolicyCompliantPassword() {
        assertThat(bind("Company1234!", "Company1234!").hasErrors()).isFalse();
    }

    @Test void rejectsTooShort() {
        BindingResult b = bind("Ab1!", "Ab1!");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("8자 이상");
    }

    @Test void rejectsTooLong() {
        String pw = "A1!" + "a".repeat(62);   // 65자
        BindingResult b = bind(pw, pw);
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("64자 이하");
    }

    @Test void rejectsMissingSpecialCharacter() {
        BindingResult b = bind("Company1234", "Company1234");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("특수문자");
    }

    @Test void rejectsMissingDigit() {
        BindingResult b = bind("Companyabc!", "Companyabc!");
        assertThat(b.getFieldError("password").getDefaultMessage()).contains("숫자");
    }

    @Test void rejectsConfirmMismatch() {
        BindingResult b = bind("Company1234!", "Company9999!");
        assertThat(b.getFieldError("passwordConfirm").getDefaultMessage()).contains("일치");
    }

    /** 상수는 기존 인증 코드의 활성 표기와 정확히 같아야 한다. */
    @Test void statusConstantsMatchExistingCode() {
        assertThat(SignupPolicy.STATUS_ACTIVE).isEqualTo("활성");
        assertThat(SignupPolicy.STATUS_PENDING).isEqualTo("승인대기");
        assertThat(SignupPolicy.STATUS_REJECTED).isEqualTo("거절");
        assertThat(SignupPolicy.UNASSIGNED_COMPANY_IDX).isEqualTo(-1L);
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*SignupPolicyTest*"`
Expected: 컴파일 실패 (`SignupPolicy` 없음).

- [ ] **Step 3: SignupPolicy 를 만든다**

`src/main/java/com/crosscert/fidoadmin/signup/SignupPolicy.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import java.util.regex.Pattern;
import org.springframework.validation.BindingResult;

/**
 * 가입 신청·승인에서 공유하는 상수와 비밀번호 정책.
 *
 * <p>STATUS_ACTIVE 는 {@link com.crosscert.fidoadmin.auth.ManagerUserDetailsService} 가
 * 로그인 가능 여부를 판단하는 값과 정확히 같아야 한다. 다르면 승인해도 로그인이 되지 않는다.
 */
public final class SignupPolicy {

    private SignupPolicy() {}

    /** 신청 직후. 로그인 불가(기존 상태 검사가 막는다). */
    public static final String STATUS_PENDING = "승인대기";
    /** 승인 완료. 로그인 가능. */
    public static final String STATUS_ACTIVE = "활성";
    /** 반려됨. 로그인 불가. */
    public static final String STATUS_REJECTED = "거절";

    /**
     * 소속 미배정 표식. 실제 고객사 IDX 는 0 이상이므로 겹치지 않는다.
     * 0 은 SUPER 를 뜻하므로 가입 경로에서 절대 쓰지 않는다.
     */
    public static final long UNASSIGNED_COMPANY_IDX = -1L;

    /** SUPER 를 뜻하는 소속. 승인 시 이 값으로 배정하는 것을 금지한다. */
    public static final long SUPER_COMPANY_IDX = 0L;

    private static final Pattern PASSWORD_POLICY =
        Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

    /** 정책 위반 시 binding 에 필드 오류를 등록한다. */
    public static void validatePassword(String pw, String confirm, BindingResult binding,
                                        String pwField, String confirmField) {
        if (pw == null || pw.isBlank()) {
            binding.rejectValue(pwField, "required", "비밀번호는 필수입니다.");
            return;
        }
        if (pw.length() < 8 || pw.length() > 64) {
            binding.rejectValue(pwField, "size", "비밀번호는 8자 이상 64자 이하여야 합니다.");
        } else if (!PASSWORD_POLICY.matcher(pw).matches()) {
            binding.rejectValue(pwField, "policy", "영문, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
        if (!pw.equals(confirm)) {
            binding.rejectValue(confirmField, "mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }
    }
}
```

- [ ] **Step 4: ManagerController 가 공통 정책을 쓰게 바꾼다**

`ManagerController` 의 `PASSWORD_POLICY` 상수와 `validate` 본문을 다음으로 바꾼다.
(상수 필드와 `java.util.regex.Pattern` import 를 제거한다.)

```java
    @Override protected void validate(ManagerForm f, boolean isNew, BindingResult binding) {
        if (isNew && !f.hasPassword()) {
            binding.rejectValue("password", "required", "비밀번호는 필수입니다.");
        }
        if (f.hasPassword()) {
            SignupPolicy.validatePassword(f.getPassword(), f.getPasswordConfirm(),
                binding, "password", "passwordConfirm");
        }
    }
```

`import com.crosscert.fidoadmin.signup.SignupPolicy;` 를 추가한다.

- [ ] **Step 5: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupPolicyTest*" --tests "*ManagerControllerWebTest*"`
Expected: PASS. 기존 운영자 등록의 비밀번호 검증 테스트가 그대로 통과해야 한다
(문구를 바꾸지 않았으므로).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/signup/SignupPolicy.java \
        src/main/java/com/crosscert/fidoadmin/manager/web/ManagerController.java \
        src/test/java/com/crosscert/fidoadmin/signup/SignupPolicyTest.java
git commit -m "refactor: 가입·운영자 등록이 공유할 비밀번호 정책과 상태 상수 분리

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: 가입 신청 서비스 (권한 상승 차단의 핵심)

인증 없이 호출되므로 `CrudService` 를 상속하지 않는다. 리포지터리를 직접 쓴다.

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/signup/SignupService.java`
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupServiceTest.java`

**Interfaces:**
- Consumes: `SignupPolicy.STATUS_PENDING`, `SignupPolicy.UNASSIGNED_COMPANY_IDX`
- Produces:
  - `SignupService.apply(String userId, String encodedPw, String userNm, String userEmail, String userPhone, String reason)` → `CcfaManager`
    - 중복 아이디면 `DataIntegrityViolationException`
    - 저장 결과는 항상 `STATUS_PENDING` / `COMPANY_IDX = -1`
  - `SignupService.existsUserId(String userId)` → `boolean`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupServiceTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SignupServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    EntityManager em = mock(EntityManager.class);
    SignupService service = new SignupService(managers, em);

    @BeforeEach void stubLock() {
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.executeUpdate()).thenReturn(0);
        when(managers.save(any(CcfaManager.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 신청 결과는 항상 승인대기 + 미배정 소속이다. */
    @Test void applyForcesPendingStatusAndUnassignedCompany() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());

        CcfaManager saved = service.apply("newbie", "hash", "홍길동",
            "hong@kb.local", "010-0000-0000", "업무 담당자입니다");

        assertThat(saved.getStatus()).isEqualTo("승인대기");
        assertThat(saved.getCompanyIdx()).isEqualTo(-1L);
        assertThat(saved.getUserId()).isEqualTo("newbie");
        assertThat(saved.getUserPw()).isEqualTo("hash");
        assertThat(saved.getEtc()).contains("업무 담당자입니다");
        assertThat(saved.getCreatedtime()).isNotNull();
    }

    /** IDX 는 시퀀스가 채번한다. 서비스가 직접 넣으면 안 된다. */
    @Test void applyDoesNotAssignIdx() {
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());
        CcfaManager saved = service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, null);
        assertThat(saved.getIdx()).isNull();
    }

    @Test void applyRejectsDuplicateUserId() {
        CcfaManager existing = new CcfaManager();
        existing.setUserId("kbadmin");
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.apply("kbadmin", "hash", "홍길동", "hong@kb.local", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void existsUserIdReflectsRepository() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(new CcfaManager()));
        when(managers.findByUserId("newbie")).thenReturn(Optional.empty());
        assertThat(service.existsUserId("kbadmin")).isTrue();
        assertThat(service.existsUserId("newbie")).isFalse();
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*SignupServiceTest*"`
Expected: 컴파일 실패 (`SignupService` 없음).

- [ ] **Step 3: SignupService 를 만든다**

`src/main/java/com/crosscert/fidoadmin/signup/SignupService.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청 저장. 인증 없이 호출되므로 CrudService 를 쓰지 않는다
 * (CrudService.create() 는 TenantContext.require() 로 이어져 로그인 사용자를 요구한다).
 *
 * <p>감사 로그는 남지 않는다. AuditLogger 는 로그인 사용자가 없으면 기록하지 않기 때문이며,
 * 이는 의도된 동작이다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final CcfaManagerRepository managers;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public boolean existsUserId(String userId) {
        return managers.findByUserId(userId).isPresent();
    }

    /**
     * 신청을 저장한다. 상태와 소속은 입력과 무관하게 강제된다(권한 상승 차단 1단계).
     *
     * <p>USER_ID 에 DB 유니크 제약이 없어 동시 신청 시 중복이 생길 수 있다.
     * ManagerService.insert() 와 같은 방식으로 테이블을 배타 잠금해 직렬화한다.
     */
    @Transactional
    public CcfaManager apply(String userId, String encodedPw, String userNm,
                             String userEmail, String userPhone, String reason) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(userId).isPresent()) {
            throw new DataIntegrityViolationException("이미 사용 중인 아이디입니다: " + userId);
        }
        LocalDateTime now = LocalDateTime.now();
        CcfaManager m = new CcfaManager();
        // IDX 는 CCFA_MANAGER_SEQ 가 채번한다. 직접 넣지 않는다.
        m.setUserId(userId);
        m.setUserPw(encodedPw);
        m.setUserNm(userNm);
        m.setUserEmail(userEmail);
        m.setUserPhone(userPhone);
        // 폼에 상태·소속 입력란이 없지만, 파라미터로 넘어와도 무시되도록 여기서 강제한다.
        m.setStatus(SignupPolicy.STATUS_PENDING);
        m.setCompanyIdx(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        m.setLogin("OFF-LINE");
        m.setAlramType("none");
        m.setAlramLevel("0");
        m.setEtc(reason == null || reason.isBlank() ? null : "신청 사유: " + reason);
        m.setCreatedtime(now);
        m.setUpdatedtime(now);
        return managers.save(m);
    }
}
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupServiceTest*"`
Expected: PASS (4건).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/signup/SignupService.java \
        src/test/java/com/crosscert/fidoadmin/signup/SignupServiceTest.java
git commit -m "feat: 가입 신청 저장 서비스 추가

상태는 승인대기, 소속은 -1 로 강제해 가입 경로의 권한 상승을 막는다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: 가입 신청 화면과 컨트롤러

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/signup/SignupForm.java`
- Create: `src/main/java/com/crosscert/fidoadmin/signup/SignupController.java`
- Create: `src/main/resources/templates/signup/form.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java`
- Modify: `src/main/resources/templates/login.html`
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupControllerWebTest.java`

**Interfaces:**
- Consumes: `SignupService.apply(...)`, `SignupService.existsUserId(...)`, `SignupPolicy.validatePassword(...)`
- Produces: `GET /signup` (폼), `POST /signup` (신청 처리 → `/login` 리다이렉트)

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupControllerWebTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignupController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class})
class SignupControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SignupService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    /** 가입 화면은 로그인 없이 열린다. */
    @Test void formIsPublic() throws Exception {
        mvc.perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("가입 신청")));
    }

    /** 소속 고객사 선택란을 두지 않는다(로그인 전 화면에 고객사 목록을 노출하지 않는다). */
    @Test void formHasNoCompanySelector() throws Exception {
        mvc.perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("name=\"status\""))));
    }

    @Test void validApplicationRedirectsToLogin() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local")
                .param("userPhone", "010-0000-0000")
                .param("reason", "업무 담당자입니다"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?signup"));
    }

    /** 폼에 소속·상태를 실어 보내도 서비스는 그 값을 받지 않는다. */
    @Test void injectedCompanyIdxAndStatusAreIgnored() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "attacker")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "공격자")
                .param("userEmail", "a@b.local")
                .param("companyIdx", "0")
                .param("status", "활성"))
            .andExpect(status().is3xxRedirection());
        // 시그니처에 소속·상태 자리가 없으므로 주입될 통로가 없다
        verify(service).apply("attacker", anyStringSafe(), "공격자", "a@b.local", null, null);
    }

    private static String anyStringSafe() { return org.mockito.ArgumentMatchers.anyString(); }

    @Test void rejectsWeakPassword() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "weak")
                .param("passwordConfirm", "weak")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "password"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test void rejectsConfirmMismatch() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company9999!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "passwordConfirm"));
    }

    @Test void rejectsDuplicateUserId() throws Exception {
        when(service.existsUserId("kbadmin")).thenReturn(true);
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "kbadmin")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /** 저장 직전 경합으로 중복이 났을 때도 폼 오류로 돌려준다(500 이 아니다). */
    @Test void handlesRaceConditionDuplicate() throws Exception {
        when(service.existsUserId("newbie")).thenReturn(false);
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("이미 사용 중인 아이디입니다: newbie"));
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
    }
}
```

**주의:** 위 테스트의 `injectedCompanyIdxAndStatusAreIgnored` 에서 Mockito 매처와
실제 값을 섞으면 예외가 난다. 구현자는 이 테스트를 다음으로 바꿔 작성한다
(매처를 섞지 않는 형태):

```java
    @Test void injectedCompanyIdxAndStatusAreIgnored() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "attacker")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "공격자")
                .param("userEmail", "a@b.local")
                .param("companyIdx", "0")
                .param("status", "활성"))
            .andExpect(status().is3xxRedirection());

        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        verify(service).apply(userId.capture(), anyString(), anyString(), anyString(), any(), any());
        assertThat(userId.getValue()).isEqualTo("attacker");
        // SignupForm 에 companyIdx·status 필드가 없으므로 주입 통로 자체가 없다
        assertThat(SignupForm.class.getDeclaredFields())
            .noneMatch(f -> f.getName().equals("companyIdx") || f.getName().equals("status"));
    }
```

필요한 import: `org.mockito.ArgumentCaptor`, `static org.assertj.core.api.Assertions.assertThat`.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*SignupControllerWebTest*"`
Expected: 컴파일 실패 (`SignupController`, `SignupForm` 없음).

- [ ] **Step 3: SignupForm 을 만든다**

`src/main/java/com/crosscert/fidoadmin/signup/SignupForm.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.crosscert.fidoadmin.common.ByteSize;
import lombok.Getter;
import lombok.Setter;

/**
 * 가입 신청 폼.
 *
 * <p>companyIdx·status 필드를 의도적으로 두지 않는다. 필드가 없으면 파라미터로 넘어와도
 * 바인딩되지 않으므로, 가입 경로로 소속이나 상태를 지정할 통로 자체가 사라진다.
 */
@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "아이디는 필수입니다.")
    @ByteSize(max = 64, message = "아이디는 64바이트 이하여야 합니다.")
    private String userId;

    private String password;
    private String passwordConfirm;

    @NotBlank(message = "이름은 필수입니다.")
    @ByteSize(max = 50, message = "이름은 50바이트 이하여야 합니다.")
    private String userNm;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @ByteSize(max = 256, message = "이메일은 256바이트 이하여야 합니다.")
    private String userEmail;

    @ByteSize(max = 20, message = "전화는 20바이트 이하여야 합니다.")
    private String userPhone;

    @ByteSize(max = 1700, message = "신청 사유는 1700바이트 이하여야 합니다.")
    private String reason;

    /** 빈 문자열은 null 로 다룬다(선택 항목). */
    public String normalizedPhone() { return blankToNull(userPhone); }
    public String normalizedReason() { return blankToNull(reason); }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
```

**길이 제한은 반드시 `@ByteSize` 로 건다(`@Size` 아님).** KBFIDO 스키마의 VARCHAR2 는
바이트 기준(`NLS_LENGTH_SEMANTICS=BYTE`, AL32UTF8)이라 한글 한 글자가 3바이트다.
`@Size(max = 1900)` 이면 한글 1900자(5700바이트)가 검증을 통과해 2048바이트 컬럼에
들어가려다 ORA-12899 로 500 오류가 난다. 이 저장소는 이미 `common/ByteSize` 로 이
문제를 해결했고 `@ByteSize` 96곳 대 `@Size` 2곳(둘 다 비밀번호, 글자 수가 맞는 자리)으로
쓰고 있다. `ManagerForm` 도 전부 `@ByteSize` 다.

`reason` 최대 1700바이트인 이유: `ETC` 컬럼이 2048바이트이고 저장 시
`"신청 사유: "` 접두와 이후 거절 사유가 덧붙을 여유를 둔다.

- [ ] **Step 4: SignupController 를 만든다**

`src/main/java/com/crosscert/fidoadmin/signup/SignupController.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 가입 신청. 인증 없이 접근한다(SecurityConfig 의 permitAll). */
@Controller
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService service;
    private final PasswordEncoder encoder;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", new SignupForm());
        return "signup/form";
    }

    @PostMapping
    public String apply(@Valid @ModelAttribute("form") SignupForm form, BindingResult binding, Model model) {
        SignupPolicy.validatePassword(form.getPassword(), form.getPasswordConfirm(),
            binding, "password", "passwordConfirm");
        if (form.getUserId() != null && !form.getUserId().isBlank() && service.existsUserId(form.getUserId())) {
            binding.rejectValue("userId", "duplicate", "이미 사용 중인 아이디입니다.");
        }
        if (binding.hasErrors()) {
            return "signup/form";
        }
        try {
            service.apply(form.getUserId(), encoder.encode(form.getPassword()), form.getUserNm(),
                form.getUserEmail(), form.normalizedPhone(), form.normalizedReason());
        } catch (DataIntegrityViolationException e) {
            // existsUserId 통과 후 저장 직전에 경합으로 중복이 난 경우
            binding.rejectValue("userId", "duplicate", "이미 사용 중인 아이디입니다.");
            return "signup/form";
        }
        return "redirect:/login?signup";
    }
}
```

- [ ] **Step 5: 가입 화면 템플릿을 만든다**

`src/main/resources/templates/signup/form.html` 생성. 로그인 화면과 같은 골격을
쓴다(레이아웃을 쓰지 않는다 — 사이드바가 없는 화면이다).

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>FIDO Admin 가입 신청</title>
  <link rel="icon" th:href="@{/favicon.ico}" sizes="32x32">
  <link rel="icon" type="image/png" th:href="@{/favicon.png}" sizes="192x192">
  <link rel="apple-touch-icon" th:href="@{/favicon.png}">
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
  <link rel="stylesheet" th:href="@{/css/admin.css}">
</head>
<body class="bg-light">
<div class="container" style="max-width: 560px;">
  <div class="card my-5">
    <div class="card-body p-4">
      <h1 class="h5 mb-1">가입 신청</h1>
      <p class="text-secondary small mb-4">신청 후 관리자 승인을 받아야 로그인할 수 있습니다.</p>

      <form th:action="@{/signup}" th:object="${form}" method="post">
        <div class="mb-3">
          <label class="form-label small">아이디 <span class="text-danger">*</span></label>
          <input type="text" th:field="*{userId}" class="form-control form-control-sm" autocomplete="username">
          <div class="text-danger small" th:if="${#fields.hasErrors('userId')}" th:errors="*{userId}"></div>
        </div>
        <div class="mb-3">
          <label class="form-label small">비밀번호 <span class="text-danger">*</span></label>
          <input type="password" th:field="*{password}" class="form-control form-control-sm" autocomplete="new-password">
          <div class="form-text">8자 이상 64자 이하, 영문·숫자·특수문자를 모두 포함합니다.</div>
          <div class="text-danger small" th:if="${#fields.hasErrors('password')}" th:errors="*{password}"></div>
        </div>
        <div class="mb-3">
          <label class="form-label small">비밀번호 확인 <span class="text-danger">*</span></label>
          <input type="password" th:field="*{passwordConfirm}" class="form-control form-control-sm" autocomplete="new-password">
          <div class="text-danger small" th:if="${#fields.hasErrors('passwordConfirm')}" th:errors="*{passwordConfirm}"></div>
        </div>
        <div class="mb-3">
          <label class="form-label small">이름 <span class="text-danger">*</span></label>
          <input type="text" th:field="*{userNm}" class="form-control form-control-sm">
          <div class="text-danger small" th:if="${#fields.hasErrors('userNm')}" th:errors="*{userNm}"></div>
        </div>
        <div class="mb-3">
          <label class="form-label small">이메일 <span class="text-danger">*</span></label>
          <input type="email" th:field="*{userEmail}" class="form-control form-control-sm">
          <div class="text-danger small" th:if="${#fields.hasErrors('userEmail')}" th:errors="*{userEmail}"></div>
        </div>
        <div class="mb-3">
          <label class="form-label small">전화</label>
          <input type="text" th:field="*{userPhone}" class="form-control form-control-sm">
          <div class="text-danger small" th:if="${#fields.hasErrors('userPhone')}" th:errors="*{userPhone}"></div>
        </div>
        <div class="mb-4">
          <label class="form-label small">신청 사유</label>
          <textarea th:field="*{reason}" class="form-control form-control-sm" rows="3"
                    placeholder="소속과 담당 업무를 적어주시면 승인이 빨라집니다."></textarea>
          <div class="text-danger small" th:if="${#fields.hasErrors('reason')}" th:errors="*{reason}"></div>
        </div>
        <button type="submit" class="btn btn-primary w-100">신청</button>
      </form>

      <div class="text-center mt-3">
        <a th:href="@{/login}" class="small">로그인으로 돌아가기</a>
      </div>
    </div>
  </div>
</div>
</body>
</html>
```

- [ ] **Step 6: SecurityConfig 에 /signup 을 연다**

`permitAll` 목록에 `"/signup"` 을 추가한다.

```java
                .requestMatchers("/login", "/signup", "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**",
                    "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png").permitAll()
```

- [ ] **Step 7: 로그인 화면에 가입 링크와 접수 안내를 단다**

`src/main/resources/templates/login.html` 의 로그인 폼 아래에 추가한다.
(기존 오류 문구 블록 근처에 접수 안내를 둔다.)

```html
      <div class="alert alert-success py-2 small" th:if="${param.signup}">
        가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.
      </div>
```

그리고 폼 아래에:

```html
      <div class="text-center mt-3">
        <a th:href="@{/signup}" class="small">가입 신청</a>
      </div>
```

- [ ] **Step 8: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupControllerWebTest*"`
Expected: PASS (8건).

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/signup/ \
        src/main/resources/templates/signup/ \
        src/main/resources/templates/login.html \
        src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java \
        src/test/java/com/crosscert/fidoadmin/signup/SignupControllerWebTest.java
git commit -m "feat: 가입 신청 화면 추가

폼에 소속·상태 필드를 두지 않아 가입 경로로 권한을 지정할 통로를 없앤다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 승인·거절 서비스 (권한 상승 차단 2·3단계)

**Files:**
- Modify: `src/main/java/com/crosscert/fidoadmin/signup/SignupService.java`
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupApprovalServiceTest.java`

**Interfaces:**
- Consumes: `SignupPolicy.*`
- Produces:
  - `SignupService.pending()` → `List<CcfaManager>` (신청일시 오름차순)
  - `SignupService.approve(Long idx, Long companyIdx)` → `CcfaManager`
    - `companyIdx == 0` 이면 `IllegalArgumentException`
    - 대상이 `승인대기` 가 아니면 `IllegalStateException`
  - `SignupService.reject(Long idx, String reason)` → `CcfaManager`
    - 대상이 `승인대기` 가 아니면 `IllegalStateException`

- [x] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupApprovalServiceTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignupApprovalServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    EntityManager em = mock(EntityManager.class);
    AuditLogger audit = mock(AuditLogger.class);
    SignupService service = new SignupService(managers, em, audit);

    private CcfaManager pending() {
        CcfaManager m = new CcfaManager();
        m.setIdx(5L);
        m.setUserId("newbie");
        m.setUserNm("홍길동");
        m.setStatus("승인대기");
        m.setCompanyIdx(-1L);
        m.setEtc("신청 사유: 업무 담당자입니다");
        m.setCreatedtime(LocalDateTime.now());
        return m;
    }

    @BeforeEach void stub() {
        when(managers.save(any(CcfaManager.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void approveAssignsCompanyAndActivates() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.approve(5L, 1L);

        assertThat(saved.getStatus()).isEqualTo("활성");
        assertThat(saved.getCompanyIdx()).isEqualTo(1L);
        assertThat(saved.getUpdatedtime()).isNotNull();
    }

    /** 권한 상승 차단 2단계: 승인으로 SUPER 를 만들 수 없다. */
    @Test void approveRejectsSuperCompanyIdx() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.approve(5L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("전역");
    }

    /** 미배정 표식으로는 승인할 수 없다. */
    @Test void approveRejectsUnassignedCompanyIdx() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.approve(5L, -1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** 권한 상승 차단 3단계: 이미 활성인 계정은 이 경로로 못 바꾼다. */
    @Test void approveRejectsAlreadyActiveAccount() {
        CcfaManager active = pending();
        active.setStatus("활성");
        active.setCompanyIdx(1L);
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.approve(5L, 2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("승인대기");
    }

    @Test void rejectSetsRejectedStatusAndKeepsReason() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.reject(5L, "소속 확인 불가");

        assertThat(saved.getStatus()).isEqualTo("거절");
        assertThat(saved.getEtc()).contains("업무 담당자입니다");
        assertThat(saved.getEtc()).contains("거절 사유: 소속 확인 불가");
        // 거절은 소속을 배정하지 않는다
        assertThat(saved.getCompanyIdx()).isEqualTo(-1L);
    }

    @Test void rejectRejectsAlreadyActiveAccount() {
        CcfaManager active = pending();
        active.setStatus("활성");
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.reject(5L, "사유"))
            .isInstanceOf(IllegalStateException.class);
    }

    /** ETC 는 2048자 제한이 있다. 넘치면 잘라 저장한다. */
    @Test void rejectTruncatesOverlongEtc() {
        CcfaManager m = pending();
        m.setEtc("가".repeat(2000));
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(m));

        CcfaManager saved = service.reject(5L, "나".repeat(200));

        assertThat(saved.getEtc().length()).isLessThanOrEqualTo(2048);
    }
}
```

- [x] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*SignupApprovalServiceTest*"`
Expected: 컴파일 실패 (`approve`, `reject`, `pending` 없음 / 생성자 인자 3개).

- [x] **Step 3: SignupService 에 승인·거절을 추가한다**

생성자에 `AuditLogger audit` 를 추가하고(`@RequiredArgsConstructor` 가 자동 생성),
다음 메서드와 import 를 더한다.

```java
import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import java.util.List;
```

필드에 `private final AuditLogger audit;` 를 추가한다.

```java
    /** 승인 대기 목록. 오래된 신청이 위로 온다. */
    @Transactional(readOnly = true)
    public List<CcfaManager> pending() {
        return managers.findByStatusOrderByCreatedtimeAsc(SignupPolicy.STATUS_PENDING);
    }

    /**
     * 승인: 소속을 배정하고 활성화한다.
     *
     * <p>권한 상승 차단 2단계 — 전역(IDX 0) 배정을 거부한다.
     * 권한 상승 차단 3단계 — 대상이 승인대기일 때만 동작한다.
     */
    @Transactional
    public CcfaManager approve(Long idx, Long companyIdx) {
        if (companyIdx == null || companyIdx == SignupPolicy.SUPER_COMPANY_IDX) {
            throw new IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다.");
        }
        if (companyIdx < 0) {
            throw new IllegalArgumentException("고객사를 선택해야 합니다.");
        }
        CcfaManager m = lockPending(idx);
        m.setCompanyIdx(companyIdx);
        m.setStatus(SignupPolicy.STATUS_ACTIVE);
        m.setUpdatedtime(LocalDateTime.now());
        CcfaManager saved = managers.save(m);
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP APPROVE " + saved.getUserId()
            + " -> COMPANY_IDX " + companyIdx);
        return saved;
    }

    /** 거절: 상태를 바꾸고 사유를 ETC 에 덧붙인다. 소속은 미배정 그대로 둔다. */
    @Transactional
    public CcfaManager reject(Long idx, String reason) {
        CcfaManager m = lockPending(idx);
        m.setStatus(SignupPolicy.STATUS_REJECTED);
        m.setEtc(appendReason(m.getEtc(), reason));
        m.setUpdatedtime(LocalDateTime.now());
        CcfaManager saved = managers.save(m);
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP REJECT " + saved.getUserId());
        return saved;
    }

    /**
     * 대상 행을 FOR UPDATE 로 잠그고 승인대기인지 다시 확인한다.
     * 두 관리자가 동시에 처리해도 한 번만 적용된다.
     *
     * <p>리포지터리에 이미 있는 findByIdxForUpdate 를 쓴다(로그인 실패 집계가 쓰는
     * findByUserIdForUpdate 와 같은 @Lock(PESSIMISTIC_WRITE) 방식이다).
     */
    private CcfaManager lockPending(Long idx) {
        CcfaManager m = managers.findByIdxForUpdate(idx)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다: " + idx));
        if (!SignupPolicy.STATUS_PENDING.equals(m.getStatus())) {
            throw new IllegalStateException("이미 처리된 신청입니다(승인대기 상태가 아닙니다): " + m.getUserId());
        }
        return m;
    }

    /**
     * ETC 는 2048 **바이트** 제한이다(문자 수가 아니다. 한글 1자 = 3바이트).
     * 넘치면 바이트 기준으로 자르되, 글자 중간에서 잘라 깨진 문자가 저장되지 않게 한다.
     */
    private static String appendReason(String etc, String reason) {
        String added = "거절 사유: " + (reason == null || reason.isBlank() ? "(사유 없음)" : reason);
        String merged = etc == null || etc.isBlank() ? added : etc + "\n" + added;
        return truncateToBytes(merged, 2048);
    }

    /** UTF-8 바이트 기준으로 자른다. 경계에서 글자가 쪼개지지 않도록 CharsetEncoder 를 쓴다. */
    private static String truncateToBytes(String s, int maxBytes) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return s;
        java.nio.CharBuffer out = java.nio.CharBuffer.allocate(s.length());
        java.nio.charset.CharsetEncoder enc = java.nio.charset.StandardCharsets.UTF_8.newEncoder();
        java.nio.ByteBuffer limited = java.nio.ByteBuffer.allocate(maxBytes);
        enc.encode(java.nio.CharBuffer.wrap(s), limited, true);
        limited.flip();
        java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE)
            .decode(limited, out, true);
        out.flip();
        return out.toString();
    }
```

- [x] **Step 4: 리포지터리에 조회·잠금 메서드를 추가한다**

`src/main/java/com/crosscert/fidoadmin/manager/repository/CcfaManagerRepository.java` 에 추가.
`findByIdxForUpdate` 는 기존 `findByUserIdForUpdate` 와 같은 방식이다(바로 위에 있으니 참고).

```java
    java.util.List<CcfaManager> findByStatusOrderByCreatedtimeAsc(String status);

    /** 승인·거절에서 대상 행을 잠근다. 잠그지 않으면 동시 처리가 서로를 덮어쓴다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CcfaManager m where m.idx = :idx")
    Optional<CcfaManager> findByIdxForUpdate(@Param("idx") Long idx);
```

`@Lock`, `LockModeType`, `@Query`, `@Param`, `Optional` 은 이 파일에 이미
import 되어 있다(`findByUserIdForUpdate` 가 쓰고 있다).

- [x] **Step 5: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupApprovalServiceTest*" --tests "*SignupServiceTest*"`
Expected: PASS. `SignupServiceTest` 는 생성자 인자가 3개로 늘었으므로
`new SignupService(managers, em, mock(AuditLogger.class))` 로 고친다.

- [x] **Step 6: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/signup/SignupService.java \
        src/main/java/com/crosscert/fidoadmin/manager/repository/CcfaManagerRepository.java \
        src/test/java/com/crosscert/fidoadmin/signup/
git commit -m "feat: 가입 신청 승인·거절 처리 추가

승인 시 전역(IDX 0) 배정을 거부하고, 승인대기 상태인 신청만 처리한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```
: 승인 화면 (SUPER 전용)

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/signup/SignupAdminController.java`
- Create: `src/main/resources/templates/signup/list.html`
- Modify: `src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java`
- Modify: `src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java`
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupAdminControllerWebTest.java`

**Interfaces:**
- Consumes: `SignupService.pending()`, `approve(Long, Long)`, `reject(Long, String)`,
  `CompanyLookup.all()`
- Produces: `GET /signups`, `POST /signups/{idx}/approve`, `POST /signups/{idx}/reject`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupAdminControllerWebTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignupAdminController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class})
class SignupAdminControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SignupService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaManager pending() {
        CcfaManager m = new CcfaManager();
        m.setIdx(5L); m.setUserId("newbie"); m.setUserNm("홍길동");
        m.setUserEmail("hong@kb.local"); m.setStatus("승인대기"); m.setCompanyIdx(-1L);
        m.setEtc("신청 사유: 업무 담당자입니다");
        m.setCreatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return m;
    }

    private CcfaCompany company(long idx, String name) {
        CcfaCompany c = new CcfaCompany(); c.setIdx(idx); c.setCompanyName(name); return c;
    }

    @Test void listShowsPendingApplications() throws Exception {
        when(service.pending()).thenReturn(List.of(pending()));
        when(companies.all()).thenReturn(List.of(company(0L, "전역"), company(1L, "KB국민은행")));
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("newbie")))
            .andExpect(content().string(containsString("홍길동")))
            .andExpect(content().string(containsString("업무 담당자입니다")));
    }

    /** 승인 화면의 고객사 선택 목록에서 전역(IDX 0)을 제외한다. */
    @Test void companySelectExcludesGlobalCompany() throws Exception {
        when(service.pending()).thenReturn(List.of(pending()));
        when(companies.all()).thenReturn(List.of(company(0L, "전역"), company(1L, "KB국민은행")));
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                containsString("value=\"0\""))));
    }

    @Test void approveRedirectsWithFlash() throws Exception {
        when(service.approve(5L, 1L)).thenReturn(pending());
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 승인했습니다."));
        verify(service).approve(5L, 1L);
    }

    /** 서비스가 거부하면 오류 플래시로 돌려준다(500 이 아니다). */
    @Test void approveShowsErrorWhenServiceRejects() throws Exception {
        when(service.approve(5L, 0L))
            .thenThrow(new IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다."));
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attributeExists("flashError"));
    }

    @Test void rejectRedirectsWithFlash() throws Exception {
        when(service.reject(5L, "소속 확인 불가")).thenReturn(pending());
        mvc.perform(post("/signups/5/reject").with(user(superUser)).with(csrf())
                .param("reason", "소속 확인 불가"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 거절했습니다."));
        verify(service).reject(5L, "소속 확인 불가");
    }

    /** 일반 고객사 계정은 접근할 수 없다. */
    @Test void companyUserIsForbidden() throws Exception {
        mvc.perform(get("/signups").with(user(companyUser)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/signups/5/approve").with(user(companyUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().isForbidden());
        verify(service, never()).approve(anyLong(), anyLong());
    }

    @Test void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/signups"))
            .andExpect(status().is3xxRedirection());
        verify(service, never()).pending();
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "*SignupAdminControllerWebTest*"`
Expected: 컴파일 실패 (`SignupAdminController` 없음).

- [ ] **Step 3: SignupAdminController 를 만든다**

`src/main/java/com/crosscert/fidoadmin/signup/SignupAdminController.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 가입 승인. SUPER 전용(SecurityConfig 에서 막는다). */
@Controller
@RequestMapping("/signups")
@RequiredArgsConstructor
public class SignupAdminController {

    private final SignupService service;
    private final CompanyLookup companies;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", service.pending());
        // 전역(IDX 0)은 선택 목록에서 제외한다. 승인으로 SUPER 를 만들 수 없게 하기 위해서다.
        List<CcfaCompany> selectable = companies.all().stream()
            .filter(c -> c.getIdx() != null && c.getIdx() != SignupPolicy.SUPER_COMPANY_IDX)
            .toList();
        model.addAttribute("companies", selectable);
        return "signup/list";
    }

    @PostMapping("/{idx}/approve")
    public String approve(@PathVariable Long idx, @RequestParam Long companyIdx, RedirectAttributes redirect) {
        try {
            service.approve(idx, companyIdx);
            redirect.addFlashAttribute("flashSuccess", "가입을 승인했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/signups";
    }

    @PostMapping("/{idx}/reject")
    public String reject(@PathVariable Long idx, @RequestParam(required = false) String reason,
                         RedirectAttributes redirect) {
        try {
            service.reject(idx, reason);
            redirect.addFlashAttribute("flashSuccess", "가입을 거절했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/signups";
    }
}
```

- [ ] **Step 4: 승인 화면 템플릿을 만든다**

`src/main/resources/templates/signup/list.html` 생성. 기존 목록 화면 규약을 따른다.

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>가입 승인</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">가입 승인</h1>
  </div>

  <p class="text-secondary small" th:if="${#lists.isEmpty(items)}">승인 대기 중인 신청이 없습니다.</p>

  <div class="fa-section" th:each="m : ${items}">
    <div class="bg-white border rounded p-3">
      <div class="d-flex justify-content-between align-items-start mb-2">
        <div>
          <span class="fw-semibold" th:text="${m.userNm}">이름</span>
          <span class="text-secondary small ms-2" th:text="${m.userId}">아이디</span>
        </div>
        <span class="text-secondary small"
              th:text="${#temporals.format(m.createdtime, 'yyyy-MM-dd HH:mm')}">신청일시</span>
      </div>
      <div class="small text-secondary mb-1" th:text="${m.userEmail}">이메일</div>
      <div class="small text-secondary mb-1" th:if="${m.userPhone}" th:text="${m.userPhone}">전화</div>
      <div class="small fa-pre mb-3" th:if="${m.etc}" th:text="${m.etc}">신청 사유</div>

      <div class="row g-2 align-items-end">
        <div class="col-auto">
          <form th:action="@{/signups/{id}/approve(id=${m.idx})}" method="post"
                th:id="'approveForm' + ${m.idx}" class="d-flex gap-2 align-items-end m-0">
            <div>
              <label class="form-label small mb-0">소속 고객사</label>
              <select name="companyIdx" class="form-select form-select-sm" required>
                <option value="">선택하세요</option>
                <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}"></option>
              </select>
            </div>
            <button type="button" class="btn btn-primary btn-sm"
                    th:attr="data-confirm-form='approveForm' + ${m.idx}"
                    data-confirm-message="이 신청을 승인하시겠습니까?" data-confirm-ok="승인">승인</button>
          </form>
        </div>
        <div class="col">
          <form th:action="@{/signups/{id}/reject(id=${m.idx})}" method="post"
                th:id="'rejectForm' + ${m.idx}" class="d-flex gap-2 align-items-end m-0">
            <div class="flex-grow-1">
              <label class="form-label small mb-0">거절 사유</label>
              <!-- 100자 제한: ETC 여유가 318바이트뿐이라 한글 106자가 한계다. 아래 계산 참고. -->
              <input type="text" name="reason" class="form-control form-control-sm" maxlength="100">
            </div>
            <button type="button" class="btn btn-outline-danger btn-sm"
                    th:attr="data-confirm-form='rejectForm' + ${m.idx}"
                    data-confirm-message="이 신청을 거절하시겠습니까?" data-confirm-ok="거절">거절</button>
          </form>
        </div>
      </div>
    </div>
  </div>
</main>
</body>
</html>
```

- [ ] **Step 5: SecurityConfig 에서 SUPER 전용으로 막는다**

`hasRole("SUPER")` 목록에 `"/signups/**"` 를 추가한다.

```java
                .requestMatchers("/companies/**", "/licenses/**", "/managers/**", "/system/**",
                                 "/criteria/**", "/fido2/**", "/signups/**").hasRole("SUPER")
```

- [ ] **Step 6: 사이드바 메뉴를 추가한다**

`MenuRegistry.ALL` 의 "운영자" 그룹에 추가한다(운영자 항목 바로 뒤).

```java
        new MenuItem("운영자", "가입 승인", "/signups", true, "bi-person-check"),
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupAdminControllerWebTest*" --tests "*MenuRegistryTest*" --tests "*LayoutWebTest*"`
Expected: PASS. `MenuRegistryTest.everyMenuHasAnIcon` 이 새 메뉴에도 통과해야 한다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/signup/SignupAdminController.java \
        src/main/resources/templates/signup/list.html \
        src/main/java/com/crosscert/fidoadmin/config/SecurityConfig.java \
        src/main/java/com/crosscert/fidoadmin/common/MenuRegistry.java \
        src/test/java/com/crosscert/fidoadmin/signup/SignupAdminControllerWebTest.java
git commit -m "feat: 가입 승인 화면 추가

고객사 선택 목록에서 전역(IDX 0)을 제외한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: 인증 흐름 통합 검증과 문서 갱신

상태에 따른 로그인 가부를 실제 인증 경로로 확인하고, 동시성을 실제 Oracle 로
검증한다. 마지막으로 문서를 갱신한다.

**Files:**
- Test: `src/test/java/com/crosscert/fidoadmin/signup/SignupLoginFlowTest.java`
- Test: `src/test/java/com/crosscert/fidoadmin/integration/SignupIntegrationTest.java`
- Modify: `README.md`
- Modify: `tasks/todo.md`

**Interfaces:**
- Consumes: 앞선 모든 태스크
- Produces: 없음

- [ ] **Step 1: 상태별 로그인 가부 테스트를 작성한다**

`src/test/java/com/crosscert/fidoadmin/signup/SignupLoginFlowTest.java` 생성:

```java
package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 가입 상태가 실제 인증 경로에서 로그인 가부로 이어지는지 확인한다. */
class SignupLoginFlowTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    ManagerUserDetailsService service = new ManagerUserDetailsService(managers, policies, companies);

    private ManagerUserDetails load(String status, Long companyIdx) {
        CcfaManager m = new CcfaManager();
        m.setIdx(5L); m.setUserId("newbie"); m.setUserPw("hash"); m.setUserNm("홍길동");
        m.setStatus(status); m.setCompanyIdx(companyIdx);
        CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행");
        when(managers.findByUserId("newbie")).thenReturn(Optional.of(m));
        when(policies.findFirstByUserIdOrderByIdxDesc(anyString())).thenReturn(Optional.empty());
        when(companies.findById(1L)).thenReturn(Optional.of(c));
        when(companies.findById(-1L)).thenReturn(Optional.empty());
        return (ManagerUserDetails) service.loadUserByUsername("newbie");
    }

    @Test void pendingAccountCannotLogIn() {
        assertThat(load(SignupPolicy.STATUS_PENDING, SignupPolicy.UNASSIGNED_COMPANY_IDX).isEnabled()).isFalse();
    }

    @Test void rejectedAccountCannotLogIn() {
        assertThat(load(SignupPolicy.STATUS_REJECTED, SignupPolicy.UNASSIGNED_COMPANY_IDX).isEnabled()).isFalse();
    }

    @Test void approvedAccountCanLogIn() {
        ManagerUserDetails u = load(SignupPolicy.STATUS_ACTIVE, 1L);
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.getCompanyIdx()).isEqualTo(1L);
    }

    /** 미배정(-1) 계정은 승인 전이므로 SUPER 가 아니다. */
    @Test void unassignedAccountIsNotSuper() {
        assertThat(load(SignupPolicy.STATUS_PENDING, SignupPolicy.UNASSIGNED_COMPANY_IDX).isSuper()).isFalse();
    }
}
```

- [ ] **Step 2: 실행해 통과를 확인한다**

Run: `./gradlew test --tests "*SignupLoginFlowTest*"`
Expected: PASS (4건).

- [ ] **Step 3: 실제 Oracle 에서 잠금·저장 경로 통합 테스트를 작성한다**

**기존 통합 테스트 구조 (반드시 이대로 따를 것):**
- 베이스 클래스는 `com.crosscert.fidoadmin.integration.OracleContainerSupport` 다
  (`AbstractOracleIntegrationTest` 라는 클래스는 **없다**).
- 기존 테스트는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` +
  `@ActiveProfiles("test")` + `@Import(...)` 를 쓴다.
- `@DataJpaTest` 는 **테스트 메서드를 트랜잭션으로 감싸고 롤백**한다. 따라서
  별도 스레드에서 같은 행을 볼 수 없다. **여러 스레드를 띄우는 동시성 테스트는
  이 구조에서 의미가 없으므로 작성하지 않는다.**

대신 **단일 스레드로 검증 가능한 것**을 확인한다: `LOCK TABLE` 이 실제 Oracle 에서
문법 오류·타임아웃 없이 수행되는지, 중복 검사와 상태 전이가 실제 DB 에서
동작하는지다. (이는 기존 `AssignedIdInsertIntegrationTest` 가 `LOCK TABLE` 을
검증하는 방식과 같다.)

`src/test/java/com/crosscert/fidoadmin/integration/SignupIntegrationTest.java` 생성:

```java
package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import com.crosscert.fidoadmin.signup.SignupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 Oracle 에서 가입 저장·승인 경로를 확인한다.
 * 특히 apply() 의 LOCK TABLE 이 문법 오류·타임아웃 없이 수행되는지 본다
 * (H2 로는 검증되지 않는다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SignupService.class)
class SignupIntegrationTest extends OracleContainerSupport {

    @Autowired SignupService signups;
    @Autowired CcfaManagerRepository managers;
    // 승인·거절은 감사 로그를 남기지만, 여기서는 저장 경로만 확인한다.
    @MockitoBean AuditLogger audit;

    @Test void applyPersistsPendingRowWithUnassignedCompany() {
        var saved = signups.apply("intg_newbie", "hash", "통합홍길동",
            "intg@kb.local", "010-1111-2222", "통합 테스트");

        assertThat(saved.getIdx()).isNotNull();          // 시퀀스 채번 확인
        assertThat(saved.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
        assertThat(saved.getCompanyIdx()).isEqualTo(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        assertThat(managers.findByUserId("intg_newbie")).isPresent();
    }

    @Test void applyRejectsDuplicateUserId() {
        signups.apply("intg_dup", "hash", "중복", "dup@kb.local", null, null);

        assertThatThrownBy(() -> signups.apply("intg_dup", "hash", "중복2", "dup2@kb.local", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void approveActivatesAndAssignsCompany() {
        Long idx = signups.apply("intg_ok", "hash", "승인대상", "ok@kb.local", null, null).getIdx();

        var approved = signups.approve(idx, 1L);

        assertThat(approved.getStatus()).isEqualTo(SignupPolicy.STATUS_ACTIVE);
        assertThat(approved.getCompanyIdx()).isEqualTo(1L);
    }

    /** 두 번째 승인은 거부된다(상태 재확인이 실제 DB 에서 동작하는지). */
    @Test void secondApproveIsRejected() {
        Long idx = signups.apply("intg_twice", "hash", "두번", "twice@kb.local", null, null).getIdx();
        signups.approve(idx, 1L);

        assertThatThrownBy(() -> signups.approve(idx, 2L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void approveRejectsSuperCompanyIdx() {
        Long idx = signups.apply("intg_super", "hash", "슈퍼시도", "super@kb.local", null, null).getIdx();

        assertThatThrownBy(() -> signups.approve(idx, 0L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(managers.findById(idx)).get()
            .extracting(m -> m.getStatus()).isEqualTo(SignupPolicy.STATUS_PENDING);
    }
}
```

**시드 데이터 (확인 완료):** `docker/init/02-seed.sql` 에 고객사
`IDX 0 전역(시스템)`, `IDX 1 KB국민은행`, `IDX 2 테스트고객사` 가 있다.
위 테스트가 쓰는 `1L`, `2L` 은 유효하다. 시드 파일은 수정하지 않는다.

- [ ] **Step 4: 통합 테스트를 실행한다**

Run: `./gradlew test --tests "*SignupIntegrationTest*"`
Expected: PASS (5건). Docker 가 떠 있어야 한다.

**동시성에 대한 메모:** 설계서 8장의 동시성 테스트 2건은 `@DataJpaTest` 의
트랜잭션 롤백 구조상 이 프로젝트에서 의미 있게 작성할 수 없다. 대신
`LOCK TABLE` 과 상태 재확인이 실제 Oracle 에서 동작하는 것을 위 테스트로
확인하며, 동시 처리의 최종 방어는 이 두 장치에 의존한다. 이 판단을 태스크
완료 시 `tasks/todo.md` 에 기록한다.

- [ ] **Step 5: 전체 테스트를 실행한다**

Run: `./gradlew test`
Expected: 전부 통과, 실패 0.

- [ ] **Step 6: 수동 확인 (앱 기동)**

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

확인 항목:
1. 로그인 화면에 "가입 신청" 링크가 보인다.
2. `/signup` 이 로그인 없이 열리고, 소속 고객사 선택란이 없다.
3. 신청하면 로그인 화면으로 가고 접수 안내가 보인다.
4. 신청한 아이디로 로그인하면 실패한다.
5. `superuser` 로 로그인하면 사이드바 "운영자" 그룹에 "가입 승인"이 보인다.
6. 승인 화면에서 고객사 선택 목록에 "전역"이 없다.
7. 승인하면 그 계정으로 로그인되고, 우상단에 배정한 고객사가 보인다.
8. `kbadmin` 으로 로그인하면 "가입 승인" 메뉴가 없고 `/signups` 가 403 이다.

- [ ] **Step 7: 문서를 갱신한다**

`README.md` 의 화면 목록에 가입 신청·가입 승인을 추가한다.

`tasks/todo.md` 끝에 추가한다:

```markdown
## 운영자 가입 신청·승인 (2026-09-17)

Spec: docs/superpowers/specs/2026-09-17-fido-admin-signup-design.md
Plan: docs/superpowers/plans/2026-09-17-fido-admin-signup.md

- [ ] Task 1: (선행 수정) COMPANY_IDX null → 슈퍼 관리자 결함 차단
- [ ] Task 2: 비밀번호 정책·상태 상수 공통화
- [ ] Task 3: 가입 신청 서비스
- [ ] Task 4: 가입 신청 화면
- [x] Task 5: 승인·거절 서비스
- [ ] Task 6: 승인 화면
- [ ] Task 7: 인증 흐름 통합 검증과 문서 갱신
```

- [ ] **Step 8: 커밋**

```bash
git add src/test/java/com/crosscert/fidoadmin/signup/SignupLoginFlowTest.java \
        src/test/java/com/crosscert/fidoadmin/integration/SignupIntegrationTest.java \
        README.md tasks/todo.md
git commit -m "test: 가입 상태별 로그인 가부와 동시성 검증

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## 완료 기준

- 전체 테스트 통과, 실패 0.
- 설계서 4장의 권한 상승 차단 3단계가 각각 테스트로 검증된다.
- 설계서 3.3 의 선행 수정이 테스트로 검증된다.
- 승인 전 로그인 불가, 승인 후 로그인 가능이 확인된다.
- 일반 고객사 계정이 `/signups` 에 접근하면 403 이다.
