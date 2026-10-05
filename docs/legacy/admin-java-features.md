# 이전 어드민 Java 소스 기능 기록

원천: 이전 어드민(Eclipse `fidoadmin`) 소스 화면 녹화(IMG_9165.MOV, 11분)를 3초 간격으로 전사한 기록.
원본 파일이 아니라 영상 판독이라 오탈자가 있을 수 있다. SQL 쪽은 `mybatis-mappers.md` 를 본다.

> **주의**
> 화면에 스크롤로 스쳐 간 구간은 읽지 못했다. 각 절에서 "확인 못 함"으로 적은 부분이 그렇다.
> 새 어드민이 이 중 무엇을 옮기고 무엇을 옮기지 않았는지는 설계서
> `docs/superpowers/specs/2026-10-01-legacy-feature-parity-design.md` 가 기준이다.
> 이 문서에는 암호화 키·IV, 계정 비밀번호, 접속 IP, 개인 메일·전화번호, DB 접속 문자열의 **값을 적지 않았다**
> (마지막 절에 종류만 있다).

## 패키지 구조

워크스페이스에는 `CCCUtills`, `CCFIDOLib`, `CCFIDOServer`(FIDO 서버), `dRPS`, `fido_samsungcard`, `fidoadmin` 이 함께 열려 있었다.
어드민은 Spring MVC + MyBatis(Quartz 스케줄러) 웹 애플리케이션이다.

| 패키지 | 내용 |
|---|---|
| `com.crosscert.fido.admin.beans` | 테이블 행 빈 `B_*`(고객사·운영자·메뉴·필드·옵션·통계·대시보드·FDS 정책·시스템 속성), `CCFIDOClient`·`CCFIDOClientInfo`(FIDO 서버 원격 호출), `CCDaoBean`(모듈명 기반 범용 DAO 파라미터·응답), `Criteria`(FIDO 메타데이터 문), `Filter`·`Filters`·`FilterOpConst`(검색 조건), `BioType`(인증수단 코드 → 이름), `ManagerSession`(로그인 세션) |
| `...admin.constrance` | `MailingByCompany`, `MailingData` — 회사별 SMTP 설정과 알림 수신자 캐시(싱글톤) |
| `...admin.controller` | 컨트롤러 15종 (아래 표) |
| `...admin.service` | `Basic`·`Common`·`Dashboard`·`FDS`·`Fido`·`Hidden`·`Log`·`Manager`·`Menu`·`Scheduler`·`Statistics` Service |
| `...admin.dao` | 위 서비스와 짝인 `*Dao`/`*DaoImpl`, `EncDataSource`(접속 정보를 암호문으로 받는 DataSource) |
| `...admin.dao.sqlMap.mys` / `.orac` | MySQL·Oracle 매퍼 두 벌. 같은 매퍼 ID 를 DB 별로 따로 썼다 |
| `...admin.filter` / `interceptor` / `listener` | `RequestEncodingFilter`(UTF-8), `BasicInterceptor`(세션 검사), 세션 리스너 2종(빈 구현) |
| `...admin.scheduling` | 스케줄러 5종 (아래 절) |
| `...admin.utills` | `AdminConfig`, `BPrint`(Gson), `CommonUtills`, `EMailSender`, `EmailUtil`, `FIDOLogWorker`, `JUSToolkit`, `LoginUtil`, `LogType`, `RandomString`, `ReqParamUtil`, `ResponseUtil`, `ServletContextUtil`, `StopWatch`, `SyncClient` |
| `...admin.validator` | 검증기 (아래 절) |
| `com.crosscert.fido.server.constants` | `FIDOLogType`, `FIDOStatistics`, `SystemProp` — FIDO 서버와 공유하는 상수·설정 모델 |
| `com.crosscert.fido.server.logger` | `FIDOLog`, `FIDOTransaction`, `HttpTransaction`, `Status`, `FidoOp`, `Stack`, `Count`, `OPCount`, `CFLParser` — FIDO 서버 로그 모델 |

화면 구조는 **메타데이터 기반 범용 CRUD** 였다. 메뉴 행(`B_MENU`)이 대상 테이블 이름(`TBL_NAME`)과 기본키를 들고 있고,
필드 정의(`B_FIELDS`), 옵션(`B_OPTION`/`B_OPTIONS`), 사용자별 컬럼 구성(`B_CVIEW`/`B_CVFIELDS`)을 읽어 공통 "main" 뷰를
그렸다. 새 어드민은 이 구조를 옮기지 않고 화면마다 템플릿을 둔다(설계서 §7).

## 컨트롤러와 URL

"새 어드민 대응" 열은 현재 코드(`MenuRegistry` 와 각 컨트롤러)의 URL 이다.
이전 어드민은 회사(고객사)를 세션에 들고 있어 모든 조회에 `COMPANY_IDX` 조건을 붙였다. 새 어드민은 같은 일을
`/select-tenant` 로 고른 테넌트(`TenantContext`)로 한다.

| 클래스 | URL | 동작 | 새 어드민 대응 |
|---|---|---|---|
| `APIController` | `/api/svc/reg/{serverName}?fsurl=` | FIDO 서버가 기동하며 자기 URL 을 등록(update 후 insert). `localhost` 는 호출자 IP 로 치환 | `/api/svc/reg/{serverName}` (`FidoClientRegistrationController`, 인증 없음) |
| `APIController` | `/api/svc/deReg/{serverName}` | 등록 해제(행 삭제) | `/api/svc/deReg/{serverName}` (행을 지우지 않고 `OFF` 로 내림) |
| `APIController` | `/api/svc/logs/work/start`, `/logs/work/restatistics/{날짜}/{시작}/{끝}` | 로그 수집 워커 수동 기동, 통계 재집계(회사별·`logLimit` 단위) | 없음(통계 집계 배치는 이전 배치가 계속 맡는다. 설계서 §7) |
| `BasicController` | `/error/{errorCode}` | 에러 코드 안내 | `/system/error-codes` (목록 화면) |
| `BasicController` | `/myAvatars.jpg` | 고정 경로 이미지 중 하나를 무작위로 내려 줌 | 없음(장식 기능) |
| `BasicController` | (메서드) `convert(type)` | 기간 프리셋 today/recently/tweekly/tmonthly/tyear | 없음(편의 기능, 설계서 §7 "이번 범위 밖") |
| `ChangePasswordController` | `/change-password` GET/POST | 비밀번호 변경. 대문자·소문자·숫자·특수문자 8자 이상, `LAST_PW_CHANGE_DATE` 갱신 | `/me/password` (같은 규칙, 만료 시 강제 이동) |
| `CompanyController` | `/system/company/list`, `/edit/{IDX}`, `/remove/{IDX}` | 고객사 CRUD. 신규 시 업체코드 5자리 자동 생성, 업체명·업체코드 중복 검사, 최상위(IDX 0) 삭제 금지, 변경을 감사 로그에 `[업체관리]` 로 | `/companies` (삭제 시 딸린 데이터 정리, 생성 시 AAID 전체 차단 — 설계서 §3.4. FDS 기본행은 2026-10-05 FDS 삭제로 만들지 않음) |
| `DashboardController` | `/dashboard/edit/{IDX}`, `/dashboard/delete/{IDX}` | 운영자별 대시보드 위젯(통계 항목) 구성. 같은 통계 중복 금지 | 없음(개인 위젯 구성은 범위 밖. 고정 대시보드 `/` 가 대신한다) |
| `ExternalController` | `/external/license/{filename}` | `HASHVALUE` 가 일치하는 라이선스의 `LICENSE` 문자열을 본문으로. 인증 없음 | `/external/license/{filename}` (`ExternalLicenseController`, GET·POST, 인증 없음) |
| `FDSController` | `/policy` POST | FDS 정책 저장(`FDSPolicyValidator`), 변경을 `[이상징후탐지정책]` 으로 기록 | 없음(2026-10-05 삭제) |
| `FDSController` | `/monitoring` | FDS 탐지 이력 목록(기간 `dterm`, `AND_IP`/`OR_IP` 조건, 페이징) | 없음(2026-10-05 삭제) |
| `FDSController` | `/ajax/realtime1`, `/ajax/realtime2` | 실시간 건수 차트. 2번도 1번 쿼리를 부르고, DAO 의 C2 는 미구현 | 없음(이전에도 반쪽이었다) |
| `FidoController` | `/{moduleName}/list`, `/edit/{IDX}` 등 | 범용 CRUD. 모듈: `aaid`, `credparams`, `metadata`(2022-04 추가), `membercode`, `appid`, `fidouser`. 등록·수정·삭제를 `KEY(old -> new)` 로 감사 기록 | `/appids`, `/appservers`, `/criteria`, `/fido2/metadata`, `/fido2/credential-params`, `/users` |
| `FidoController` | `/aaid/disabled/{AAID}`, `/aaid/enabled/{AAID}` POST | 회사별 AAID 차단/허용 토글 | `/criteria` (고객사 컨텍스트에서 토글) |
| `FidoController` | (등록·수정 처리 안) | `membercode` 는 `MEMBER_CODE`+`MEMBER_ID` 중복 검사(수정에서만), `aaid` 는 메타데이터 JSON 파싱과 AAID 중복 검사 | 멤버코드 중복은 등록·수정 모두(설계서 §3.2) |
| `HiddenController` | `/factory`, `/jsonAjax` | 요청 파라미터의 SQL 원문을 그대로 실행하는 콘솔, 복구 기능 | **없음(보안상 옮기지 않음)** |
| `LogController` | `/log/systemlog` | 감사(시스템) 로그 조회. 분류 키워드, 기간(기본 오늘), 회사별, 20건 페이징 | `/logs/audit` |
| `LogController` | `/log/{moduleName}` (`authenticate`/`error`) | FIDO 인증 로그·오류 로그. 일자별 테이블 `FIDO_LOGS_yyyyMMdd` | `/logs/fido`, `/logs/exceptions` |
| `LogController` | `/log/{moduleName}/detail/{IDX&yyyyMMdd}` | 로그 상세. `JSONDATA` 를 base64url 로 풀어 `FIDOLog` 로 | `/logs/fido/{date}/{id}` |
| `LoginController` | `/login` | 로그인. 아이디·비밀번호 필수, SHA-256 비교, IP·UA 기록, 5회 실패 시 30분 잠금, 90일 경과 시 변경 화면으로 | `/login` (잠금·만료는 같이 옮김. 사유 안내는 보안상 줄임) |
| `LoginController` | `/login` (분기) | 특정 내부 IP 에서 오면 비밀번호 없이 슈퍼 계정으로 자동 로그인 | **없음(보안상 옮기지 않음)** |
| `MailController` | `/mail/content/{type}` | 메일 본문 템플릿 렌더 | 없음(메일은 이전 배치가 보낸다) |
| `ManagerController` | `/system/manager/list`, `/edit/{IDX}`, `/remove/{IDX}` | 운영자 CRUD(`ManagerValidator`, ID 중복 검사), 변경 시 메일 수신자 캐시 갱신. 로그인한 운영자의 작업 회사 전환 | `/managers`, `/managers/super`, `/select-tenant` (회사 전환) |
| `StatisticsController` | `/statistics/statismanager`, `/edit/{IDX}`, `/ajaxGraph/{IDX}`, `/ajax/options`, `/remove/{IDX}` | 사용자 정의 통계 빌더(대상 테이블·함수·그룹 2단·필터 연산자·정렬·상위 N·그래프 종류) | 없음(범위 밖. 대시보드 `/` 가 고정 통계를 보여 준다) |
| `SystemController` | `/systemconfig/...` 의 서버 목록 | 등록된 FIDO 서버 목록과 `reload` 신호 전송 | `/system/fido-clients` (수동 reload 버튼) |
| `SystemController` | `/systemconfig/license` GET/POST, `/ajax` | 라이선스 파일 목록·업로드(`LICENSE_ROOT` 경로). 삭제 메서드는 본문이 비어 있었다 | `/licenses` (행 관리. 파일 업로드는 옮기지 않음) |
| `SystemController` | `/systemconfig` GET/POST | 회사별 시스템 설정(`CCFA_SYSTEM_PROP` 키·값). 체크박스 키는 값이 없으면 `DISABLE`, 변경은 "시스템 설정 변경" 으로 기록 | `/system/settings`(FIDO 서버 설정), `/system/props`(시스템 설정) |
| `SystemController` | `/company`, `/manager`, `/license` | `/system/*/list` 로 가는 단축 리다이렉트 | 없음 |

이전 어드민에서 읽지 못한 부분: 컨트롤러 저장(POST) 매핑 문자열 대부분(메서드만 보였다), `ManagerController.remove` 의
뒷부분, `FidoController` 의 `metadata` 중복 검사.

### 감사 로그 기록 방식

모든 변경은 세션 객체의 `trace(LogType, 메시지)` 로 남았다. 유형은 `EDIT`, `DELETE`, `VIEW`, `SEARCH`, `ADD`, `LOGIN`,
`LOGOUT`, `ERROR`, `SESSION_TIMEOUT`. 메시지는 `[메뉴명] 키(이전 -> 이후)` 꼴의 변경분이다. 세션에는 무결성 해시
(`IP|UA|회사명` 등을 이어 `JUSToolkit` 으로 해시)를 만드는 코드가 있었다. 새 어드민은 `audit` 패키지가 이 역할을
한다(`AuditType`, `AuditChanges`, `AuditView`).

## 서비스·DAO

서비스는 같은 이름의 DAO 에 위임하는 얇은 계층이다. DAO 는 `SqlSessionDaoSupport` 를 상속해 `모듈명.쿼리ID` 로
MyBatis 를 부른다(쿼리 본문은 `mybatis-mappers.md`).

| 서비스(DAO 모듈) | 주요 메서드 | 비고 |
|---|---|---|
| `BasicService` (`basic.`) | 에러 코드 조회(없으면 "알 수 없는 오류가 발생하였습니다."), 범용 `getList`/`getData`/`insertData`/`updateData`/`deleteData`, 옵션·필드 조회, 시스템 속성 select/insert/update, 라이선스 select/insert/update/delete, 회사별 AAID `enable`/`disable`/`select`, `disableCompanyAllAAID` | AAID 는 차단 목록 방식이다. 비활성 = 행 insert, 활성 = 행 delete |
| `CommonService` (`CommonDaoImpl`) | `모듈명.selectItemList`/`selectListCount` | `CCDaoBean` 으로 받는 범용 조회. FIDO 서버 목록(`clientInfo`)도 여기로 읽었다 |
| `DashboardService` (`dashboard.`) | 목록·건수·단건·등록·수정·삭제 | 단건 조회가 목록 쿼리를 재사용 |
| `FDSService` (`fds.`) | 정책 select/insert/update/delete, 모니터 목록·건수, 실시간 C1·C2 | C2 는 TODO 스텁이 null 반환 |
| `FidoService` | (DAO 인터페이스가 비어 있음) | 실제 일은 `FidoController` 와 `BasicService` 가 했다 |
| `HiddenService` | `select`/`update`/`delete`/`insert(String sql)` | 문자열 SQL 을 `basic.*Query` 에 넘긴다. 결과는 SQL·영향 행·오류 메시지 |
| `LogService` (`log.`) | 로그 기록·조회·건수, 대상 인덱스 목록, JSON 목록, 처리한 인덱스 삭제, 마지막 통계 시점 | 배치가 원본 로그를 읽는 용도 |
| `ManagerService` (`manager.`) | 고객사 select/count/insert/update/delete(다건), 운영자 select/insert/update/delete, 시스템 속성 insert/delete, 업체코드 조회, 비밀번호 정책 insert/update/reset(2022-06-10 추가) | 아래 "딸린 데이터" 참고 |
| `MenuService` (`menu.`) | 메뉴 트리 재귀 조회(부모 IDX) | |
| `SchedulerService` | 고객사 목록, 만료 챌린지·TC 내용·TC 해시 정리, 운영자 목록, 시스템 속성, 예외 목록 조회·삭제, 메일 이력 기록, 통계 insert, 잠금 자동 해제 | |
| `StatisticsService` (`statistics.`) | 통계 정의 CRUD, 필터·정렬 CRUD, 대상·컬럼·그래프 조회 | 동적 SQL 집계 |

### 고객사·운영자 생성 시 딸린 데이터

`ManagerDaoImpl` 에 있던 동작이다. 새 어드민으로는 고객사 쪽만 옮겼다.

- 고객사 생성: 행 insert → 새 IDX 로 **AAID 전체 차단**(`disableCompanyAllAAID`) → 회사 기본 시스템 속성 insert →
  **FDS 정책 기본행**(`fds.insert`) 도 같은 자리에서 생성. 새 어드민: `CompanyService.create` (설계서 §3.4). FDS 정책 기본행은 2026-10-05 이후 만들지 않는다.
- 고객사 삭제: 행 삭제 → 그 고객사의 시스템 속성·AAID 행 정리. 새 어드민: `CompanyService.delete` 가 설정·AAID 를 지운다(FDS 정책 행은 2026-10-05 이후 건드리지 않는다).
- 운영자 생성: 행 insert 후 `OWNER_IDX` 로 `managerStatisticsInit1`~`4` 를 넣어 개인 통계·대시보드 기본값을 만든다.
  개인 위젯을 옮기지 않았으므로 **옮기지 않았다**.
- 운영자 삭제: 행 삭제 + `deleteManagerData`(개인 데이터 정리). 같은 이유로 옮기지 않았다.

### FIDO 서버 reload 신호

- 이전 어드민: `CCFIDOClientInfo.sendSignal(command)` 가 `{서버 URL}/api/command/{command}` 로 **GET** 을 보냈다.
  `sendAllSignal` 이 `CCFA_FIDOCLIENT` 전 행에 `reload` 를 보냈고, 호출 시점은 AppID·멤버코드·AAID 메타데이터의
  등록·수정에서 **insert/update 보다 먼저**였다(커밋 전 값을 FIDO 서버가 읽을 수 있는 경쟁 조건). 예외는 출력만 했다.
  수동 화면은 `SystemController` 의 서버 목록이었다.
- 별도로 `SyncClient` 는 TCP 소켓으로 FIDO 서버 여러 대에 `COMPANY_IDX:n` 같은 문자열을 보내는 경로였다
  (`fidoserverip`·`fidoserverport` 설정 키, 요청 유형 `CompanyUpdate`/`MetadataUpdate`). 호출부는 주석 처리돼 있었다.
- 새 어드민: 커밋 뒤 비동기 자동 전송(`STATUS='ON'` 행만) + `/system/fido-clients` 의 동기 수동 전송 (설계서 §2). `SyncClient` 는 옮기지 않았다.

## 스케줄러

Quartz `QuartzJobBean` 기반이다. 주기 설정은 XML 이라 확인하지 못했다.

| 클래스 | 동작 | 새 어드민 대응 |
|---|---|---|
| `actionPerMin` | 이름상 매분. ① 미통보 예외 목록(`selectExceptions`)을 모아 제목·본문 템플릿으로 **오류 알림 메일** 발송, 이력 저장(`insertMailLog`, 본문 3994 바이트로 절단), 발송한 예외 삭제 ② TC 해시 정리(`deleteTChash`) ③ 예외가 나면 고정 수신자에게 긴급 메일 ④ 회사별 메일링(`MailingByCompany`) 블록은 주석 처리 | 없음(이전 배치가 계속 돈다는 전제. 어드민은 `CCFA_EXCEPTIONS`·`CCFA_MAILING` 을 읽기만 한다) |
| `BasicScheduler` | 베이스 클래스. 본문은 자리 표시 출력 하나 | 없음 |
| `FIDOLogScheduler` | 현행. `FIDOLogWorker` 스레드를 시작 | 없음(배치가 계속 돈다는 전제) |
| `FIDOLogScheduler_old` | 예전 구현. 1시간 전 시간대 로그를 회사별로 `logLimit`(기본 100) 단위로 읽어 `.cfl` 로 저장하고 통계를 넣은 뒤 **원본 로그 삭제**, 이미 실행 중이면 건너뜀 | 없음 |
| `ManagerPwPolicyScheduler` | 2022-06-10 추가. `updateManagerPwPolicyUnlock` 로 **잠금 시간이 지난 계정을 자동 해제** | 로그인 시 잠금 시각으로 판정한다(`LoginAttemptService`). 별도 배치는 없음 |

## 검증기

선언형 프레임워크(`BasicValidator`)에 규칙 배열 `{유형, 필드, 이름, ...}` 를 넘기는 방식이다.
유형: `REQUIRED`, `REQUIRED_ANY`, `INT`, `LONG`, `DATE`, `LENGTH`, `DATESTRING`, `BOOLEAN`, `LOGINID`, `PASSWORD`, `DOUBLE`.
날짜 형식 4종: `yyyy-MM-dd`, `yyyy-MM`, `yyyy-MM-dd HH:mm`, `yyyy-MM-dd HH:mm:ss`.
`BasicValidator.validatePassword` 는 항상 null 을 돌려주는 빈 구현이라 실제 비밀번호 규칙은 `ManagerValidator` 와
`ChangePasswordController` 에 따로 있었다.

| 검증기 | 규칙 | 새 어드민 |
|---|---|---|
| `CompanyValidator` | 회사명·상태 필수, 시작·종료일 `FULLTIME`, 최대 APPID·앱서버·사용자 수는 정수(비면 0) | 고객사 폼 검증 |
| `ManagerValidator` | 비밀번호·재입력 일치, 대문자·소문자·숫자·특수문자 포함 8자 이상, 로그인 ID `[a-zA-Z0-9]{3,20}`, 변경 시 `LAST_PW_CHANGE_DATE` 기록("2507" 주석) | 같은 규칙(설계서 §3.3), 만료는 열이 있을 때만(§3.6) |
| `FDSPolicyValidator` | AND·OR 국가 필수, IP 목록 각 항목이 단일·`a.b.c.d/nn`·`a~b` 중 하나(쉼표 구분, 공백 제거). 오류 "올바른 ip형식이 아닙니다." | 없음(2026-10-05 FDS 삭제와 함께 `@IpRuleList` 제거) |
| `FidoValidator` | 모듈별 필수·정수 규칙. `appid`(APPID·STATUS), `metadata`(인증기 메타데이터 필드), `credparams`(CRED_ALG), `membercode`(MEMBER_CODE·MEMBER_ID·TYPE), `fidouser`(USERID). 메타데이터는 JSON 원문을 `Criteria` 로 파싱하고 해시를 저장, 실패 시 "유효하지 않은 metadata 형식입니다." | 화면별 폼 검증. `fidouser` 블록 뒤는 읽지 못했다 |
| `StatisticsValidator` | 통계 빌더 입력(대상·함수·그룹·필터·정렬·상위 N). 그룹 2 만 있으면 거부, 그룹이 있으면 정렬 항목은 그룹 필드 중 하나 | 없음(통계 빌더를 옮기지 않음) |
| `DashboardValidator` | 통계 항목·순서·컬럼 클래스·크기 필수 | 없음(개인 위젯을 옮기지 않음) |

## 설정 키

### `CCFA_SYSTEM_PROP` 의 `PROP_KEY` (이름만. `SystemProp` 클래스 기준, 회사별 행)

- 경로: `RPS_ROOT`, `FS_ROOT`, `METADATA_ROOT`, `LICENSE`, `LICENSE_ROOT`
- 알림 메일: `SMTP`, `SMTP_IP`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
- 인증서: `CERT`, `CERT_VERIFY`, `CERT_SIGN_VERIFY`, `CERT_P7`, `CERT_P1`, `CERT_PUBKEY`, `CERT_PUBKEY_ALG`, `AUTH_ALG`
  (일부는 2016-08-29 추가)
- OCSP: `OCSP_PASSWD`, `OCSP_PRIKEY`, `OCSP_CERT`, `OCSP_SERVERIP`, `OCSP_SERVERPORT`
- 프로토콜: `PROTOCOL_TV`, `TC_ORIGIN_TERM`, `CHALLENGE_TERM`, `FIDO_METADATA_VALID`, `FIDO_REREG_ENABLE`,
  `FIDO_ATTESTCERT_AAID_CHECK`(체크박스 키 중 유일하게 꺼지면 `DISABLE` 이 아니라 `N`)
- 그 밖: 알 수 없는 키는 "Undefined" 로 출력만 했다. 목록 뒷부분은 확인하지 못했다.
- 화면 저장 시 체크박스 키(`CERT_P9`, `CERT_P7`, `CERT_SIGN_VERIFY`, `CERT`, `SMTP`, `CERT_P1`, `CERT_PUBKEY`,
  `CERT_PUBKEY_ALG`, `AUTH_ALG`, `PROTOCOL_TV`, `FIDO_METADATA_VALID`, `FIDO_REREG_ENABLE` 등)는 값이 없으면 `DISABLE`.
- 새 어드민이 더한 키: `PW_FAIL_LIMIT`(로그인 실패 한도), `PW_EXPIRY_DAYS`(비밀번호 만료 일수, 없으면 90). 회사 0 행.

### `AdminConfig` (정적 설정 holder, 키 이름만)

`key`, `ocspcert`, `ocspprik`, `ocsppwd`, `encKey`, `logsPath`, `logLimit`(기본 100), `license`, `fidoserverip`,
`fidoserverport`(기본 12799). 값은 배포 환경 설정에서 왔고 화면에서는 읽지 못했다.

### 그 밖의 고정 동작

- 로그인 실패: 5회 연속 실패 시 30분 잠금(`ACCOUNT_LOCK`, `LOCKTIME`, `PW_FAIL_CNT`, `CCFA_MANAGER_PW_POLICY`).
- 비밀번호 만료: 90일(코드 상수). 새 어드민은 `PW_EXPIRY_DAYS` 로 바꿀 수 있다.
- 비밀번호 저장: SHA-256 소문자 hex, 솔트 없음(`LoginUtil.toEncPassword`). 새 어드민의 `Sha256PasswordEncoder` 가 같은 방식을 받아 준다.
- 인증수단 코드(`BioType`): 1 PRESENCE, 2 지문, 4 PIN, 8 음성, 16 얼굴인식, 32 지역기반, 64 홍채, 128 패턴, 256 손바닥,
  512 없음, 1024 ALL, 그 밖 알 수 없음.

## 로그 파이프라인

```
FIDO 서버 ──> FIDO_LOGS_yyyyMMdd (일자별 테이블)   JSONDATA 컬럼 = base64url(JSON of FIDOLog)
                   │
                   │ FIDOLogScheduler → FIDOLogWorker (회사별, logLimit 단위로 인덱스 목록 → JSON 목록)
                   ▼
   parseLog: base64url 디코드 → Gson → FIDOLog
        ├─ FIDOStatistics.push(log) ─> 회사·서비스·target 별 집계 ─> insertStatistics ─> FIDO_STATISTICS
        └─ target 키(a/b/c/d)로 묶어 {logsPath}/transactionlogs/{회사명}/{yyyy}/{MM}/{dd}/{HH}.cfl 저장
```

- **`FIDO_LOGS_yyyyMMdd`**: 일자별 분할 테이블. 현행 워커는 **전날** 테이블을 대상으로 한다(`getCalDate(-1,"yyyyMMdd")`).
  화면은 조회 기간의 날짜 목록을 만들어(`getCalGapDate`) 해당 일자 테이블들을 읽고, 상세는 `IDX&yyyyMMdd` 로 테이블을 고른다.
  새 어드민 대응: `/logs/fido` 와 `/logs/fido/{date}/{id}`.
- **`JSONDATA`**: base64url 문자열. 디코드하면 `FIDOLog`(`transaction`, `hash`, `logtime`, `accessIp`, `userAgent`,
  `deviceInfo`). `transaction` 은 `serviceName`, `userName`, `op`(`Reg`/`Auth`/`DeReg`/`TC`), `bioType`, 요청·응답,
  `status`(`RequestOK`/`ResponseOK`/`Success`/`Error`/`Wait`), 단계별 `Stack` 이다.
  사용자 이름에 `_*` 가 있으면 뒤쪽을 서비스명으로 쓰고, `_*KF` 로 끝나면 KFIDO, 아니면 FIDO 로 구분한다.
  `Auth` 인데 요청에 `transaction` 이 들어 있으면 TC(전자서명 확인)로 센다.
- **`.cfl`**: zip 압축 안의 JSON 배열(`FIDOLog` 목록). `CFLParser.ZipFileReader` 가 읽어 서비스명 × 인증수단 × 동작 ×
  결과로 센다. 워커가 덮어쓰는 코드(3분할)가 있어 버그 가능성이 보였다(확인 못 함).
- **통계 집계**: `FIDOStatistics` 는 `regSuccess`/`regFail`/`authSuccess`/`authFail`/`tcSuccess`/`tcFail`/`deregSuccess`/`deregFail`
  여덟 값을 센다. 새 어드민 대시보드가 이 테이블을 읽는다(`StatisticsQueryService`).
- **무결성**: `FIDOLog.isIntegrity()` 는 `hash` 를 `transaction` 의 해시와 비교한다. 새 어드민은 검증하지 않는다.
- 구버전(`_old`)은 처리한 원본 로그를 지웠고, 현행 워커에서 삭제 여부는 확인하지 못했다.

이 파이프라인 전체(수집·집계·아카이브·정리)는 이전 배치가 계속 돈다는 전제로 새 어드민에 옮기지 않았다.

## 옮기지 않은 것과 이유

설계서 §7 의 요약이다.

| 항목 | 이유 |
|---|---|
| 특정 내부 IP 에서 비밀번호 없이 슈퍼 계정으로 자동 로그인하는 분기 | 보안. 인증 우회다 |
| 요청 파라미터의 SQL 원문을 그대로 실행하는 숨김 메뉴(`/factory`, `/jsonAjax`)와 복구 기능 | 보안. 임의 SQL 실행이다 |
| 사용자 정의 통계 빌더, 개인 대시보드 위젯, 운영자 생성 시 개인 통계 초기화 | 기존 설계에서 이미 범위 밖. 고정 대시보드가 대신한다 |
| `CCFA_MENU`/`CVIEW` 기반 동적 화면 | 기존 설계에서 범위 밖. 화면마다 템플릿을 쓴다 |
| 엑셀 내보내기 | 기존 설계에서 범위 밖 |
| 통계 집계(`FIDOLogScheduler`), 만료 정리, 오류 알림 메일(`actionPerMin`), `.cfl` 아카이브 | 이전 배치가 계속 돈다는 전제 |
| `JUSToolkit` 무결성 해시·SEED 복호화, `EncDataSource` | 자체 라이브러리 필요 |
| `SyncClient`(TCP 동기화 신호) | 호출부가 주석 처리돼 있었고 reload HTTP 신호로 대체 |
| 조회 기간 빠른 선택(오늘·7일·주·월·년) | 편의 기능이라 이번 범위 밖 |
| 로그인 실패 시 남은 시도 횟수 안내 | 계정 존재·상태가 드러나므로 사유를 숨기는 현재 방식을 유지 |
| 메일 수신자 캐시(`MailingData`)와 회사별 SMTP 발송 코드 | 발송은 이전 배치 몫. 어드민은 설정 값만 저장 |
| 장식 기능(`/myAvatars.jpg`), 세션 리스너, `StopWatch` 디버그 출력 | 기능이 아님 |

## 하드코딩된 민감값

종류만 적는다. 값은 이 문서에 없다. 소스를 다시 볼 일이 있으면 원본에서 확인하고, 새 저장소에 옮기지 않는다.

| 종류 | 위치 | 메모 |
|---|---|---|
| 암호화 키·IV 문자열 | `JUSToolkit`(SEED-CBC 암·복호화용 IV 고정값) | 키는 `AdminConfig` 에서 왔지만 IV 는 코드에 박혀 있었다(판독도 불확실) |
| DB 접속 복호용 AES 키 문자열 | `EncDataSource` | 접속 정보를 hex 암호문으로 두고 이 키로 풀었다. `[plain]` 접두사면 평문 허용 |
| 슈퍼 계정 비밀번호 | `LoginController` 의 자동 로그인 분기 | 계정 이름과 비밀번호가 함께 코드에 있었다 |
| 내부 IP 자동 로그인 | `LoginController` | 그 IP 에서 오면 위 계정으로 바로 로그인 |
| 메일 수신자·참조(CC) | `actionPerMin`, `EMailSender` | 사내 주소 여러 개가 코드에 있었다 |
| 전화번호 | `actionPerMin` | SMS 대상으로 보이는 번호 3개. 사용처는 확인하지 못했다 |
| DB 접속 문자열 | 영상 끝의 메모장 | 환경(QA 등)별 URL·계정·비밀번호가 암호화 표기가 붙은 암호문과 접속 문자열로 적혀 있었다 |
