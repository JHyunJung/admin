# 이상 징후 탐지 삭제 · AAID 정책 추가 · 멤버코드 자동 생성 설계

작성일: 2026-10-05

## 목표

1. 이상 징후 탐지(FDS) 기능을 어드민 코드에서 완전히 걷어낸다.
2. AAID(정책) 화면(`/criteria`)에서 SUPER 가 FIDO UAF 메타데이터 JSON 을 붙여 넣어 새 AAID 정책을 등록할 수 있게 한다.
3. 멤버코드(`/appservers`) 등록 시 멤버코드를 사람이 입력하지 않고 서버가 랜덤으로 만든다.

## 결정 사항 (사용자 확인)

| 항목 | 결정 |
|---|---|
| FDS 삭제 범위 | 코드만. docker 스키마·시드·ERD 기록은 운영 DB 모양 그대로 둔다. 운영 DROP 스크립트 없음 |
| AAID 입력 방식 | 메타데이터 JSON 붙여넣기 → 파싱해 컬럼 채움 |
| AAID 등록 권한 | SUPER 전용 (CRITERIA 는 전역 테이블) |
| 새 AAID 기본 상태 | 모든 고객사에서 비활성 (차단 행 insert) |
| 멤버코드 | 서버 자동 생성, 수정 불가 |

## 1. 이상 징후 탐지 삭제

### 지우는 것

- `MenuRegistry` 의 "이상 징후 탐지" 그룹 두 항목(`/fds-policies`, `/fds-monitor`)
- `company/web`: `FdsPolicyController`, `FdsPolicyForm`, `FdsPolicySearchForm`, `FdsMonitorController`, `FdsMonitorRow`, `FdsMonitorSearchForm`
- `company/service`: `FdsPolicyService`, `FdsMonitorQueryService`
- `company/repository/CcfaFdsPolicyRepository`
- 템플릿 `company/fds-policy/{list,detail,form}.html`, `company/fds-monitor/list.html`
- `common/IpRuleList`, `common/IpRuleListValidator` (FDS 정책 폼만 쓴다)
- 테스트: `FdsPolicyServiceTest`, `FdsMonitorQueryServiceTest`, `FdsPolicyControllerWebTest`, `FdsMonitorControllerWebTest`, `FdsMonitorRowTest`, `FdsPolicyTenantTest`, `IpRuleListValidatorTest`
- `CompanyService.create` 의 FDS 기본 정책 행 생성, `CompanyService.delete` 의 FDS 정책 행 정리, 생성자 의존성
- `CompanyServiceTest`, `LegacyParityIntegrationTest`, `MenuRegistryTest` 의 FDS 관련 단언
- 메뉴 아이콘 CSS 등 FDS 전용 정적 자원이 있으면 함께 제거

### 남기는 것

- 엔티티 `CcfaFdsPolicy`. `ErdConformanceTest` 가 ERD 39개 테이블마다 엔티티를 요구하고, 이번 범위에서 ERD·스키마는 그대로 두기 때문이다. 화면·서비스에서 쓰지 않는 DB 모양 거울로만 남는다(`BakFidoLogsTest` 등과 같은 처지). 클래스 주석에 "어드민에서 쓰지 않음"을 적는다.
- `FidoLogTable` — FIDO 로그 화면이 계속 쓴다. 주석의 FDS 언급만 지운다.
- `docker/init` 의 `CCFA_FDS_POLICY` 테이블·시드, FDS 더미 로그.

### 문서

- `docs/legacy/admin-java-features.md` 의 `FDSController` 행 "새 어드민 대응"을 "없음(2026-10-05 삭제)"로, 고객사 생성·삭제 설명에서 FDS 정책 언급을 정리한다.
- 지난 FDS 스펙·플랜 문서는 기록으로 남긴다.

### 위험

FIDO 서버가 `CCFA_FDS_POLICY` 를 고객사별로 읽는다면, 이후 생성하는 고객사에는 기본행(국가 조건 `NO`)이 없다. 서버 쪽이 행 없음을 견디는지 배포 전 확인한다. 기존 고객사의 행은 그대로 남는다.

## 2. AAID 정책 추가

### 화면

- `fido/criteria/list.html` 상단에 "AAID 추가" 버튼. SUPER 에게만 보인다.
- `GET /criteria/new` → `fido/criteria/form.html`. 메타데이터 JSON textarea 하나와 저장·취소 버튼.
- `POST /criteria` → 성공 시 새 행 상세(`/criteria/{id}`)로 리다이렉트, 실패 시 폼에 오류와 입력값 유지.
- 두 엔드포인트 모두 SUPER 아니면 403. 서비스가 `tenant.require().isSuper()` 로 막는다(`CriteriaQueryService` 는 `requireSuperForGlobalTable()` 을 로그인 확인으로 풀어 두었으므로 기반 검사에 기대지 않고 직접 확인한다).

### 컨트롤러 구조

`CriteriaController` 는 `ReadOnlyController` 를 상속한다. 기반을 `CrudController` 로 바꾸면 수정·삭제 경로가 함께 열리므로 바꾸지 않고, `new`/`create` 두 핸들러만 직접 추가한다. 폼은 `CriteriaMetadataForm`(`@NotBlank String jsondata`).

### 파싱 — `CriteriaMetadataParser`

`fido/service` 에 둔다. 입력은 JSON 원문 문자열, 출력은 저장 전 `Criteria` 엔티티(IDX·시각 제외). Jackson `ObjectMapper` 로 읽는다.

| 메타데이터 필드 | 컬럼 | 규칙 |
|---|---|---|
| `aaid` | `AAID` | 필수, 공백 제거 후 비면 실패 |
| `aaid` 의 `#` 앞 | `VENDORIDS` | `#` 이 없으면 null |
| `userVerificationDetails[][].userVerification` | `USERVERIFICATION` | 모든 값을 비트 OR. 없으면 null |
| `keyProtection` | `KEYPROTECTION` | 숫자 |
| `matcherProtection` | `MATCHERPROTECTION` | 숫자 |
| `attachmentHint` | `ATTACHMENTHNUMBER` | 숫자 |
| `tcDisplay` | `TCDISPLAY` | 숫자 |
| `tcDisplayContentType` | `TCDISPLAYCONTENTTYPE` | 문자열 |
| `authenticationAlgorithms` 배열, 없으면 `authenticationAlgorithm` | `AUTHENTICATIONALGORITHMS` | 쉼표로 이음 |
| `assertionScheme` | `ASSERTIONSCHEMES` | 문자열 |
| `attestationTypes` | `ATTESTATIONTYPES` | 쉼표로 이음 |
| `authenticatorVersion` | `AUTHENTICATORVERSION` | 숫자 |
| 원문 | `JSONDATA` | 입력 그대로 |
| 원문 UTF-8 바이트의 SHA-256 | `METAHASH` | base64url, 패딩 없음 |

`aaid` 외 필드는 없으면 null 로 둔다. 단 JPA insert 는 null 을 명시해 테이블 DEFAULT 가 적용되지 않으므로, DDL 에 기본값이 있는 두 컬럼은 파서가 같은 값을 채운다: `TCDISPLAYCONTENTTYPE` = `text/plain`, `ASSERTIONSCHEMES` = `UAFV1TLV`.

`METAHASH` 알고리즘은 MDS 관례를 따른 선택이다. 이전 어드민의 해시 방식은 소스에서 확인하지 못했다.

### 검증 (폼 오류로 표시)

- JSON 파싱 실패, 최상위가 객체가 아님, `aaid` 없음 → "유효하지 않은 metadata 형식입니다." (이전 어드민 문구)
- 문자열 컬럼이 길이(바이트)를 넘음 → 해당 필드명과 한도를 담은 오류
- 같은 `AAID` 의 CRITERIA 행이 이미 있음 → "이미 등록된 AAID 입니다."

파서는 `CriteriaMetadataException`(메시지 포함)을 던지고, 컨트롤러가 `BindingResult` 의 `jsondata` 오류로 옮긴다.

### 저장 — `CriteriaQueryService.create(String json)`

한 트랜잭션:

1. SUPER 확인 (`tenant.require().isSuper()`, 아니면 403)
2. 파싱, 중복 검사
3. `CRITERIA` insert (`CREATETIME`·`UPDATEDTIME` = now)
4. 모든 고객사에 차단 행 insert:
   ```sql
   INSERT INTO CCFA_COMPANY_AAID (COMPANY_IDX, AAID)
   SELECT c.IDX, :aaid FROM CCFA_COMPANY c
    WHERE NOT EXISTS (SELECT 1 FROM CCFA_COMPANY_AAID b
                       WHERE b.COMPANY_IDX = c.IDX AND b.AAID = :aaid)
   ```
   최상위 고객사(IDX 0)도 포함한다 — `disableAllFor` 와 같이 고객사를 가리지 않는다.
5. 감사 로그 `AuditType.CREATE`: "AAID(정책) 등록 | AAID: … | 차단 고객사 n곳"
6. `FidoConfigChanged("AAID 등록 " + aaid)` 발행 → 커밋 뒤 reload

동시에 같은 AAID 를 두 번 등록하는 경쟁은 CRITERIA 에 유니크 제약이 없어 막지 못한다. SUPER 전용·저빈도 화면이라 애플리케이션 중복 검사로 충분하다고 보고 잠금은 두지 않는다.

### 범위 밖

AAID 수정·삭제, 메타데이터 파일 업로드, MDS 자동 수집.

## 3. 멤버코드 자동 생성

### 동작

- 등록 폼(`fido/appserver/form.html`, `isNew`): 멤버코드 입력란을 없애고 "저장 시 자동 생성됩니다" 안내를 둔다.
- 수정 폼: 멤버코드를 읽기 전용 텍스트로 보여 준다.
- `AppserverForm.memberCode` 의 `@NotBlank` 를 없애고, `applyTo` 가 멤버코드를 건드리지 않게 한다. 폼으로 들어온 값은 등록·수정 모두 무시된다.
- `AppserverService.applyDefaults` 에서 `memberCode` 가 비어 있으면 `MemberCodeGenerator.generate()` 로 채운다. `applyDefaults` 는 `CrudService.create` 에서만 불리므로 수정 경로에는 영향이 없다.

### 생성기 — `MemberCodeGenerator`

- `fido/service` 의 컴포넌트. `SecureRandom`, 문자 집합 `A-Z0-9`, 길이 10.
- 생성 후 `AppserverRepository.existsByMemberCode` 로 전체 중복을 보고, 겹치면 다시 뽑는다. 최대 5회, 그래도 겹치면 `IllegalStateException`.
- 테스트에서 난수를 고정할 수 있게 `RandomGenerator` 를 생성자로 받는다.

### 중복 검사

`AppserverController.validate` 의 (멤버코드, 멤버키) 중복 검사는 수정 때만 돈다. 등록 때는 멤버코드가 아직 없고, 생성기가 전체 유일성을 보장한다.

감사 로그·reload 는 기존 `AppserverService` 흐름을 그대로 탄다.

## 테스트

- `CriteriaMetadataParserTest`: 전체 필드 정상 매핑, userVerification 비트 OR, `authenticationAlgorithm` 단수 폴백, `aaid` 누락, 잘못된 JSON, 배열 최상위, 길이 초과, METAHASH 값
- `CriteriaQueryService` 등록 테스트: 중복 거부, 모든 고객사 차단 행 생성, COMPANY 역할 403, 이벤트 발행
- `CriteriaController` 웹 테스트: SUPER 폼 접근·저장 리다이렉트, COMPANY 403, 오류 시 폼 재표시, 목록의 버튼 노출 조건
- `MemberCodeGeneratorTest`: 형식, 충돌 시 재시도, 5회 실패 시 예외
- `AppserverController` 웹 테스트: 등록 시 입력한 멤버코드가 무시되고 생성값이 저장됨, 수정 시 멤버코드 불변
- FDS 삭제 후 `/fds-policies`, `/fds-monitor` 404, 고객사 생성 시 `CCFA_FDS_POLICY` 행이 생기지 않음
- 마지막에 `./gradlew test` 전체
