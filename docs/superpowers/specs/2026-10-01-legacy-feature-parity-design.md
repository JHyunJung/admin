# 이전 어드민 기능 보강 설계

날짜: 2026-10-01
대상: 기존 FIDO Admin. 이전 어드민(Eclipse `fidoadmin`, Spring MVC + MyBatis)에 있었지만 지금 없는 기능 중
운영에 필요한 것을 옮긴다.
근거: 이전 어드민 Java 소스 화면 녹화(`IMG_9165.MOV`, 11분)를 3초 간격 프레임 225장으로 전사한 기록.
전사 요약은 이 작업에서 `docs/legacy/admin-java-features.md` 로 남긴다(§8).

## 1. 문제

화면 단위로는 이전 어드민이 거의 다 옮겨져 있다(고객사·운영자·라이선스·FDS·AAID 토글·AppID·멤버코드·
FIDO 로그·감사 로그·계정 잠금·FIDO 서버 자가등록). 빠진 것은 화면 뒤의 동작이다.

| # | 이전 어드민 동작 | 현재 | 이번 결정 |
|---|---|---|---|
| 1 | 설정 변경 시 등록된 모든 FIDO 서버에 `GET {SERVERURL}/api/command/reload` (`CCFIDOClientInfo.sendSignal`) | 없음. 어드민에서 바꿔도 FIDO 서버가 모른다 | 구현 (§2) |
| 2 | FDS 정책 IP 목록 형식 검사(`FDSPolicyValidator.checkIp`) | 길이만 검사 | 구현 (§3.1) |
| 3 | 멤버코드 `MEMBER_CODE`+`MEMBER_ID` 중복 검사 | 없음 | 구현 (§3.2) |
| 4 | 비밀번호 대문자·소문자·숫자·특수문자 모두 필수 | 영문·숫자·특수문자 | 구현 (§3.3) |
| 5 | 비밀번호 90일 만료(`LAST_PW_CHANGE_DATE`) | 없음 | **제외** — 운영 DB 에 열이 없고 스키마 변경 불가 |
| 7 | 고객사 생성 시 AAID 전체 차단(`disableCompanyAllAAID`) + FDS 정책 기본행(`fds.insert`), 삭제 시 정리 | 설정 복사·삭제만 | 구현 (§3.4) |
| 8 | 외부 라이선스 조회 `/external/license/{filename}` (FIDO 서버가 사용) | 없음 | 구현 (§3.5) |

옮기지 않는 것은 §7 에 적는다.

## 2. FIDO 서버 reload 신호

### 2.1 이전 어드민

```java
// CCFIDOClientInfo.sendSignal(command)
WzURL url = new WzURL(getServerUrl() + "/api/command/" + command);   // GET, 응답 본문 문자열
```

`sendAllSignal` 이 `clientInfo`(= `CCFA_FIDOCLIENT`) 전 행에 `"reload"` 를 보낸다. 호출 시점은
AppID·멤버코드·AAID 메타데이터 등록/수정이고, **insert/update 보다 먼저** 부른다. FIDO 서버가 커밋 전
값을 다시 읽을 수 있는 경쟁 조건이다. 예외는 `printStackTrace` 뿐이다.
`SystemController` 에는 서버 목록을 보여 주며 reload 를 보내는 수동 화면도 있었다.

### 2.2 결정

**커밋 후 비동기 자동 전송 + 동기 수동 전송.**

- 저장은 FIDO 서버 응답을 기다리지 않는다. 느린 서버가 있어도 저장이 늦어지지 않는다.
- 커밋 뒤에만 보낸다. 롤백된 변경은 보내지 않는다.
- 자동 전송 실패는 화면에 바로 보이지 않는다. 대신 WARN 로그로 남고, 운영자는 FIDO 서버 목록의
  수동 버튼으로 결과를 보며 다시 보낼 수 있다.

대상은 `STATUS='ON'` 행만이다. 현재 자가등록 해제는 행을 지우지 않고 `OFF` 로 내리므로(이전 어드민은 행 삭제),
`ON` 만 보내는 것이 이전 동작과 같다.

### 2.3 구성 (패키지 `system.reload`)

**`FidoReloadClient`**
- `List<ReloadResult> reloadAll()` — `CCFA_FIDOCLIENT` 에서 `STATUS='ON'` 행을 읽어 서버마다 한 번씩 보낸다.
  한 서버의 실패가 다음 서버 전송을 막지 않는다.
- URL: `SERVERURL` 끝의 `/` 를 떼고 `/api/command/reload` 를 붙인다. 메서드 GET.
- 스킴이 `http`/`https` 가 아니면 보내지 않고 실패 결과로 남긴다. 리다이렉트는 따라가지 않는다.
  서버 목록은 인증 없는 자가등록 API(`/api/svc/reg`)로 채워지기 때문이다.
- 연결 제한 2초, 응답 제한 5초. HTTP 2xx 면 성공, 그 밖의 상태·예외는 실패.
- `ReloadResult(String servercode, String serverurl, boolean ok, String detail, long elapsedMs)` —
  `detail` 은 성공 시 HTTP 상태, 실패 시 상태 또는 예외 메시지(한 줄, 200자 이내).

**`FidoConfigChanged(String reason)`** — 이벤트 레코드. `reason` 은 로그용 한 줄(예: `"APPID 수정 12"`).

**`FidoReloadListener`**
- `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` — 트랜잭션 밖에서 발행해도 실행.
- 전용 실행기 `fidoReloadExecutor`(스레드 1, 대기열 50)에서 `reloadAll()` 을 돈다. 대기열이 차면
  WARN 한 줄을 남기고 버린다. reload 는 멱등이므로 다음 변경이나 수동 전송이 대신한다.
- 서버별 결과를 로그로 남긴다. 성공은 INFO, 실패는 WARN(`reason`, 서버 코드, URL, `detail`).
- 감사 로그는 남기지 않는다. 비동기 스레드에는 로그인 사용자가 없고, 원인이 된 변경은 이미 감사 로그에 있다.

**수동 전송**
- `POST /system/fido-clients/reload` (SUPER, `/system/**` 규칙에 이미 포함). 동기로 `reloadAll()`.
- 결과를 완료 메시지로 돌려준다. 모두 성공이면 `flashSuccess` "FIDO 서버 n대에 reload 를 보냈습니다.",
  하나라도 실패면 `flashError` 에 실패 서버별 `servercode: detail` 목록. `ON` 서버가 없으면 `flashError`
  "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다."
- 감사 로그: `AuditType.UPDATE`, `"FIDO 서버 reload 수동 전송 | 성공 n / 실패 m"`.
- FIDO 서버 목록 화면 상단에 버튼(POST 폼, CSRF 토큰 포함)을 둔다.

### 2.4 발행 지점

모두 해당 변경의 트랜잭션 안에서 `ApplicationEventPublisher.publishEvent` 한다.

| 변경 | 위치 | 이전 어드민 |
|---|---|---|
| AppID 등록·수정·삭제 | `AppidService` | 등록·수정만 |
| 멤버코드 등록·수정·삭제 | `AppserverService` | 등록·수정만 |
| FIDO2 메타데이터 등록·수정·삭제 | `Fido2MetadataService` | 메타데이터 등록·수정 |
| 크리덴셜 파라미터 등록·수정·삭제 | `Fido2CredentialParamsService` | 확인 안 됨 |
| AAID 단건·전체 켜기/끄기 | `CriteriaQueryService` | 확인 안 됨 |
| FIDO 서버 설정 저장 | `FidoSettingService` | 호출이 주석 처리돼 있었음 |
| 고객사 생성(§3.4 로 AAID 차단이 생김) | `CompanyService` | — |

이전 어드민에 없던 지점을 더하는 이유: 모두 FIDO 서버가 읽는 데이터이고, reload 는 여러 번 보내도 결과가 같다.

`CrudService` 기반 서비스(AppID·멤버코드·FIDO2 두 개)는 `create`/`update`/`delete` 를 재정의해 `super` 를
부른 뒤 발행한다. `CrudService` 생성자는 바꾸지 않는다(다른 서비스 수십 개에 영향).
실제로 바뀐 것이 없을 때(AAID 토글이 "이미 해당 상태", 전체 토글 변경 0건)는 발행하지 않는다.

## 3. 작은 보강

### 3.1 FDS 정책 IP 형식

- 새 제약 어노테이션 `@IpRuleList`(검증기 `IpRuleListValidator`)를 `FdsPolicyForm.andIp`, `orIp` 에 붙인다.
- 빈 값은 통과. 공백을 모두 지우고 쉼표로 나눈 각 항목이 다음 중 하나여야 한다.
  - 단일 IPv4 `a.b.c.d` (각 옥텟 0~255)
  - CIDR `a.b.c.d/n` (n 0~32)
  - 범위 `a.b.c.d~e.f.g.h` — 시작 ≤ 끝 (이전 어드민에는 없던 순서 검사)
- 빈 항목(`1.1.1.1,,2.2.2.2`)은 오류다.
- 오류 문구: `올바른 IP 형식이 아닙니다: {첫 번째 잘못된 항목}`.
- 저장 값은 입력 그대로 둔다(공백 제거 결과로 바꾸지 않는다). FDS 모니터링은 IP 를 쓰지 않으므로 형식만 지킨다.
- IPv6 는 이전 어드민도 받지 않았다. 받지 않는다.

### 3.2 멤버코드 중복

- `AppserverController.validate` 에서 `AppserverService.existsDuplicate(memberCode, memberId, excludeIdx)` 를 부른다.
- 범위: 현재 고객사(`TenantContext`) 안. 수정 시 자기 행 제외.
- 등록·수정 모두 검사한다(이전 어드민은 수정만).
- 걸리면 `memberId` 필드 오류 `이미 등록된 코드와 ID값 입니다.`
- DB 유니크 제약은 없다. 동시 등록 경합은 막지 못한다 — 운영자 화면 수준의 방어다.

### 3.3 비밀번호 대소문자

- `PasswordPolicy.PATTERN` 을 `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$` 로 바꾼다.
- 문구: `영문 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.` (`PasswordChangeForm` 의 어노테이션 문구 포함)
- 길이 8~64 는 그대로. 기존 비밀번호로의 로그인은 영향 없다. 다음 변경부터 적용된다.
- 가입 신청 화면·운영자 폼·비밀번호 변경 화면의 안내 문구가 규칙을 적고 있으면 함께 고친다.

### 3.4 고객사 생성·삭제 시 딸린 데이터

생성(`CompanyService.create`, 한 트랜잭션, 기존 설정 복사 다음):
1. AAID 전체 차단 — `CCFA_COMPANY_AAID` 에 `CRITERIA` 의 모든 AAID 를 새 고객사로 넣는다.
   `CriteriaQueryService.changeStatusAll(false)` 의 insert 를 `disableAllFor(Long companyIdx)` 로 떼어 두 곳이 같이 쓴다
   (`changeStatusAll` 은 테넌트 컨텍스트의 고객사를 넘긴다).
2. FDS 정책 기본행 — `CCFA_FDS_POLICY (COMPANY_IDX, AND_COUNTRY='NO', OR_COUNTRY='NO')`, 생성·수정일 현재 시각.
   이미 있으면 두지 않는다.
3. 감사 로그 각 1줄(`AuditType.CREATE`): `CCFA_COMPANY_AAID 전체 차단 고객사 n (m건)`, `CCFA_FDS_POLICY 기본값 고객사 n`.
4. `FidoConfigChanged` 발행.

삭제(`CompanyService.delete`, 기존 설정 삭제와 같은 자리): 그 고객사의 `CCFA_COMPANY_AAID` 행과 `CCFA_FDS_POLICY` 행을
지우고 각각 감사 로그(`AuditType.DELETE`)를 남긴다. 하위 데이터 검사(`beforeDelete`)에 막히면 아무것도 지우지 않는다.

주의: 새 고객사는 AAID 를 하나씩 허용하기 전까지 FIDO 등록이 되지 않는다. 고객사 등록 완료 메시지에
`모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요.` 를 덧붙인다.

### 3.5 외부 라이선스 조회

```java
// 이전 어드민 ExternalController
@RequestMapping("/license/{filename}")                  // 메서드 제한 없음
filters.add(new Filter("HASHVALUE", Equal, filename));
List<Map> map = basicService.selectLicense(param);
if (map == null || map.size() < 1) return;              // 200, 빈 본문
ResponseUtil.responseText(res, (String) map.get(0).get("LICENSE"));
```

- `ExternalLicenseController`: `GET`, `POST` `/external/license/{filename}`.
- `CcfaLicenseRepository.findFirstByHashvalueOrderByIdxAsc(filename)` → 있으면 `LICENSE` 를 `text/plain;charset=UTF-8` 로,
  없거나 `LICENSE` 가 null 이면 200 빈 본문. 고객사 구분 없음(이전과 같다).
- 보안 설정: `/external/**` permitAll, CSRF 제외. `WebMvcConfig` 의 테넌트 선택·감사 조회 인터셉터 제외 목록에 추가.
- 요청 로그(`RequestLogFilter`)는 그대로 남는다. 조회 기록은 로그인 사용자가 없어 감사 로그에 남기지 않는다.
- `{filename}` 에 점이 있어도 경로 변수 전체가 잡히는지(`abc.lic`) 테스트로 고정한다.

## 4. 오류 처리

- reload 자동 전송: 어떤 예외도 호출자에게 올라가지 않는다(커밋은 이미 끝났다). 리스너 안에서 잡아 WARN.
- reload 수동 전송: 서버별 실패는 결과로 보여 주고, 목록 조회 자체의 예외는 공통 오류 처리로 간다.
- §3.4 의 딸린 데이터 작성이 실패하면 고객사 생성 전체가 롤백된다(같은 트랜잭션).

## 5. 파일

새 파일
- `system/reload/FidoReloadClient.java`, `ReloadResult.java`, `FidoConfigChanged.java`, `FidoReloadListener.java`, `FidoReloadConfig.java`(실행기·HTTP 클라이언트 빈)
- `common/IpRuleList.java`, `common/IpRuleListValidator.java`
- `company/web/ExternalLicenseController.java`
- `docs/legacy/admin-java-features.md`

수정
- `system/web/FidoClientController.java` + 목록 템플릿(수동 버튼)
- `fido/service/AppidService.java`, `AppserverService.java`, `CriteriaQueryService.java`, `fido/web/AppserverController.java`
- `fido2/service` 메타데이터·크리덴셜 파라미터 서비스
- `system/service/FidoSettingService.java`
- `company/service/CompanyService.java`, `company/web/CompanyController.java`(완료 메시지), `company/web/FdsPolicyForm.java`
- `company/repository/CcfaLicenseRepository.java`, `CcfaFdsPolicyRepository.java`(필요 시 조회 메서드)
- `common/PasswordPolicy.java`, `auth/PasswordChangeForm.java` 와 비밀번호 안내 문구가 있는 템플릿
- `config/SecurityConfig.java`, `config/WebMvcConfig.java`
- `docker/init/02-seed.sql` — 필요 시 테스트용 `HASHVALUE` 가 있는 라이선스 행

## 6. 테스트

- `FidoReloadClientTest` — JDK `HttpServer` 로 성공(200), 실패(500), 시간 초과(응답 지연), 잘못된 스킴, `OFF` 서버 제외,
  URL 끝 `/` 정리, 한 서버 실패 후 다음 서버 전송.
- `FidoReloadListenerTest` — 커밋 후 한 번 호출, 롤백 시 호출 안 함, `reloadAll` 예외가 새지 않음.
- 발행 지점별 서비스 테스트 — 등록·수정·삭제·토글·설정 저장·고객사 생성에서 `FidoConfigChanged` 가 나가고,
  AAID 토글이 "이미 해당 상태"면 나가지 않음(`@RecordApplicationEvents` 또는 모의 발행기).
- `FidoClientController` `@WebMvcTest` — 수동 전송의 성공·부분 실패·대상 없음 메시지, 감사 로그, SUPER 아닌 사용자 403.
- `IpRuleListValidatorTest` — 단일·CIDR·범위 통과, 옥텟 256·`/33`·역순 범위·빈 항목·IPv6 거부, 빈 값 통과, 공백 허용.
- `AppserverController` `@WebMvcTest` — 등록·수정 중복 거부, 자기 자신 수정 통과.
- `PasswordPolicyTest` — 소문자 없음·대문자 없음 거부, 네 종류 포함 통과. 기존 비밀번호 관련 테스트의 예시 값을 새 규칙에 맞춘다.
- `CompanyService` 테스트 — 생성 시 AAID 차단·FDS 기본행·감사 로그, 기존 FDS 행이 있으면 건너뜀, 삭제 시 정리, `beforeDelete` 에 막히면 정리 안 함.
  Oracle 통합 테스트(`OracleContainerSupport`)로 실제 insert 를 한 번 확인.
- `ExternalLicenseController` `@WebMvcTest` — 로그인 없이 GET·POST 200, 일치 행 본문, 없는 해시 빈 본문, 점 포함 파일명.

## 7. 옮기지 않는 것

- **보안상 옮기지 않음**: 특정 내부 IP 에서 비밀번호 없이 슈퍼 계정으로 자동 로그인하는 분기(`LoginController`),
  요청 파라미터의 SQL 원문을 그대로 실행하는 숨김 메뉴(`HiddenController` `/factory`, `/jsonAjax`).
- **기존 설계에서 이미 범위 밖**: 사용자 정의 통계 빌더·개인 대시보드 위젯, `CCFA_MENU`/`CVIEW` 기반 동적 화면,
  엑셀 내보내기.
- **이전 배치가 계속 돈다는 전제**: 통계 집계(`FIDOLogScheduler`), 만료 정리, 오류 알림 메일(`actionPerMin`),
  `.cfl` 아카이브.
- **자체 라이브러리 필요**: `JUSToolkit` 무결성 해시·SEED 복호화, `EncDataSource`.
- **스키마 변경 필요**: 비밀번호 90일 만료.
- **보안상 유지**: 로그인 실패 시 남은 시도 횟수 안내(이전 어드민에 있었음). 계정 존재·상태가 드러나므로 지금처럼 사유를 숨긴다.
- **이번 범위 밖(편의)**: 조회 기간 빠른 선택(오늘/7일/주/월/년).

## 8. 이전 어드민 기능 기록

`docs/legacy/admin-java-features.md` 에 전사 결과를 패키지별(컨트롤러 URL, 서비스·DAO, 스케줄러, 검증기, 설정 키)로
정리한다. `mybatis-mappers.md` 와 짝을 이룬다. 영상에 보인 암호화 키·IV·계정 비밀번호·내부 IP·개인 메일 주소·전화번호·
DB 접속 문자열 **값은 옮기지 않고** "하드코딩돼 있었다"는 사실만 적는다.
