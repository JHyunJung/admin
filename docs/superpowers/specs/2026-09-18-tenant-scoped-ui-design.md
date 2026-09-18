# 테넌트 선택 기반 UI/UX 설계

날짜: 2026-09-18
대상: 기존 FIDO Admin (설계서 `2026-09-16-fido-admin-design.md` 3.3 테넌트 격리를 대체한다)

## 1. 문제

멀티테넌트 시스템인데 슈퍼관리자 화면이 전체 고객사 데이터를 한 번에 보여준다.

`CrudService.search()` 는 테넌트 필터를 이렇게 정한다.

    Long filter = TenantContext.isSuper() ? form.getCompanyIdx() : TenantContext.companyIdx();

SUPER 는 폼의 `companyIdx` 를 쓰는데 이 값은 보통 비어 있고, `Specs.eq(attr, null)` 은
술어를 생략한다. 결과는 전체 조회다. 고객사를 고르려면 화면마다 있는 검색폼의
select 를 써야 하고, 그 선택은 화면을 옮기면 사라진다.

더 큰 문제는 목록이 아니라 단건 접근이다. `CrudService.checkTenant()` 는
`TenantContext.isSuper()` 면 검사 없이 통과한다. 즉 SUPER 는 `/users/12345` 를
직접 입력해 임의 테넌트의 행을 열고 수정·삭제할 수 있다. 목록에서 고객사를 골라도
이 경로는 막히지 않는다.

**목표**: 슈퍼관리자가 한 번에 하나의 고객사만 보게 한다. 선택한 고객사가
조회·상세·등록·수정·삭제 전부의 경계가 된다.

## 2. 핵심 개념 — 유효 테넌트

`TenantContext.companyIdx()` 의 의미를 **"로그인한 사람의 소속"** 에서
**"지금 보고 있는 테넌트"** 로 바꾼다.

| 역할 | 유효 테넌트 |
|---|---|
| COMPANY | 항상 자기 `COMPANY_IDX`. 세션 선택값은 읽지 않는다. |
| SUPER | 세션에 선택된 `COMPANY_IDX`. 미선택이면 값이 없다. |

미선택은 `null` 이 아니라 **`NoTenantSelectedException`** 으로 표현한다.

`null` 로 두면 `Specs.eq(attr, null)` 이 술어를 생략해 지금과 똑같은 전체 조회가
된다. 즉 누락 하나가 조용히 격리 해제로 이어진다. 예외로 두면 누락이
선택 화면 리다이렉트로 드러난다. 값이 없는 상태를 표현할 수 없게 만드는 것이
이 설계의 안전성의 뿌리다.

### 2.1 TenantContext 를 빈으로 바꾼다

`TenantContext` 는 정적 유틸(`SecurityContextHolder` 직접 참조)이라 세션 빈에
접근할 수 없다. 스프링 빈으로 전환한다.

    @Component
    @RequiredArgsConstructor
    public class TenantContext {
        private final SelectedTenant selected;   // @SessionScope

        public Optional<ManagerUserDetails> current() { ... }   // 기존과 동일
        public ManagerUserDetails require() { ... }             // 기존과 동일

        /** 유효 테넌트. 없으면 NoTenantSelectedException. */
        public Long companyIdx() {
            ManagerUserDetails user = require();
            if (!user.isSuper()) return user.getCompanyIdx();
            return selected.companyIdx()
                .orElseThrow(NoTenantSelectedException::new);
        }

        /** 선택 여부만 묻는다. 인터셉터와 레이아웃이 쓴다. */
        public boolean hasTenant() { ... }
    }

`TenantContext.isSuper()` 는 **삭제한다.** "지금 보는 테넌트가 전역인가"라는 질문이
새 모델에서 성립하지 않기 때문이다. 계정이 슈퍼관리자인지 묻는 자리는
`ManagerUserDetails.isSuper()` 를 직접 쓴다 — 이쪽은 의미가 그대로다.

호출부는 `src/main` 에서 38곳 / 22개 파일이고, 테스트 2개 파일이 더 있다.
`AuditLogger`, `CompanyLookup`, `ManagerService`,
`StatisticsQueryService`, 컨트롤러 16개는 이미 빈이라 생성자 주입으로 받는다.
`CrudService` 는 추상 클래스이므로 생성자 파라미터에 더한다(하위 28개 서비스의
생성자가 함께 바뀐다 — 기계적이지만 양이 있다).

### 2.2 SelectedTenant

    @Component
    @SessionScope
    public class SelectedTenant {
        private Long companyIdx;
        public Optional<Long> companyIdx() { return Optional.ofNullable(companyIdx); }
        public void select(Long idx) { ... }
        public void clear() { ... }
    }

`select()` 는 **존재하는 고객사이고 `IDX != 0`** 일 때만 받는다. 0 은 SUPER 를
뜻하므로 테넌트로 선택할 수 없다(`SignupPolicy.SUPER_COMPANY_IDX` 와 같은 규칙).
검증은 이 빈이 아니라 이를 호출하는 `TenantSelectionController` 가 리포지터리로
직접 한다 — `CompanyLookup` 은 화면용 가림 로직이 있어 존재 판정에 쓸 수 없다
(`SignupService.approve()` 가 같은 이유로 리포지터리를 직접 쓴다).

세션 고정 공격은 기존 `sessionFixation().migrateSession()` 이 막고, 로그아웃의
`invalidateHttpSession(true)` 가 선택을 지운다. 추가 조치는 필요 없다.

## 3. 화면을 두 영역으로 나눈다

`MenuItem.superOnly` 는 지금 두 가지를 뭉뚱그린다: "SUPER 만 보는 화면"과
"COMPANY_IDX 컬럼이 없는 전역 테이블". 새 모델에서 이 둘은 갈라진다.

`MenuItem` 에 `MenuArea` 를 더한다.

    public enum MenuArea { TENANT, SYSTEM, PERSONAL }
    public record MenuItem(MenuArea area, String group, String title,
                           String href, boolean superOnly, String icon) {}

`superOnly` 는 **남긴다.** 영역과 직교하는 성질이기 때문이다 — 라이선스·운영자는
`TENANT` 이면서 `superOnly` 다.

### 3.1 영역 배정

기존 메뉴 30개가 16(TENANT) + 13(SYSTEM) + 1(PERSONAL) 로 갈리고,
여기에 4장의 `/managers/super` 가 SYSTEM 에 더해져 31개가 된다.

**TENANT (16개 화면)** — 테넌트 데이터를 다루는 화면. 15개는
`companyIdxAttribute()` 가 값을 돌려주는 서비스이고, 대시보드는 `CrudService` 를
타지 않는 생 SQL 이라 따로 처리한다(5.2). 선택된 고객사가 없으면 열리지 않는다.

| 화면 | 경로 | 속성 | superOnly |
|---|---|---|---|
| 대시보드 | `/` | (생 SQL) | 아니오 |
| FDS 정책 | `/fds-policies` | `companyIdx` | 아니오 |
| 라이선스 | `/licenses` | `companyIdx` | **예** |
| 운영자 | `/managers` | `companyIdx` | **예** |
| 앱 ID | `/appids` | `companyIdx` | 아니오 |
| 앱 서버 | `/appservers` | `companyIdx` | 아니오 |
| 사용자 | `/users` | `companyIdx` | 아니오 |
| 챌린지 | `/challenges` | `companyIdx` | 아니오 |
| 서명 | `/signs` | `companyIdx` | 아니오 |
| 거래 해시 | `/transaction-hashes` | `companyIdx` | 아니오 |
| 거래 확인 | `/transaction-confirmations` | `companyIdx` | 아니오 |
| FIDO 로그 | `/logs/fido` | `companyIdx` | 아니오 |
| 감사 로그 | `/logs/audit` | `companyIdx` | 아니오 |
| 예외 로그 | `/logs/exceptions` | `companyIdx` | 아니오 |
| 메일/SMS 큐 | `/logs/mailing` | `companyIdx` | 아니오 |
| 시스템 설정 | `/system/props` | `id.companyIdx` | **예** |

**SYSTEM (기존 13개 + 신설 1개)** — `companyIdxAttribute()` 가 `null` 인 전역
테이블과, 테넌트에 속하지 않는 워크플로. 선택과 무관하게 항상 열린다. 전부 `superOnly`.

`/companies`, `/signups`, `/managers/super`(신설), `/criteria`,
`/fido2/metadata`, `/fido2/credential-params`, `/fido2/demo-access-codes`,
`/system/info`, `/system/error-codes`, `/system/fido-clients`,
`/system/criteria`, `/system/menus`, `/system/options`, `/system/fields`

**PERSONAL (1개)** — `/me/password`. 선택과 무관.

### 3.2 판단이 필요했던 배정 셋

- **라이선스·운영자·시스템 설정은 SUPER 전용이지만 TENANT** 다. 실제 테넌트 데이터를
  담기 때문이다. 지금은 SUPER 가 전체를 보지만 앞으로는 선택한 고객사 것만 본다.
- **가입 승인(`/signups`)은 SYSTEM** 이다. 승인 대기 계정은 `COMPANY_IDX = -1`
  (미배정)이라 어느 테넌트에도 속하지 않는다. TENANT 에 두면 어떤 고객사를 골라도
  목록이 비어 버린다.
- **`/system/props` 는 `id.companyIdx`(EmbeddedId)** 다. `Specs.path()` 가 점 경로를
  이미 해석하므로 코드 변경 없이 같은 규칙이 걸린다.

## 4. 슈퍼관리자 계정 화면 (신설)

SUPER 계정은 `COMPANY_IDX = 0` 이고 0 은 선택 목록에 없다. 그래서 **어떤 고객사를
골라도 슈퍼관리자 계정 자체가 운영자 목록에 나오지 않는다.** 지금은 전체 조회라
보였으므로 이것은 이번 변경이 만드는 회귀다. 그대로 두면 슈퍼관리자 계정을
관리할 화면이 사라진다.

**`/managers/super`** 를 SYSTEM 영역에 신설한다.

- `COMPANY_IDX = 0` 인 `CCFA_MANAGER` 행만 다룬다.
- 기능은 `/managers` 와 같다: 목록·상세·등록·수정·삭제·잠금 해제.
- `ManagerService` 를 재사용하되 유효 테넌트가 아니라 **상수 0** 으로 필터한다.
  `SuperManagerService` 가 `CrudService` 를 상속하지 않고 `ManagerService` 를
  위임받는 얇은 서비스로 두어, 테넌트 필터 경로와 섞이지 않게 한다.
- 자기 자신 삭제 금지(`beforeDelete`)와 가입 상태 변경 금지(`update`)는 그대로 적용된다.
- 사이드바 "시스템" 그룹에 `슈퍼관리자 계정` 으로 놓는다.

**권한 상승 경로 점검**: 이 화면은 `COMPANY_IDX = 0` 계정을 만들 수 있으므로
SUPER 를 만들 수 있는 유일한 화면이 된다. 기존 규칙과의 관계는 이렇다.

- `/signup`(공개 가입)은 `COMPANY_IDX = -1` 고정 — 변화 없다.
- `SignupService.approve()` 는 `IDX 0` 배정을 거부 — 변화 없다.
- `/managers`(테넌트)는 유효 테넌트로 강제 덮어쓰므로 0 을 쓸 수 없다.
- `/managers/super` 만 0 을 쓴다. SUPER 전용 URL 이고 감사 로그가 남는다.

즉 승인 경로로는 여전히 SUPER 를 만들 수 없고, 슈퍼관리자가 명시적으로 이 화면에서만
만들 수 있다. 이것은 지금보다 경로가 **좁아지는** 변화다.

## 5. 격리 강제

### 5.1 CrudService — 역할 분기가 사라진다

    // 지금
    Long filter = TenantContext.isSuper() ? form.getCompanyIdx() : TenantContext.companyIdx();
    // 바뀐 뒤
    spec = Specs.all(spec, Specs.eq(attr, tenant.companyIdx()));

`checkTenant()` 의 `if (TenantContext.isSuper()) return;` 을 **삭제한다.** 이것이
2장에서 말한 단건 접근 구멍을 막는 지점이다. 선택한 고객사와 다른 행을 열면
`TenantMismatchException` → 404(존재를 숨긴다, 기존 규칙 유지).

`create()`/`update()` 의 `if (!TenantContext.isSuper()) setCompanyIdx(...)` 는
**조건 없는 덮어쓰기** 가 된다. 선택한 고객사에서 만든 데이터는 그 고객사 소유다.

`requireSuperForGlobalTable()` 은 그대로 둔다. SYSTEM 영역 테이블의 서비스 계층
방어선이고 영역 구분과 목적이 같다.

### 5.2 CrudService 를 타지 않는 경로

- **대시보드** — `StatisticsQueryService` 의 `isSuper() ? null : ...` 세 곳
  (L35, L46, L54)을 `tenant.companyIdx()` 로 바꾼다. `(:companyIdx IS NULL OR ...)`
  SQL 은 그대로 두되 `null` 이 들어갈 일이 없어진다.
- **`CompanyLookup`** — `all()` 은 상단 선택기와 선택 화면 전용이 된다. SUPER 는
  전체, COMPANY 는 자기 1건이라는 규칙은 그대로다(선택 목록의 출처이므로 유효
  테넌트를 보면 안 된다 — 자기 자신을 참조하게 된다). `name(idx)` 는 표시용으로 남는다.
- **`SignupAdminController`** — 승인 드롭다운이 `CompanyLookup.all()` 을 쓴다.
  SYSTEM 영역이므로 선택과 무관하게 전체 목록이 필요하다. `all()` 의 의미를
  바꾸지 않으므로 이 화면은 변경 없이 동작한다.

### 5.3 인터셉터

`TenantSelectionInterceptor` 를 `WebMvcConfig.addInterceptors()` 에 등록한다
(현재 이 override 가 없으므로 신설).

- 요청 경로가 TENANT 영역이고 `!tenant.hasTenant()` 이면 `/select-tenant` 로 리다이렉트.
- SYSTEM·PERSONAL·`/login`·`/signup`·정적 리소스는 통과.
- 경로→영역 판정은 `MenuRegistry` 를 단일 출처로 삼는다. 메뉴에 없는 하위 경로
  (`/users/123/edit`)는 가장 긴 접두사 일치로 판정한다.

`SecurityConfig` 의 URL 규칙은 **건드리지 않는다.** 역할 검사와 테넌트 선택 검사는
다른 관심사이고, 지금의 `hasRole("SUPER")` 목록은 그대로 유효하다.

`NoTenantSelectedException` 이 인터셉터를 빠져나와 서비스까지 도달하는 경우
(인터셉터가 못 잡은 경로)는 `GlobalExceptionHandler` 가 `/select-tenant` 리다이렉트로
처리한다. 이중 방어이며, 여기 걸리는 경로가 있다면 인터셉터의 누락이므로 로그를 남긴다.

## 6. UI

### 6.1 상단 선택기 (`layout/base.html`)

현재 "이름 / 고객사 / SUPER 뱃지" 자리를 바꾼다.

    FIDO Admin          [🏢 국민은행 ▾]   홍길동  SUPER  [로그아웃]
                         ├ 국민은행
                         ├ 신한은행
                         └ ⚙ 고객사 선택 화면으로

- SUPER + TENANT 영역: 드롭다운.
- SUPER + SYSTEM 영역: `⚙ 시스템 관리` 뱃지(드롭다운 없음). 지금이 테넌트 맥락이
  아님을 드러낸다.
- COMPANY: 지금처럼 고객사 이름 정적 텍스트.

전환은 `POST /select-tenant` (CSRF 토큰 포함, GET 으로 상태를 바꾸지 않는다) →
세션 갱신 → 리다이렉트. 돌아갈 곳은 이렇게 정한다.

- 목록 화면에 있었으면 그 화면으로.
- **상세·수정 화면(`/users/123`)에 있었으면 해당 목록으로.** 그 ID 는 이전 테넌트의
  것이라 새 테넌트에서는 404 가 되기 때문이다.

판정은 인터셉터가 아는 경로 패턴으로 한다(메뉴 경로와 정확히 일치하면 목록,
그 하위면 목록으로 절상).

### 6.2 고객사 선택 화면 (`/select-tenant`, 신설)

로그인 직후 SUPER 가 처음 만나는 화면.

- 고객사를 카드로 배치. 이름 검색 입력.
- 각 카드에 고객사명과 IDX.
- 하단에 시스템 영역 메뉴 링크(고객사 관리, 가입 승인, 슈퍼관리자 계정 등).
- COMPANY 계정이 이 URL 에 오면 자기 대시보드로 리다이렉트(선택할 것이 없다).

### 6.3 사이드바 (`fragments/sidebar.html`)

- 머리에 선택된 고객사 이름.
- 그 아래 TENANT 그룹들(대시보드/고객사/운영자/FIDO/로그 중 테넌트 항목).
- 구분선 아래 SYSTEM 그룹.
- 미선택 상태에서는 TENANT 그룹 전체를 흐리게 하고 링크를 죽인다.

### 6.4 제거되는 것

| 대상 | 수 | 위치 |
|---|---|---|
| 검색폼 고객사 `<select>` | 16 | `*/list.html` |
| 폼 고객사 `<select>` | 6 | `appid/form`, `appserver/form`, `manager/form`, `license/form`, `fds-policy/form`, `props/form` |
| 목록 표의 고객사 컬럼 | 16 | `*/list.html` |
| 컨트롤러의 `if (isSuper()) model.addAttribute("companies", ...)` | 17개 호출부 / 14개 컨트롤러 | 컨트롤러 |
| 조건 없는 `model.addAttribute("companies", companies.all())` | 5 (`ManagerController` ×2, `SystemPropController` ×2, `LicenseController` ×1) | 컨트롤러 |

`SignupAdminController` 의 `companies` 도 조건 없는 호출부지만 **남긴다**
(5.2 — 승인 시 소속 배정용이며 SYSTEM 영역이다). 조건 없는 호출부 6곳 중 5곳만
지운다.
| `SearchForm.companyIdx` 필드 + `toQueryString()` 항목 | 1 | `common/SearchForm.java` |

`signup/list.html` 의 고객사 select 는 **남긴다** (5.2 참조 — 승인 시 소속을
배정하는 용도이고 SYSTEM 영역이다).

### 6.5 FDS 정책의 예외 — companyIdx 가 PK 다

`CCFA_FDS_POLICY` 는 `companyIdx` 가 **할당형 PK** 다(고객사당 1건). 다른 화면처럼
select 만 지우면 식별자를 채울 길이 사라진다. 이 화면만 다음과 같이 다룬다.

- 등록 폼의 고객사 select 를 지우되, `FdsPolicyController.toEntity()` 가
  `f.getCompanyIdx()` 대신 **유효 테넌트** 로 PK 를 채운다.
- `validate()` 의 `isNew && isSuper() && companyIdx == null` 거부 규칙은 삭제한다.
  유효 테넌트는 항상 값이 있으므로 이 상태가 성립하지 않는다.
- 수정 화면의 읽기 전용 표시와 hidden 필드는 그대로 둔다(식별자는 바꿀 수 없다).
- `AssignedIdCrudService.insert()` 의 존재 검사가 그대로 동작하므로, 이미 정책이
  있는 고객사를 선택한 채 등록하면 기존과 같은 중복 거부가 난다.

같은 성질의 화면이 하나 더 있다. `/system/props` 는 `PROP_KEY + COMPANY_IDX`
복합키이고, **식별자가 URL 경로에 그대로 들어간다**(`/system/props/{key}@{companyIdx}`).

- 등록 시 `SystemPropForm.toNewEntity()` 가 `companyIdx` 를 유효 테넌트로 채운다
  (`props/form.html` 의 select 제거는 다른 화면과 동일).
- 상세·수정·삭제 URL 에 다른 테넌트의 `companyIdx` 를 넣어도 `checkTenant()` 가
  막는다. 지금은 SUPER 면 그냥 통과하므로, 이 화면은 URL 조작으로 임의 테넌트의
  시스템 설정을 바꿀 수 있는 상태다. 5.1 의 `isSuper()` 분기 삭제가 이것도 닫는다.
- 8장 테스트 2·3번에 이 경로(`{key}@{다른 테넌트}`)를 포함한다.

**남기는 것**: 상세 화면의 `고객사 (IDX)` 표시. 그 행이 실제로 어디 소속인지는
여전히 사실 정보이고 데이터 확인에 쓰인다.

## 7. 감사 로그

`AuditLogger.log()` 는 지금 행위자의 `COMPANY_IDX` 를 기록한다. SUPER 의 모든 행위가
`COMPANY_IDX = 0` 으로 뭉친다.

**대상 테넌트를 기록하도록 바꾼다.** `CCFA_AUDIT_LOG` 스키마는 바꿀 수 없으므로
기존 `COMPANY_IDX`/`COMPANY_NAME` 컬럼에 유효 테넌트를 넣고, 행위자는 기존
`USER_ID`/`USER_NAME` 으로 식별한다. 이것은 지금보다 나아지는 변화다 —
"슈퍼관리자가 어느 고객사 데이터를 만졌는가"가 처음으로 남는다.

선택이 없는 상태(SYSTEM 영역)의 기록은 행위자의 `COMPANY_IDX`(=0)를 그대로 쓴다.
전역 작업이므로 사실에 맞다.

**무결성 해시**: `integrityHash()` 는 저장되는 값으로부터 계산되므로 넣는 값이
바뀌어도 재계산·검증이 성립한다. **기존 행의 검증도 깨지지 않는다** — 해시 입력
필드 목록과 인코딩 규칙을 바꾸지 않기 때문이다.

## 8. 테스트

기존 96개 테스트 파일 중 13개가 "SUPER" 를 참조하며, 그중 일부는
**"SUPER 는 전체를 본다"를 검증하므로 기대값이 뒤집힌다.**

가장 직접적인 곳: `common/CrudServiceTest.java:143` —
`assertThat(companyIdxEqualsIn(captor.getValue())).isNull()` (SUPER 필터 없음).
이 단언이 "선택된 테넌트로 필터된다"로 바뀐다.

함께 볼 것: `TenantContextTest`, `LayoutWebTest`, `MenuRegistryTest`,
`dashboard/StatisticsTenantScopeTest`, `FdsPolicyControllerWebTest`,
그리고 컨트롤러별 `*WebTest` 의 SUPER 시나리오.

### 새로 넣을 것

1. 미선택 SUPER 가 TENANT URL 에 가면 `/select-tenant` 로 리다이렉트한다.
2. **A 를 선택한 SUPER 가 B 의 행을 `GET /users/{id}` 로 열면 404.**
   (지금은 200 — 이번 변경이 막는 구멍이므로 가장 중요한 테스트다.)
3. 같은 조건에서 `POST` 수정·삭제도 404.
4. 선택을 전환하면 목록 결과가 실제로 바뀐다.
5. SYSTEM 영역은 미선택 상태에서도 200.
6. `/managers` 목록에 `COMPANY_IDX = 0` 계정이 나오지 않고, `/managers/super` 에는
   그 계정만 나온다.
7. `/managers/super` 외의 경로로 `COMPANY_IDX = 0` 계정을 만들 수 없다.
8. 감사 로그의 `COMPANY_IDX` 가 행위자가 아니라 대상 테넌트다.
9. **COMPANY 계정의 동작이 전혀 바뀌지 않는다** (회귀 방지). 기존 COMPANY 시나리오
   테스트가 수정 없이 통과해야 한다.

### 검증 규칙

`tasks/lessons.md` 의 규칙을 따른다 — "통과했다"는 실행된 테스트에 대해서만 참이다.
커밋 전 `grep -ho 'skipped="[0-9]*"' build/test-results/test/*.xml` 로
skipped 합계가 0인지 확인한다.

## 9. 리스크

| 리스크 | 완화 |
|---|---|
| `TenantContext` 정적→빈 전환이 38곳/22파일 + 테스트에 파급 | 기계적 변경. 컴파일러가 누락을 전부 잡는다. 의미가 바뀌는 곳은 `isSuper()` 삭제로 강제 검토된다 |
| 슈퍼관리자 계정이 운영자 목록에서 사라짐 | 4장 `/managers/super` 신설 |
| 세션 기반이라 URL 공유 시 보는 사람마다 다른 데이터 | 선택 방식으로 이미 감수한 트레이드오프. 완화로 상세 화면 상단에 "국민은행의 데이터를 보고 있습니다" 문맥 배너 |
| 기존 테스트 기대값 반전을 "고쳐서 통과시키는" 유혹 | 반전되는 단언은 전부 **의도된 동작 변경** 임을 커밋 메시지에 명시하고, 9번(COMPANY 회귀 없음) 테스트로 반대편을 고정한다 |
| 인터셉터 경로 판정 누락 | `GlobalExceptionHandler` 이중 방어 + 누락 시 로그 |

## 10. 범위 밖

- URL 경로에 테넌트를 넣는 방식(`/t/{idx}/users`) — 세션 방식을 선택했다.
- 마지막 선택 기억(영속) — 스키마를 바꾸지 않는 것이 프로젝트 제약이다.
- 고객사 간 비교·전수 검색 화면 — 단일 테넌트 모델을 선택하며 포기했다.
  필요해지면 SYSTEM 영역의 별도 화면으로 설계한다.
- `CCFA_AUDIT_LOG` 스키마 변경(행위자 소속과 대상 테넌트를 각각 저장) — 7장의
  컬럼 재해석으로 대신한다.
