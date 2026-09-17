# FIDO Admin 1부 — 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 KBFIDO Oracle 스키마를 무변경으로 사용하는 Spring Boot + Thymeleaf 관리자 웹의 기반(빌드, 로컬 Oracle, 엔티티 39개, 인증, 공통 CRUD, 레이아웃)과 참조 화면 3개(고객사 CRUD, 감사 로그 조회, 대시보드)를 실행 가능한 상태로 만든다.

**Architecture:** Controller(Thymeleaf) → Service(트랜잭션·테넌트 필터·감사 로그) → Spring Data JPA Repository. 엔티티는 ERD와 1:1이며 연관관계 없이 ID 값만 보유한다. 단순 CRUD 화면은 `CrudController`/`CrudService` 추상 클래스를 상속해 공통 처리한다. 2부 계획은 이 기반 위에 나머지 26개 화면을 같은 패턴으로 추가한다.

**Tech Stack:** Java 17, Gradle Wrapper 8.14, Spring Boot 3.5.3 (Web, Thymeleaf, Security, Data JPA, Validation), Lombok, ojdbc11, WebJars(bootstrap 5.3.7, chart.js 4.5.0, webjars-locator-lite), Docker `gvenzl/oracle-free:23-slim`, JUnit 5, Testcontainers oracle-free.

**Spec:** `docs/superpowers/specs/2026-09-16-fido-admin-design.md`

## Global Constraints

- Java 17, Spring Boot 3.5.x, Gradle Wrapper 8.14, Groovy DSL.
- DB는 Oracle. **ERD 이외의 테이블·컬럼을 추가·수정·삭제하지 않는다.** `spring.jpa.hibernate.ddl-auto: none`, `open-in-view: false`.
- 엔티티 컬럼은 `docs/erd/kbfido-columns.txt`와 1:1. 컬럼 이름·개수가 다르면 `ErdConformanceTest`가 실패한다.
- 뷰는 Thymeleaf만. 외부 CSS/JS/폰트 호출 금지. 정적 자원은 WebJars(jar 내부)와 `src/main/resources/static`만 사용. 폰트는 시스템 폰트 스택.
- 시퀀스 이름은 임시로 `<TABLE>_SEQ`. 실제 이름 제공 시 엔티티의 `sequenceName`과 `docker/init/01-schema.sql`을 함께 교체한다.
- `docker/init/*.sql`은 **로컬 검증 전용**이며 운영 DB에 적용하지 않는다.
- 민감 컬럼(`USER_PW`, `PUBKEY`, `CERTIFICATE`, `AMZ_TOKEN`)은 화면 DTO에서 제외하거나 앞 16자만 표시.
- JS `alert/confirm/prompt` 사용 금지. 삭제 확인은 Bootstrap 모달.
- 패키지 루트 `com.crosscert.fidoadmin`. 포트 8080. UI 언어 한국어.
- 커밋 메시지는 한국어 요약 + `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## 파일 구조 (1부에서 생성)

```
fido-admin/
├── build.gradle, settings.gradle, gradlew*, gradle/wrapper/
├── README.md
├── docker/docker-compose.yml, docker/init/01-schema.sql, docker/init/02-seed.sql
├── src/main/java/com/crosscert/fidoadmin/
│   ├── FidoAdminApplication.java
│   ├── config/SecurityConfig.java, JpaConfig.java, WebMvcConfig.java
│   ├── common/SearchForm.java, CrudService.java, CrudController.java, ReadOnlyController.java,
│   │          TenantContext.java, TenantMismatchException.java, GlobalExceptionHandler.java,
│   │          Specs.java, MenuItem.java, MenuRegistry.java
│   ├── audit/AuditLogger.java, AuditType.java
│   ├── auth/Sha256PasswordEncoder.java, ManagerUserDetails.java, ManagerUserDetailsService.java,
│   │        LoginSuccessHandler.java, LoginFailureHandler.java, LogoutSuccessHandler.java,
│   │        LoginController.java, PasswordChangeController.java, PasswordChangeForm.java
│   ├── company/entity/CcfaCompany.java, CcfaFdsPolicy.java, CcfaLicense.java
│   ├── company/repository/CcfaCompanyRepository.java
│   ├── company/service/CompanyService.java, CompanyLookup.java
│   ├── company/web/CompanyController.java, CompanyForm.java, CompanySearchForm.java
│   ├── manager/entity/CcfaManager.java, CcfaManagerPwPolicy.java
│   ├── manager/repository/CcfaManagerRepository.java, CcfaManagerPwPolicyRepository.java
│   ├── fido/entity/Appid.java, Appserver.java, Userinfo.java, Challenge.java, Criteria.java,
│   │               Sign.java, Transactionhash.java, TransactionConfirmation.java
│   ├── fido2/entity/Fido2Metadata.java, Fido2CredentialParams.java, Fido2DemoAccessCode.java
│   ├── log/entity/FidoLogs.java, FidoLogs20210101.java, FidoLogs20210102.java, BakFidoLogsBak.java,
│   │              BakFidoLogsTest.java, CcfaAuditLog.java, CcfaExceptions.java, CcfaMailing.java
│   ├── log/repository/CcfaAuditLogRepository.java
│   ├── log/service/AuditLogQueryService.java
│   ├── log/web/AuditLogController.java, AuditLogSearchForm.java
│   ├── statistics/entity/FidoStatistics.java, FidoStatisticsId.java, FidoStatisticsBak.java,
│   │                     CcfaStatistics.java, CcfaStatisticsFilter.java, CcfaStatisticsFilterId.java,
│   │                     CcfaStatisticsOrder.java, CcfaStatisticsOrderId.java
│   ├── statistics/repository/FidoStatisticsRepository.java
│   ├── dashboard/DashboardController.java, DashboardSearchForm.java, StatisticsQueryService.java,
│   │             DailyStat.java, StatTotals.java
│   ├── aws/entity/AwsInfo.java
│   └── system/entity/CcfaSystemProp.java, CcfaSystemPropId.java, CcfaSystemInfo.java, CcfaErrorTable.java,
│                     CcfaFidoclient.java, CcfaCriteria.java, CcfaMenu.java, CcfaOption.java, CcfaOptions.java,
│                     CcfaFields.java
│   └── system/repository/CcfaSystemPropRepository.java
├── src/main/resources/application.yml, application-local.yml, static/css/admin.css,
│   templates/layout/base.html, fragments/*.html, login.html, error/403.html, 404.html, 500.html,
│   dashboard/index.html, company/company/list.html, form.html, detail.html, log/audit/list.html, detail.html,
│   auth/password.html
└── src/test/java/... (각 태스크에 명시), src/test/resources/erd-columns.txt
```

---

## Task 1: Gradle 프로젝트 스캐폴드와 부팅 확인

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`
- Create: `src/main/java/com/crosscert/fidoadmin/FidoAdminApplication.java`
- Create: `src/main/resources/application.yml`, `src/main/resources/application-local.yml`
- Test: `src/test/java/com/crosscert/fidoadmin/BuildSmokeTest.java`

**Interfaces:**
- Produces: Gradle 태스크 `./gradlew test`, `./gradlew bootRun --args='--spring.profiles.active=local'`; 메인 클래스 `FidoAdminApplication`.

- [x] **Step 1: Gradle Wrapper 생성**

로컬에 Gradle이 설치되어 있으므로 wrapper를 만든다.

```bash
cd /Users/jhyun/Git/10-work/crosscert/fido-admin
gradle wrapper --gradle-version 8.14 --distribution-type bin
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` 생성. `./gradlew --version`에 `Gradle 8.14` 출력.

- [x] **Step 2: settings.gradle 작성**

```groovy
rootProject.name = 'fido-admin'
```

- [x] **Step 3: build.gradle 작성**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.crosscert'
version = '0.1.0-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

configurations {
    compileOnly { extendsFrom annotationProcessor }
}

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
    implementation 'org.webjars:webjars-locator-lite'
    implementation 'org.webjars:bootstrap:5.3.7'
    implementation 'org.webjars.npm:chart.js:4.5.0'
    runtimeOnly 'com.oracle.database.jdbc:ojdbc11'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:oracle-free'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    testLogging { events 'failed'; exceptionFormat 'full' }
}
```

- [x] **Step 4: 메인 클래스 작성**

`src/main/java/com/crosscert/fidoadmin/FidoAdminApplication.java`

```java
package com.crosscert.fidoadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FidoAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(FidoAdminApplication.class, args);
    }
}
```

- [x] **Step 5: application.yml 작성**

`src/main/resources/application.yml`

```yaml
spring:
  application:
    name: fido-admin
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: none
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
        implicit-strategy: org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl
    open-in-view: false
    properties:
      hibernate:
        jdbc.time_zone: Asia/Seoul
        default_batch_fetch_size: 50
  thymeleaf:
    cache: true
  web:
    resources:
      cache:
        cachecontrol:
          max-age: 1d
server:
  port: 8080
  servlet:
    session:
      timeout: 30m
fido-admin:
  page-size: 20
  password-fail-limit: 5
```

`src/main/resources/application-local.yml`

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/FREEPDB1
    username: kbfido
    password: kbfido
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  thymeleaf:
    cache: false
logging:
  level:
    org.hibernate.orm.jdbc.bind: trace
```

- [x] **Step 6: 스모크 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/BuildSmokeTest.java`

```java
package com.crosscert.fidoadmin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {
    @Test
    void mainClassExists() {
        assertThat(FidoAdminApplication.class.getSimpleName()).isEqualTo("FidoAdminApplication");
    }
}
```

- [x] **Step 7: 빌드·테스트 실행**

Run: `./gradlew test --no-daemon`
Expected: `BUILD SUCCESSFUL`, 테스트 1개 통과.

- [x] **Step 8: 커밋**

```bash
git add settings.gradle build.gradle gradlew gradlew.bat gradle/ src/
git commit -m "chore: Gradle 스캐폴드와 Spring Boot 기본 설정

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 2: 로컬 검증용 Docker Oracle 스키마·시드

**Files:**
- Create: `docker/docker-compose.yml`
- Create: `docker/init/01-schema.sql`
- Create: `docker/init/02-seed.sql`
- Create: `docker/README.md`

**Interfaces:**
- Produces: `localhost:1521/FREEPDB1`, 계정 `kbfido/kbfido`, 스키마 `KBFIDO`에 ERD 39개 테이블·시퀀스 29개·시드 데이터. 로그인 계정 `superuser/Admin1234!`(SUPER), `kbadmin/Company1234!`(COMPANY, COMPANY_IDX 1).
- 시퀀스 이름 `<TABLE>_SEQ`는 Task 4 엔티티의 `sequenceName`과 반드시 같아야 한다.

- [x] **Step 1: docker-compose.yml 작성**

`docker/docker-compose.yml`

```yaml
services:
  oracle:
    image: gvenzl/oracle-free:23-slim
    container_name: fido-admin-oracle
    ports:
      - "1521:1521"
    environment:
      ORACLE_PASSWORD: oracle
      APP_USER: kbfido
      APP_USER_PASSWORD: kbfido
    volumes:
      - ./init:/container-entrypoint-initdb.d:ro
      - oracle-data:/opt/oracle/oradata
    healthcheck:
      test: ["CMD", "healthcheck.sh"]
      interval: 10s
      timeout: 5s
      retries: 30
volumes:
  oracle-data:
```

`APP_USER`로 만든 `KBFIDO` 사용자는 `FREEPDB1` 안에 생성된다. init 스크립트는 SYSDBA로 실행되므로 각 스크립트 첫 줄에서 컨테이너와 스키마를 전환한다.

- [x] **Step 2: 01-schema.sql 작성**

`docker/init/01-schema.sql` — 아래 내용 전체. ERD 컬럼 정의(`docs/erd/kbfido-columns.txt`)와 1:1이다.

```sql
-- ============================================================
-- 로컬 검증 전용 스키마. 운영 DB에 절대 적용하지 말 것.
-- 근거: docs/erd/kbfido-columns.txt (KBFIDO 39 테이블 / 354 컬럼)
-- 시퀀스 이름 <TABLE>_SEQ 는 임시. 실제 이름 확인 후 교체.
-- ============================================================
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = KBFIDO;

CREATE TABLE APPID (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER,
  APPID                        VARCHAR2(128),
  MEMO                         VARCHAR2(512),
  STATUS                       VARCHAR2(12) DEFAULT 'use' NOT NULL,
  DEVICE                       VARCHAR2(128),
  DEVICE_DEFAULT               VARCHAR2(1) DEFAULT 'F' NOT NULL,
  SERVICENAME                  VARCHAR2(512),
  CREATEDTIME                  TIMESTAMP DEFAULT SYSDATE,
  UPDATEDTIME                  TIMESTAMP DEFAULT SYSDATE,
  CONSTRAINT APPID_PK PRIMARY KEY (IDX)
);

CREATE TABLE APPSERVER (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER,
  MEMBER_CODE                  VARCHAR2(32) NOT NULL,
  MEMBER_ID                    VARCHAR2(32) NOT NULL,
  TYPE                         VARCHAR2(10) DEFAULT 'use' NOT NULL,
  NOTE                         VARCHAR2(128),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT APPSERVER_PK PRIMARY KEY (IDX)
);

CREATE TABLE USERINFO (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER,
  BIO_TYPE                     NUMBER,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  USERID                       VARCHAR2(512) NOT NULL,
  AAID                         VARCHAR2(128) NOT NULL,
  AUTHENTICATORVERSION         NUMBER DEFAULT 0 NOT NULL,
  KEYID                        VARCHAR2(1024),
  PUBKEY                       VARCHAR2(2048),
  CERTIFICATE                  VARCHAR2(2048),
  SIGNCOUNTER                  NUMBER DEFAULT 0 NOT NULL,
  UVS                          VARCHAR2(64),
  STATUS                       VARCHAR2(20) DEFAULT 'O' NOT NULL,
  REGTIME                      TIMESTAMP DEFAULT sysdate NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT USERINFO_PK PRIMARY KEY (IDX)
);

CREATE TABLE CHALLENGE (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER(11,0),
  USERID                       VARCHAR2(512),
  SERVICENAME                  VARCHAR2(512),
  CHALLENGECODE                VARCHAR2(140),
  CREATETIME                   TIMESTAMP DEFAULT sysdate NOT NULL,
  LOGIDX                       NUMBER(11,0) DEFAULT -1 NOT NULL,
  UV                           VARCHAR2(20),
  CONSTRAINT CHALLENGE_PK PRIMARY KEY (IDX)
);

CREATE TABLE CRITERIA (
  IDX                          NUMBER(11,0) NOT NULL,
  AAID                         VARCHAR2(64),
  VENDORIDS                    VARCHAR2(32),
  USERVERIFICATION             NUMBER,
  KEYPROTECTION                NUMBER,
  MATCHERPROTECTION            NUMBER,
  ATTACHMENTHNUMBER            NUMBER,
  TCDISPLAY                    NUMBER,
  TCDISPLAYCONTENTTYPE         VARCHAR2(128) DEFAULT 'text/plain',
  AUTHENTICATIONALGORITHMS     VARCHAR2(64),
  ASSERTIONSCHEMES             VARCHAR2(64) DEFAULT 'UAFV1TLV',
  ATTESTATIONTYPES             VARCHAR2(64),
  AUTHENTICATORVERSION         NUMBER,
  METAHASH                     VARCHAR2(512),
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATETIME                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UPDATEDTIME                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT CRITERIA_PK PRIMARY KEY (IDX)
);

CREATE TABLE SIGN (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER,
  USERID                       VARCHAR2(512) NOT NULL,
  ASSERTION                    VARCHAR2(4000) NOT NULL,
  DN                           VARCHAR2(200),
  PLAINTEXT                    CLOB DEFAULT EMPTY_CLOB(),
  DATA                         VARCHAR2(2000) NOT NULL,
  SIGNATURE                    VARCHAR2(1000) NOT NULL,
  MEMO                         VARCHAR2(64),
  DEL                          VARCHAR2(1) DEFAULT 'N',
  CREATETIME                   TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT SIGN_PK PRIMARY KEY (IDX)
);

CREATE TABLE TRANSACTIONHASH (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER,
  USERID                       VARCHAR2(512),
  CONTENT                      CLOB DEFAULT EMPTY_CLOB(),
  CONTENTHASH                  VARCHAR2(64),
  CREATETIME                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT TRANSACTIONHASH_PK PRIMARY KEY (IDX)
);

CREATE TABLE TRANSACTION_CONFIRMATION (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER,
  USERID                       VARCHAR2(512),
  AAID                         VARCHAR2(64),
  CONTENTTYPE                  VARCHAR2(128),
  CONTENT                      CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT TRANSACTION_CONFIRMATION_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO2_METADATA (
  IDX                          NUMBER(11,0) NOT NULL,
  DESCRIPTION                  VARCHAR2(256),
  AAGUID                       VARCHAR2(256),
  ALTERNATIVEDESCRIPTIONS      VARCHAR2(2048),
  PROTOCOLFAMILY               VARCHAR2(128),
  AUTHENTICATORVERSION         NUMBER(38,0),
  UPV                          VARCHAR2(512),
  ASSERTIONSCHME               VARCHAR2(128),
  AUTHENTICATIONALGORITHM      NUMBER(38,0),
  PUBLICKEYALGANDENCODING      NUMBER(38,0),
  ATTESTATIONTYPES             VARCHAR2(2048),
  USERVERIFICATIONDETAILS      VARCHAR2(2048),
  KEYPROTECTION                NUMBER(38,0),
  MATCHERPROTECTION            NUMBER(38,0),
  CRYPTOSTRENGTH               NUMBER(38,0),
  OPERATINGENV                 VARCHAR2(2048),
  ATTACHMENTHINT               NUMBER(38,0),
  ISSECONDFACTORONLY           VARCHAR2(8),
  TCDISPLAY                    NUMBER(38,0),
  ATTESTATIONROOTCERTIFICATES  CLOB DEFAULT EMPTY_CLOB(),
  ICON                         CLOB DEFAULT EMPTY_CLOB(),
  SKI                          VARCHAR2(2048),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT FIDO2_METADATA_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO2_CREDENTIAL_PARAMS (
  IDX                          NUMBER(11,0) NOT NULL,
  CRED_TYPE                    VARCHAR2(32) DEFAULT 'public-key' NOT NULL,
  CRED_ALG                     NUMBER(38,0),
  STATUS                       VARCHAR2(1) DEFAULT 'T' NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT FIDO2_CREDENTIAL_PARAMS_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO2_DEMO_ACCESS_CODE (
  ACCESSCODE                   VARCHAR2(128) NOT NULL,
  VENDORNAME                   VARCHAR2(128),
  STARTTIME                    NUMBER,
  ENDTIME                      NUMBER,
  STATUS                       VARCHAR2(1) DEFAULT 'E' NOT NULL,
  NOTE                         VARCHAR2(300),
  ETC                          VARCHAR2(1024)
);

CREATE TABLE FIDO_LOGS (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  SERIALCODE                   VARCHAR2(128) NOT NULL,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT FIDO_LOGS_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO_LOGS_20210101 (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  SERIALCODE                   VARCHAR2(128) NOT NULL,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT FIDO_LOGS_20210101_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO_LOGS_20210102 (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  SERIALCODE                   VARCHAR2(128) NOT NULL,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT FIDO_LOGS_20210102_PK PRIMARY KEY (IDX)
);

CREATE TABLE BAK_FIDO_LOGS_BAK (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  SERIALCODE                   VARCHAR2(128) NOT NULL,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP,
  DEC_JSONDATA                 CLOB,
  CONSTRAINT BAK_FIDO_LOGS_BAK_PK PRIMARY KEY (IDX)
);

CREATE TABLE BAK_FIDO_LOGS_TEST (
  IDX                          NUMBER(11,0) NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  SERIALCODE                   VARCHAR2(128) NOT NULL,
  SERVICENAME                  VARCHAR2(512) NOT NULL,
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  TIMESTAMP,
  CONSTRAINT BAK_FIDO_LOGS_TEST_PK PRIMARY KEY (IDX)
);

CREATE TABLE FIDO_STATISTICS (
  COMPANY_IDX                  NUMBER DEFAULT 0 NOT NULL,
  SERVICE_NAME                 VARCHAR2(512) NOT NULL,
  GROUPBY                      VARCHAR2(32) NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  AUTH_S                       NUMBER DEFAULT 0,
  AUTH_F                       NUMBER DEFAULT 0,
  TC_S                         NUMBER DEFAULT 0,
  TC_F                         NUMBER DEFAULT 0,
  REG_S                        NUMBER DEFAULT 0,
  REG_F                        NUMBER DEFAULT 0,
  DEREG_S                      NUMBER DEFAULT 0,
  DEREG_F                      NUMBER DEFAULT 0
);

CREATE TABLE FIDO_STATISTICS_BAK (
  COMPANY_IDX                  NUMBER DEFAULT 0 NOT NULL,
  SERVICE_NAME                 VARCHAR2(512) NOT NULL,
  GROUPBY                      VARCHAR2(32) NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  AUTH_S                       NUMBER DEFAULT 0,
  AUTH_F                       NUMBER DEFAULT 0,
  TC_S                         NUMBER DEFAULT 0,
  TC_F                         NUMBER DEFAULT 0,
  REG_S                        NUMBER DEFAULT 0,
  REG_F                        NUMBER DEFAULT 0,
  DEREG_S                      NUMBER DEFAULT 0,
  DEREG_F                      NUMBER DEFAULT 0
);

CREATE TABLE CCFA_AUDIT_LOG (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER NOT NULL,
  COMPANY_NAME                 VARCHAR2(512) NOT NULL,
  TYPE                         VARCHAR2(32) NOT NULL,
  USER_ID                      VARCHAR2(64) NOT NULL,
  USER_NAME                    VARCHAR2(32) NOT NULL,
  MESSAGE                      VARCHAR2(4000) NOT NULL,
  IP                           VARCHAR2(15),
  UA                           VARCHAR2(2048),
  INTERGRITY_HASH              VARCHAR2(512),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_AUDIT_LOG_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_COMPANY (
  IDX                          NUMBER NOT NULL,
  COMPANY_NAME                 VARCHAR2(256),
  COMPANY_TYPE                 VARCHAR2(20),
  VENDOR_CODE                  VARCHAR2(20),
  CONTACT                      VARCHAR2(512),
  CONTACT_PHONE                VARCHAR2(50),
  CONTACT_PHONE2               VARCHAR2(50),
  CONTACT_ADDR                 VARCHAR2(2048),
  ENABLE_TYPE                  VARCHAR2(20),
  STARTTIME                    TIMESTAMP DEFAULT sysdate,
  ENDTIME                      TIMESTAMP DEFAULT TO_TIMESTAMP('9999-12-31 23:59:59','YYYY-MM-DD HH24:MI:SS'),
  MAX_APPID                    NUMBER DEFAULT 0 NOT NULL,
  MAX_APPSERVER                NUMBER DEFAULT 0 NOT NULL,
  MAX_USER                     NUMBER DEFAULT 0 NOT NULL,
  ETC                          VARCHAR2(4000),
  CREATOR                      NUMBER,
  UPDATOR                      NUMBER,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_COMPANY_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_CRITERIA (
  IDX                          NUMBER(11,0) NOT NULL,
  AAID                         VARCHAR2(64),
  METAHASH                     VARCHAR2(512),
  JSONDATA                     CLOB DEFAULT EMPTY_CLOB(),
  CREATETIME                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UPDATEDTIME                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT CCFA_CRITERIA_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_ERROR_TABLE (
  ERROR_CODE                   VARCHAR2(20) NOT NULL,
  ERROR_MESSAGE                VARCHAR2(1024),
  ERROR_COMMENT                VARCHAR2(4000),
  ERROR_TYPE                   VARCHAR2(20)
);

CREATE TABLE CCFA_EXCEPTIONS (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER DEFAULT 0,
  E_TYPE                       VARCHAR2(32),
  E_LEVEL                      VARCHAR2(32),
  EXCEPTION_MESSAGE            VARCHAR2(4000),
  EXCEPTION_DETAIL_MESSAGE     VARCHAR2(4000),
  EXCEPTION_DATA               CLOB DEFAULT EMPTY_CLOB(),
  CREATEDTIME                  VARCHAR2(64),
  CONSTRAINT CCFA_EXCEPTIONS_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_FDS_POLICY (
  COMPANY_IDX                  NUMBER NOT NULL,
  AND_IP                       VARCHAR2(4000),
  AND_TERM                     VARCHAR2(32),
  AND_DEVICE                   VARCHAR2(16),
  AND_COUNTRY                  VARCHAR2(16) NOT NULL,
  OR_IP                        VARCHAR2(4000),
  OR_TERM                      VARCHAR2(32),
  OR_COUNTRY                   VARCHAR2(16) NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_FDS_POLICY_PK PRIMARY KEY (COMPANY_IDX)
);

CREATE TABLE CCFA_FIDOCLIENT (
  SERVERCODE                   VARCHAR2(64) NOT NULL,
  SERVERNAME                   VARCHAR2(1024) NOT NULL,
  SERVERURL                    VARCHAR2(2048) NOT NULL,
  STATUS                       VARCHAR2(4) DEFAULT 'ON' NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL
);

CREATE TABLE CCFA_FIELDS (
  IDX                          NUMBER NOT NULL,
  FIELD_TABLE                  VARCHAR2(128) NOT NULL,
  FIELD_NAME                   VARCHAR2(256) NOT NULL,
  FIELD_TYPE                   VARCHAR2(128) NOT NULL,
  FIELD_TITLE                  VARCHAR2(128),
  PK                           NUMBER DEFAULT 0 NOT NULL,
  FK                           NUMBER DEFAULT 0 NOT NULL,
  OPTION_IDX                   NUMBER,
  EDITABLE                     NUMBER(38,0) DEFAULT 0 NOT NULL,
  CONSTRAINT CCFA_FIELDS_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_LICENSE (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER,
  COMPANY_NAME                 VARCHAR2(256),
  CONTACT_NAME                 VARCHAR2(128),
  CONTACT_PHONE                VARCHAR2(128),
  CONTACT_EMAIL                VARCHAR2(256),
  SERVICE_NAME                 VARCHAR2(256),
  ETC                          VARCHAR2(1024),
  LICENSE                      VARCHAR2(2048),
  FILE_PATH                    VARCHAR2(256),
  HASHVALUE                    VARCHAR2(256),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_LICENSE_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_MAILING (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER DEFAULT 0,
  "TO"                         VARCHAR2(4000),
  SUBJECT                      VARCHAR2(256),
  CONTENT                      VARCHAR2(4000),
  STATUS                       VARCHAR2(256),
  SMS_TO                       VARCHAR2(4000),
  SMS_CONTENT                  VARCHAR2(300),
  SMS_STATUS                   VARCHAR2(512),
  SENDTIME                     VARCHAR2(64),
  CONSTRAINT CCFA_MAILING_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_MANAGER (
  IDX                          NUMBER NOT NULL,
  USER_ID                      VARCHAR2(64) NOT NULL,
  USER_PW                      VARCHAR2(128) NOT NULL,
  USER_NM                      VARCHAR2(50),
  USER_EMAIL                   VARCHAR2(256),
  USER_PHONE                   VARCHAR2(20),
  COMPANY_IDX                  NUMBER DEFAULT 0 NOT NULL,
  STATUS                       VARCHAR2(20),
  LOGIN                        VARCHAR2(20) DEFAULT 'OFF-LINE',
  BLOCK_TIME                   TIMESTAMP,
  LAST_ACCESS                  TIMESTAMP,
  ETC                          VARCHAR2(2048),
  ALRAM_TYPE                   VARCHAR2(32) DEFAULT 'none' NOT NULL,
  ALRAM_LEVEL                  VARCHAR2(20) DEFAULT '0' NOT NULL,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_MANAGER_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_MANAGER_PW_POLICY (
  IDX                          NUMBER NOT NULL,
  USER_ID                      VARCHAR2(64) NOT NULL,
  ACCOUNT_LOCK                 VARCHAR2(1) DEFAULT 'N',
  PW_FAIL_CNT                  NUMBER DEFAULT 1,
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_MANAGER_PW_POLICY_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_MENU (
  IDX                          NUMBER(11,0) NOT NULL,
  MENU_NAME                    VARCHAR2(32),
  MENU_CODE                    VARCHAR2(32),
  MENU_PARENT_IDX              NUMBER DEFAULT 0 NOT NULL,
  MENU_ICON                    VARCHAR2(64),
  MENU_URL                     VARCHAR2(512),
  MENU_SEQ                     NUMBER,
  TBL_NAME                     VARCHAR2(128),
  PK                           VARCHAR2(128),
  VISIBLE                      VARCHAR2(20) DEFAULT 'true' NOT NULL,
  OPEN_TYPE                    VARCHAR2(20) DEFAULT 'open',
  STATISTICS                   VARCHAR2(20) DEFAULT 'N',
  READONLY                     VARCHAR2(20) DEFAULT 'N' NOT NULL,
  CONSTRAINT CCFA_MENU_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_OPTION (
  IDX                          NUMBER NOT NULL,
  OPTION_NAME                  VARCHAR2(64),
  OPTION_NOTE                  VARCHAR2(64),
  OPTION_TITLE                 VARCHAR2(64),
  CONSTRAINT CCFA_OPTION_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_OPTIONS (
  IDX                          NUMBER NOT NULL,
  OPTION_IDX                   NUMBER,
  OPTION_VALUE                 VARCHAR2(128),
  OPTION_TITLE                 VARCHAR2(128),
  OPTION_NOTE                  VARCHAR2(128),
  CONSTRAINT CCFA_OPTIONS_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_STATISTICS (
  IDX                          NUMBER NOT NULL,
  OWNER_IDX                    NUMBER,
  TYPE                         VARCHAR2(16),
  OPEN_TYPE                    VARCHAR2(20) DEFAULT 'close' NOT NULL,
  GROUP1_IDX                   NUMBER,
  GROUP2_IDX                   NUMBER,
  TARGET_IDX                   NUMBER,
  GRAPH_TYPE                   VARCHAR2(32),
  TITLE                        VARCHAR2(1024),
  CONTENT                      VARCHAR2(4000),
  PRIME_FIELD_IDX              NUMBER,
  "LIMIT"                      NUMBER,
  REALTIME                     VARCHAR2(20) DEFAULT 'custom' NOT NULL,
  ETC                          VARCHAR2(4000),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT CCFA_STATISTICS_PK PRIMARY KEY (IDX)
);

CREATE TABLE CCFA_STATISTICS_FILTER (
  STATISTICS_IDX               NUMBER NOT NULL,
  COLUMN_NAME                  VARCHAR2(256) NOT NULL,
  OP                           VARCHAR2(20) NOT NULL,
  "VALUE"                      VARCHAR2(1024) NOT NULL,
  TYPE                         VARCHAR2(20) DEFAULT 'F'
);

CREATE TABLE CCFA_STATISTICS_ORDER (
  STATISTICS_IDX               NUMBER NOT NULL,
  COLUMN_NAME                  VARCHAR2(128) NOT NULL,
  TYPE                         VARCHAR2(20) NOT NULL
);

CREATE TABLE CCFA_SYSTEM_INFO (
  PROP_KEY                     VARCHAR2(128) NOT NULL,
  PROP_VALUE                   VARCHAR2(1024),
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate,
  CONSTRAINT CCFA_SYSTEM_INFO_PK PRIMARY KEY (PROP_KEY)
);

CREATE TABLE CCFA_SYSTEM_PROP (
  PROP_KEY                     VARCHAR2(128) NOT NULL,
  COMPANY_IDX                  NUMBER DEFAULT 0 NOT NULL,
  PROP_VALUE                   VARCHAR2(4000),
  SHARE_TYPE                   VARCHAR2(20) DEFAULT 'NO',
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate
);

CREATE TABLE AWS_INFO (
  IDX                          NUMBER NOT NULL,
  COMPANY_IDX                  NUMBER(38,0) NOT NULL,
  AMZ_TOKEN                    VARCHAR2(512),
  CUSTOMER_ID                  VARCHAR2(512),
  PRODUCT_CODE                 VARCHAR2(512),
  CUSTOMER_AWS_ACCOUNT_ID      VARCHAR2(512),
  DIMENSION                    VARCHAR2(100),
  EXPIRATIONDATE               VARCHAR2(100),
  CREATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  UPDATEDTIME                  TIMESTAMP DEFAULT sysdate NOT NULL,
  CONSTRAINT AWS_INFO_PK PRIMARY KEY (IDX)
);

-- ---------- 고유 제약 / 보조 인덱스 (ERD 인덱스 목록) ----------
ALTER TABLE CCFA_MANAGER ADD CONSTRAINT CCFA_MANAGER_USER_ID_UK UNIQUE (USER_ID);
CREATE INDEX CRITERIA_AAID ON CRITERIA (AAID);
CREATE INDEX CCFA_CRITERIA_AAID ON CCFA_CRITERIA (AAID);
CREATE INDEX FIDO2_CREDENTIAL_PARAMS_ALG ON FIDO2_CREDENTIAL_PARAMS (CRED_ALG);

-- ---------- 시퀀스 + IDX 컬럼 기본값 (IDX 보유 29개 테이블) ----------
-- 시드 데이터가 1~999 를 쓰므로 1000 부터 시작한다.
CREATE SEQUENCE APPID_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE APPID MODIFY IDX DEFAULT APPID_SEQ.NEXTVAL;
CREATE SEQUENCE APPSERVER_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE APPSERVER MODIFY IDX DEFAULT APPSERVER_SEQ.NEXTVAL;
CREATE SEQUENCE USERINFO_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE USERINFO MODIFY IDX DEFAULT USERINFO_SEQ.NEXTVAL;
CREATE SEQUENCE CHALLENGE_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CHALLENGE MODIFY IDX DEFAULT CHALLENGE_SEQ.NEXTVAL;
CREATE SEQUENCE CRITERIA_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CRITERIA MODIFY IDX DEFAULT CRITERIA_SEQ.NEXTVAL;
CREATE SEQUENCE SIGN_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE SIGN MODIFY IDX DEFAULT SIGN_SEQ.NEXTVAL;
CREATE SEQUENCE TRANSACTIONHASH_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE TRANSACTIONHASH MODIFY IDX DEFAULT TRANSACTIONHASH_SEQ.NEXTVAL;
CREATE SEQUENCE TRANSACTION_CONFIRMATION_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE TRANSACTION_CONFIRMATION MODIFY IDX DEFAULT TRANSACTION_CONFIRMATION_SEQ.NEXTVAL;
CREATE SEQUENCE FIDO2_METADATA_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE FIDO2_METADATA MODIFY IDX DEFAULT FIDO2_METADATA_SEQ.NEXTVAL;
CREATE SEQUENCE FIDO2_CREDENTIAL_PARAMS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE FIDO2_CREDENTIAL_PARAMS MODIFY IDX DEFAULT FIDO2_CREDENTIAL_PARAMS_SEQ.NEXTVAL;
CREATE SEQUENCE FIDO_LOGS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE FIDO_LOGS MODIFY IDX DEFAULT FIDO_LOGS_SEQ.NEXTVAL;
CREATE SEQUENCE FIDO_LOGS_20210101_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE FIDO_LOGS_20210101 MODIFY IDX DEFAULT FIDO_LOGS_20210101_SEQ.NEXTVAL;
CREATE SEQUENCE FIDO_LOGS_20210102_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE FIDO_LOGS_20210102 MODIFY IDX DEFAULT FIDO_LOGS_20210102_SEQ.NEXTVAL;
CREATE SEQUENCE BAK_FIDO_LOGS_BAK_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE BAK_FIDO_LOGS_BAK MODIFY IDX DEFAULT BAK_FIDO_LOGS_BAK_SEQ.NEXTVAL;
CREATE SEQUENCE BAK_FIDO_LOGS_TEST_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE BAK_FIDO_LOGS_TEST MODIFY IDX DEFAULT BAK_FIDO_LOGS_TEST_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_AUDIT_LOG_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_AUDIT_LOG MODIFY IDX DEFAULT CCFA_AUDIT_LOG_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_COMPANY_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_COMPANY MODIFY IDX DEFAULT CCFA_COMPANY_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_CRITERIA_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_CRITERIA MODIFY IDX DEFAULT CCFA_CRITERIA_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_EXCEPTIONS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_EXCEPTIONS MODIFY IDX DEFAULT CCFA_EXCEPTIONS_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_FIELDS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_FIELDS MODIFY IDX DEFAULT CCFA_FIELDS_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_LICENSE_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_LICENSE MODIFY IDX DEFAULT CCFA_LICENSE_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_MAILING_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_MAILING MODIFY IDX DEFAULT CCFA_MAILING_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_MANAGER_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_MANAGER MODIFY IDX DEFAULT CCFA_MANAGER_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_MANAGER_PW_POLICY_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_MANAGER_PW_POLICY MODIFY IDX DEFAULT CCFA_MANAGER_PW_POLICY_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_MENU_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_MENU MODIFY IDX DEFAULT CCFA_MENU_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_OPTION_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_OPTION MODIFY IDX DEFAULT CCFA_OPTION_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_OPTIONS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_OPTIONS MODIFY IDX DEFAULT CCFA_OPTIONS_SEQ.NEXTVAL;
CREATE SEQUENCE CCFA_STATISTICS_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE CCFA_STATISTICS MODIFY IDX DEFAULT CCFA_STATISTICS_SEQ.NEXTVAL;
CREATE SEQUENCE AWS_INFO_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
ALTER TABLE AWS_INFO MODIFY IDX DEFAULT AWS_INFO_SEQ.NEXTVAL;

COMMIT;
```

- [x] **Step 3: 02-seed.sql 작성**

`docker/init/02-seed.sql` — 아래 내용 전체.

```sql
-- ============================================================
-- 로컬 검증 전용 시드 데이터. 운영 DB에 절대 적용하지 말 것.
-- 계정: superuser / Admin1234!  (COMPANY_IDX 0, SUPER)
--       kbadmin   / Company1234! (COMPANY_IDX 1, COMPANY)
-- ============================================================
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = KBFIDO;

-- 고객사: IDX 0 은 전역(superuser) 레코드. 없으면 로그인 조인이 깨진다.
INSERT INTO CCFA_COMPANY (IDX, COMPANY_NAME, COMPANY_TYPE, VENDOR_CODE, CONTACT, ENABLE_TYPE, MAX_APPID, MAX_APPSERVER, MAX_USER)
VALUES (0, '전역(시스템)', 'SYSTEM', 'CC', 'admin@crosscert.local', 'Y', 0, 0, 0);
INSERT INTO CCFA_COMPANY (IDX, COMPANY_NAME, COMPANY_TYPE, VENDOR_CODE, CONTACT, CONTACT_PHONE, ENABLE_TYPE, MAX_APPID, MAX_APPSERVER, MAX_USER)
VALUES (1, 'KB국민은행', 'BANK', 'KB', 'fido@kb.local', '02-0000-0000', 'Y', 10, 10, 100000);
INSERT INTO CCFA_COMPANY (IDX, COMPANY_NAME, COMPANY_TYPE, VENDOR_CODE, CONTACT, ENABLE_TYPE, MAX_APPID, MAX_APPSERVER, MAX_USER)
VALUES (2, '테스트고객사', 'TEST', 'TS', 'test@test.local', 'N', 1, 1, 100);

-- 운영자 (USER_PW = SHA-256 hex)
INSERT INTO CCFA_MANAGER (IDX, USER_ID, USER_PW, USER_NM, USER_EMAIL, COMPANY_IDX, STATUS)
VALUES (1, 'superuser', '5ce41ada64f1e8ffb0acfaafa622b141438f3a5777785e7f0b830fb73e40d3d6', '슈퍼관리자', 'super@crosscert.local', 0, '활성');
INSERT INTO CCFA_MANAGER (IDX, USER_ID, USER_PW, USER_NM, USER_EMAIL, COMPANY_IDX, STATUS)
VALUES (2, 'kbadmin', 'c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461', 'KB운영자', 'kbadmin@kb.local', 1, '활성');
INSERT INTO CCFA_MANAGER (IDX, USER_ID, USER_PW, USER_NM, COMPANY_IDX, STATUS)
VALUES (3, 'disabled', 'c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461', '비활성운영자', 1, '비활성');
INSERT INTO CCFA_MANAGER_PW_POLICY (IDX, USER_ID, ACCOUNT_LOCK, PW_FAIL_CNT) VALUES (1, 'superuser', 'N', 0);
INSERT INTO CCFA_MANAGER_PW_POLICY (IDX, USER_ID, ACCOUNT_LOCK, PW_FAIL_CNT) VALUES (2, 'kbadmin', 'N', 0);

-- 시스템 설정
INSERT INTO CCFA_SYSTEM_PROP (PROP_KEY, COMPANY_IDX, PROP_VALUE, SHARE_TYPE) VALUES ('PW_FAIL_LIMIT', 0, '5', 'YES');
INSERT INTO CCFA_SYSTEM_PROP (PROP_KEY, COMPANY_IDX, PROP_VALUE, SHARE_TYPE) VALUES ('SESSION_TIMEOUT', 0, '30', 'NO');
INSERT INTO CCFA_SYSTEM_PROP (PROP_KEY, COMPANY_IDX, PROP_VALUE, SHARE_TYPE) VALUES ('SERVICE_NAME', 1, 'kbstar', 'NO');
INSERT INTO CCFA_SYSTEM_INFO (PROP_KEY, PROP_VALUE) VALUES ('VERSION', '1.0.0');
INSERT INTO CCFA_SYSTEM_INFO (PROP_KEY, PROP_VALUE) VALUES ('BUILD_DATE', '2026-09-16');
INSERT INTO CCFA_ERROR_TABLE (ERROR_CODE, ERROR_MESSAGE, ERROR_COMMENT, ERROR_TYPE) VALUES ('1200', 'OK', '정상 처리', 'INFO');
INSERT INTO CCFA_ERROR_TABLE (ERROR_CODE, ERROR_MESSAGE, ERROR_COMMENT, ERROR_TYPE) VALUES ('1498', 'INVALID_SIGNATURE', '서명 검증 실패', 'ERROR');
INSERT INTO CCFA_FIDOCLIENT (SERVERCODE, SERVERNAME, SERVERURL, STATUS) VALUES ('FIDO01', 'FIDO 서버 1', 'https://fido1.internal:8443', 'ON');
INSERT INTO CCFA_FIDOCLIENT (SERVERCODE, SERVERNAME, SERVERURL, STATUS) VALUES ('FIDO02', 'FIDO 서버 2', 'https://fido2.internal:8443', 'OFF');

-- FIDO 인증 도메인
INSERT INTO APPID (IDX, COMPANY_IDX, APPID, MEMO, STATUS, DEVICE, DEVICE_DEFAULT, SERVICENAME)
VALUES (1, 1, 'https://kbstar.com/facets.json', 'KB 스타뱅킹', 'use', 'android', 'T', 'kbstar');
INSERT INTO APPID (IDX, COMPANY_IDX, APPID, MEMO, STATUS, DEVICE, DEVICE_DEFAULT, SERVICENAME)
VALUES (2, 1, 'ios:bundle-id:com.kbstar.kbbank', 'KB 스타뱅킹 iOS', 'use', 'ios', 'F', 'kbstar');
INSERT INTO APPID (IDX, COMPANY_IDX, APPID, MEMO, STATUS, DEVICE, DEVICE_DEFAULT, SERVICENAME)
VALUES (3, 2, 'https://test.local/facets.json', '테스트', 'unuse', 'android', 'F', 'testsvc');
INSERT INTO APPSERVER (IDX, COMPANY_IDX, MEMBER_CODE, MEMBER_ID, TYPE, NOTE) VALUES (1, 1, 'KB001', 'kbserver01', 'use', '운영 서버');
INSERT INTO APPSERVER (IDX, COMPANY_IDX, MEMBER_CODE, MEMBER_ID, TYPE, NOTE) VALUES (2, 2, 'TS001', 'tsserver01', 'use', '테스트');
INSERT INTO USERINFO (IDX, COMPANY_IDX, BIO_TYPE, SERVICENAME, USERID, AAID, AUTHENTICATORVERSION, KEYID, PUBKEY, CERTIFICATE, SIGNCOUNTER, UVS, STATUS)
VALUES (1, 1, 1, 'kbstar', 'user001', '0012#0001', 1, 'keyid-001', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-SAMPLE-001', 'MIIB-CERT-SAMPLE-001', 12, '2', 'O');
INSERT INTO USERINFO (IDX, COMPANY_IDX, BIO_TYPE, SERVICENAME, USERID, AAID, AUTHENTICATORVERSION, KEYID, PUBKEY, CERTIFICATE, SIGNCOUNTER, UVS, STATUS)
VALUES (2, 1, 2, 'kbstar', 'user002', '0012#0002', 1, 'keyid-002', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-SAMPLE-002', 'MIIB-CERT-SAMPLE-002', 3, '16', 'O');
INSERT INTO USERINFO (IDX, COMPANY_IDX, BIO_TYPE, SERVICENAME, USERID, AAID, AUTHENTICATORVERSION, KEYID, PUBKEY, CERTIFICATE, SIGNCOUNTER, UVS, STATUS)
VALUES (3, 2, 1, 'testsvc', 'tester', '0012#0001', 1, 'keyid-003', 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-SAMPLE-003', 'MIIB-CERT-SAMPLE-003', 0, '2', 'X');
INSERT INTO CHALLENGE (IDX, COMPANY_IDX, USERID, SERVICENAME, CHALLENGECODE, LOGIDX, UV) VALUES (1, 1, 'user001', 'kbstar', 'chal-0001', -1, 'fingerprint');
INSERT INTO CHALLENGE (IDX, COMPANY_IDX, USERID, SERVICENAME, CHALLENGECODE, LOGIDX, UV) VALUES (2, 1, 'user002', 'kbstar', 'chal-0002', 1, 'face');
INSERT INTO CRITERIA (IDX, AAID, VENDORIDS, USERVERIFICATION, KEYPROTECTION, MATCHERPROTECTION, ATTACHMENTHNUMBER, TCDISPLAY, AUTHENTICATIONALGORITHMS, ATTESTATIONTYPES, AUTHENTICATORVERSION, METAHASH, JSONDATA)
VALUES (1, '0012#0001', '0012', 2, 2, 2, 1, 1, '1', '15879', 1, 'hash-0001', '{"aaid":"0012#0001","description":"Sample fingerprint"}');
INSERT INTO CRITERIA (IDX, AAID, VENDORIDS, USERVERIFICATION, KEYPROTECTION, MATCHERPROTECTION, ATTACHMENTHNUMBER, TCDISPLAY, AUTHENTICATIONALGORITHMS, ATTESTATIONTYPES, AUTHENTICATORVERSION, METAHASH, JSONDATA)
VALUES (2, '0012#0002', '0012', 16, 2, 2, 1, 1, '1', '15879', 1, 'hash-0002', '{"aaid":"0012#0002","description":"Sample face"}');
INSERT INTO CCFA_CRITERIA (IDX, AAID, METAHASH, JSONDATA) VALUES (1, '0012#0001', 'hash-0001', '{"aaid":"0012#0001","policy":"admin"}');
INSERT INTO SIGN (IDX, COMPANY_IDX, USERID, ASSERTION, DN, PLAINTEXT, DATA, SIGNATURE, MEMO, DEL)
VALUES (1, 1, 'user001', 'assertion-sample-001', 'CN=user001', '이체 100,000원 승인', 'data-001', 'sig-001', '이체', 'N');
INSERT INTO TRANSACTIONHASH (IDX, COMPANY_IDX, USERID, CONTENT, CONTENTHASH) VALUES (1, 1, 'user001', '이체 100,000원', 'a1b2c3');
INSERT INTO TRANSACTION_CONFIRMATION (IDX, COMPANY_IDX, USERID, AAID, CONTENTTYPE, CONTENT) VALUES (1, 1, 'user001', '0012#0001', 'text/plain', '거래확인 내용');

-- FIDO2
INSERT INTO FIDO2_METADATA (IDX, DESCRIPTION, AAGUID, PROTOCOLFAMILY, AUTHENTICATORVERSION, UPV, ASSERTIONSCHME, AUTHENTICATIONALGORITHM, PUBLICKEYALGANDENCODING, ATTESTATIONTYPES, KEYPROTECTION, MATCHERPROTECTION, CRYPTOSTRENGTH, ATTACHMENTHINT, ISSECONDFACTORONLY, TCDISPLAY, ATTESTATIONROOTCERTIFICATES, ICON, SKI)
VALUES (1, 'YubiKey 5 Series', 'cb69481e-8ff7-4039-93ec-0a2729a154a8', 'fido2', 50100, '[{"major":1,"minor":0}]', 'FIDOV2', 1, 260, 'basic_full', 10, 4, 128, 2, 'false', 0, 'MIIC-ROOT-CERT-SAMPLE', 'data:image/png;base64,iVBORw0KGgo=', 'ski-0001');
INSERT INTO FIDO2_METADATA (IDX, DESCRIPTION, AAGUID, PROTOCOLFAMILY, AUTHENTICATORVERSION, ASSERTIONSCHME, AUTHENTICATIONALGORITHM, PUBLICKEYALGANDENCODING, ATTESTATIONTYPES, ISSECONDFACTORONLY)
VALUES (2, 'Samsung Pass', '53414d53-554e-4700-0000-000000000000', 'fido2', 1, 'FIDOV2', 1, 260, 'basic_full', 'false');
INSERT INTO FIDO2_CREDENTIAL_PARAMS (IDX, CRED_TYPE, CRED_ALG, STATUS) VALUES (1, 'public-key', -7, 'T');
INSERT INTO FIDO2_CREDENTIAL_PARAMS (IDX, CRED_TYPE, CRED_ALG, STATUS) VALUES (2, 'public-key', -257, 'T');
INSERT INTO FIDO2_CREDENTIAL_PARAMS (IDX, CRED_TYPE, CRED_ALG, STATUS) VALUES (3, 'public-key', -8, 'F');
INSERT INTO FIDO2_DEMO_ACCESS_CODE (ACCESSCODE, VENDORNAME, STARTTIME, ENDTIME, STATUS, NOTE) VALUES ('DEMO-0001', '벤더A', 1767225600, 1798761600, 'E', '데모용');
INSERT INTO FIDO2_DEMO_ACCESS_CODE (ACCESSCODE, VENDORNAME, STARTTIME, ENDTIME, STATUS, NOTE) VALUES ('DEMO-0002', '벤더B', 1767225600, 1770000000, 'D', '만료');

-- 로그
INSERT INTO FIDO_LOGS (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME)
VALUES (1, 1, 'SN-0001', 'kbstar', '{"op":"Auth","userid":"user001","result":"1200"}', SYSTIMESTAMP - INTERVAL '1' DAY);
INSERT INTO FIDO_LOGS (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME)
VALUES (2, 1, 'SN-0002', 'kbstar', '{"op":"Reg","userid":"user002","result":"1200"}', SYSTIMESTAMP - INTERVAL '2' HOUR);
INSERT INTO FIDO_LOGS (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME)
VALUES (3, 2, 'SN-0003', 'testsvc', '{"op":"Auth","userid":"tester","result":"1498"}', SYSTIMESTAMP);
INSERT INTO CCFA_AUDIT_LOG (IDX, COMPANY_IDX, COMPANY_NAME, TYPE, USER_ID, USER_NAME, MESSAGE, IP, UA, INTERGRITY_HASH)
VALUES (1, 0, '전역(시스템)', 'LOGIN', 'superuser', '슈퍼관리자', '로그인 성공', '127.0.0.1', 'seed', 'seed-hash-1');
INSERT INTO CCFA_AUDIT_LOG (IDX, COMPANY_IDX, COMPANY_NAME, TYPE, USER_ID, USER_NAME, MESSAGE, IP, UA, INTERGRITY_HASH)
VALUES (2, 1, 'KB국민은행', 'UPDATE', 'kbadmin', 'KB운영자', 'APPID UPDATE 1', '10.0.0.5', 'seed', 'seed-hash-2');
INSERT INTO CCFA_EXCEPTIONS (IDX, COMPANY_IDX, E_TYPE, E_LEVEL, EXCEPTION_MESSAGE, EXCEPTION_DETAIL_MESSAGE, EXCEPTION_DATA, CREATEDTIME)
VALUES (1, 1, 'AUTH', 'ERROR', 'Invalid signature', 'signature mismatch for user001', '{"trace":"..."}', '2026-09-15 10:00:00');
INSERT INTO CCFA_MAILING (IDX, COMPANY_IDX, "TO", SUBJECT, CONTENT, STATUS, SMS_TO, SMS_CONTENT, SMS_STATUS, SENDTIME)
VALUES (1, 1, 'ops@kb.local', '[FIDO] 장애 알림', '인증 실패율 증가', 'SENT', '010-0000-0000', '인증 실패율 증가', 'SENT', '2026-09-15 10:05:00');

-- 어드민 메타 (데이터로만 취급)
INSERT INTO CCFA_MENU (IDX, MENU_NAME, MENU_CODE, MENU_PARENT_IDX, MENU_ICON, MENU_URL, MENU_SEQ, TBL_NAME, PK, VISIBLE, OPEN_TYPE, STATISTICS, READONLY)
VALUES (1, 'FIDO', 'FIDO', 0, 'shield', NULL, 1, NULL, NULL, 'true', 'open', 'N', 'N');
INSERT INTO CCFA_MENU (IDX, MENU_NAME, MENU_CODE, MENU_PARENT_IDX, MENU_ICON, MENU_URL, MENU_SEQ, TBL_NAME, PK, VISIBLE, OPEN_TYPE, STATISTICS, READONLY)
VALUES (2, '앱 ID', 'APPID', 1, NULL, '/appids', 1, 'APPID', 'IDX', 'true', 'open', 'N', 'N');
INSERT INTO CCFA_OPTION (IDX, OPTION_NAME, OPTION_NOTE, OPTION_TITLE) VALUES (1, 'STATUS', '사용 상태', '상태');
INSERT INTO CCFA_OPTIONS (IDX, OPTION_IDX, OPTION_VALUE, OPTION_TITLE, OPTION_NOTE) VALUES (1, 1, 'use', '사용', NULL);
INSERT INTO CCFA_OPTIONS (IDX, OPTION_IDX, OPTION_VALUE, OPTION_TITLE, OPTION_NOTE) VALUES (2, 1, 'unuse', '미사용', NULL);
INSERT INTO CCFA_FIELDS (IDX, FIELD_TABLE, FIELD_NAME, FIELD_TYPE, FIELD_TITLE, PK, FK, OPTION_IDX, EDITABLE)
VALUES (1, 'APPID', 'STATUS', 'select', '상태', 0, 0, 1, 1);
INSERT INTO CCFA_LICENSE (IDX, COMPANY_IDX, COMPANY_NAME, CONTACT_NAME, CONTACT_PHONE, CONTACT_EMAIL, SERVICE_NAME, LICENSE, HASHVALUE)
VALUES (1, 1, 'KB국민은행', '홍길동', '02-0000-0000', 'fido@kb.local', 'kbstar', 'LICENSE-SAMPLE-KEY', 'hash');
INSERT INTO CCFA_FDS_POLICY (COMPANY_IDX, AND_IP, AND_TERM, AND_DEVICE, AND_COUNTRY, OR_IP, OR_TERM, OR_COUNTRY)
VALUES (1, '10.0.0.0/8', '30', 'mobile', 'KR', NULL, NULL, 'KR');
INSERT INTO CCFA_STATISTICS (IDX, OWNER_IDX, TYPE, OPEN_TYPE, GRAPH_TYPE, TITLE, "LIMIT", REALTIME)
VALUES (1, 1, 'bar', 'open', 'bar', '일별 인증', 30, 'custom');
INSERT INTO CCFA_STATISTICS_FILTER (STATISTICS_IDX, COLUMN_NAME, OP, "VALUE", TYPE) VALUES (1, 'SERVICE_NAME', '=', 'kbstar', 'F');
INSERT INTO CCFA_STATISTICS_ORDER (STATISTICS_IDX, COLUMN_NAME, TYPE) VALUES (1, 'CREATEDTIME', 'DESC');
INSERT INTO AWS_INFO (IDX, COMPANY_IDX, AMZ_TOKEN, CUSTOMER_ID, PRODUCT_CODE, CUSTOMER_AWS_ACCOUNT_ID, DIMENSION, EXPIRATIONDATE)
VALUES (1, 2, 'amz-token-sample', 'cust-0001', 'prod-0001', '123456789012', 'users', '2027-12-31');

-- 최근 30일 일별 통계 (GROUPBY = 'day')
BEGIN
  FOR i IN 0..29 LOOP
    INSERT INTO FIDO_STATISTICS (COMPANY_IDX, SERVICE_NAME, GROUPBY, CREATEDTIME, AUTH_S, AUTH_F, TC_S, TC_F, REG_S, REG_F, DEREG_S, DEREG_F)
    VALUES (1, 'kbstar', 'day', TRUNC(SYSDATE) - i, 1000 + MOD(i * 37, 400), MOD(i * 7, 40), 200 + MOD(i * 13, 90), MOD(i * 3, 10), 50 + MOD(i * 11, 60), MOD(i, 5), MOD(i * 5, 20), MOD(i, 3));
    INSERT INTO FIDO_STATISTICS (COMPANY_IDX, SERVICE_NAME, GROUPBY, CREATEDTIME, AUTH_S, AUTH_F, TC_S, TC_F, REG_S, REG_F, DEREG_S, DEREG_F)
    VALUES (2, 'testsvc', 'day', TRUNC(SYSDATE) - i, 10 + MOD(i, 9), MOD(i, 4), 0, 0, MOD(i, 3), 0, 0, 0);
  END LOOP;
END;
/
COMMIT;
```

- [x] **Step 4: docker/README.md 작성**

```markdown
# 로컬 검증용 Oracle

**이 디렉터리의 SQL은 로컬 검증 전용이다. 운영 DB에 절대 적용하지 않는다.**

- 기동: `docker compose -f docker/docker-compose.yml up -d` (최초 기동 시 이미지 다운로드 + 초기화에 2~3분)
- 상태: `docker compose -f docker/docker-compose.yml ps` 에서 `healthy` 확인
- 접속: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`, `kbfido / kbfido`
- 초기화 다시 하기: `docker compose -f docker/docker-compose.yml down -v` 후 다시 `up -d`
- 시퀀스 이름은 임시(`<TABLE>_SEQ`). 실제 이름을 받으면 `01-schema.sql`과 엔티티의 `@SequenceGenerator.sequenceName`을 함께 교체한다.
```

- [x] **Step 5: 컨테이너 기동과 스키마 검증**

Docker Desktop이 실행 중이어야 한다.

```bash
docker compose -f docker/docker-compose.yml up -d
# healthy 가 될 때까지 대기 (최대 3분)
until [ "$(docker inspect -f '{{.State.Health.Status}}' fido-admin-oracle)" = "healthy" ]; do sleep 5; done
docker exec fido-admin-oracle sqlplus -s kbfido/kbfido@localhost/FREEPDB1 <<'SQL'
SET PAGESIZE 0 FEEDBACK OFF
SELECT COUNT(*) FROM user_tables;
SELECT COUNT(*) FROM user_tab_columns;
SELECT COUNT(*) FROM user_sequences;
SELECT COUNT(*) FROM CCFA_MANAGER;
SELECT COUNT(*) FROM FIDO_STATISTICS;
SQL
```

Expected 출력 순서대로: `39`, `354`, `29`, `3`, `60`. 다르면 init 로그(`docker logs fido-admin-oracle`)에서 ORA- 오류를 찾아 SQL을 고친다.

- [x] **Step 6: 커밋**

```bash
git add docker/
git commit -m "chore: 로컬 검증용 Docker Oracle 스키마와 시드 추가

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 3: SHA-256 비밀번호 인코더

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/auth/Sha256PasswordEncoder.java`
- Test: `src/test/java/com/crosscert/fidoadmin/auth/Sha256PasswordEncoderTest.java`

**Interfaces:**
- Produces: `class Sha256PasswordEncoder implements org.springframework.security.crypto.password.PasswordEncoder` — `encode(CharSequence)`는 소문자 hex 64자, `matches(raw, encoded)`는 대소문자 무시 비교. `static String sha256Hex(String)` 유틸(감사 로그 해시에서 재사용).

- [x] **Step 1: 실패하는 테스트 작성**

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256PasswordEncoderTest {

    private final Sha256PasswordEncoder encoder = new Sha256PasswordEncoder();

    @Test
    void encodeProducesLowercaseHex64() {
        // printf 'Admin1234!' | shasum -a 256
        assertThat(encoder.encode("Admin1234!"))
            .isEqualTo("5ce41ada64f1e8ffb0acfaafa622b141438f3a5777785e7f0b830fb73e40d3d6");
    }

    @Test
    void matchesIgnoresCaseOfStoredHash() {
        String upper = "5CE41ADA64F1E8FFB0ACFAAFA622B141438F3A5777785E7F0B830FB73E40D3D6";
        assertThat(encoder.matches("Admin1234!", upper)).isTrue();
        assertThat(encoder.matches("wrong", upper)).isFalse();
    }

    @Test
    void matchesReturnsFalseForNullOrBlankStored() {
        assertThat(encoder.matches("Admin1234!", null)).isFalse();
        assertThat(encoder.matches("Admin1234!", "")).isFalse();
    }

    @Test
    void sha256HexHandlesKorean() {
        assertThat(Sha256PasswordEncoder.sha256Hex("한글")).hasSize(64).matches("[0-9a-f]+");
    }
}
```

- [x] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.Sha256PasswordEncoderTest' --no-daemon`
Expected: 컴파일 오류 `Sha256PasswordEncoder` 없음.

- [x] **Step 3: 구현**

```java
package com.crosscert.fidoadmin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 기존 CCFA_MANAGER.USER_PW 형식(salt 없는 SHA-256 hex 64자)과 호환되는 인코더.
 * 저장 방식 강화(salt/bcrypt)는 다른 시스템과의 호환 문제로 범위 밖이다.
 */
public class Sha256PasswordEncoder implements PasswordEncoder {

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return sha256Hex(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return encode(rawPassword).equalsIgnoreCase(encodedPassword.trim());
    }
}
```

- [x] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.Sha256PasswordEncoderTest' --no-daemon`
Expected: 4개 통과.

- [x] **Step 5: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/auth/Sha256PasswordEncoder.java src/test/java/com/crosscert/fidoadmin/auth/Sha256PasswordEncoderTest.java
git commit -m "feat: 기존 해시 호환 SHA-256 비밀번호 인코더

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 4: 엔티티 39개와 ERD 정합성 테스트

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/config/JpaConfig.java`
- Create: 엔티티 39개 (아래 매핑표의 경로)
- Create: `src/test/resources/erd-columns.txt` (`docs/erd/kbfido-columns.txt` 복사본)
- Test: `src/test/java/com/crosscert/fidoadmin/erd/ErdColumns.java`, `src/test/java/com/crosscert/fidoadmin/erd/ErdConformanceTest.java`

**Interfaces:**
- Produces: 39개 `@Entity` 클래스. 모두 Lombok `@Getter @Setter @NoArgsConstructor`, 모든 필드에 `@Column(name = "...")` 명시. IDX PK는 `Long idx`. 복합키는 `@EmbeddedId` + `<Entity>Id` 클래스(`@Embeddable`, `equals/hashCode` 포함, `Serializable`).
- 후속 태스크가 쓰는 이름: `CcfaCompany`, `CcfaManager`, `CcfaManagerPwPolicy`, `CcfaAuditLog`, `CcfaSystemProp`/`CcfaSystemPropId`, `FidoStatistics`/`FidoStatisticsId`.

### 매핑표 (클래스 → 테이블 → 식별자)

| 패키지 `com.crosscert.fidoadmin.` | 클래스 | 테이블 | 식별자 |
|---|---|---|---|
| fido.entity | `Appid` | APPID | SEQ `APPID_SEQ` |
| fido.entity | `Appserver` | APPSERVER | SEQ `APPSERVER_SEQ` |
| fido.entity | `Userinfo` | USERINFO | SEQ `USERINFO_SEQ` |
| fido.entity | `Challenge` | CHALLENGE | SEQ `CHALLENGE_SEQ` |
| fido.entity | `Criteria` | CRITERIA | SEQ `CRITERIA_SEQ` |
| fido.entity | `Sign` | SIGN | SEQ `SIGN_SEQ` |
| fido.entity | `Transactionhash` | TRANSACTIONHASH | SEQ `TRANSACTIONHASH_SEQ` |
| fido.entity | `TransactionConfirmation` | TRANSACTION_CONFIRMATION | SEQ `TRANSACTION_CONFIRMATION_SEQ` |
| fido2.entity | `Fido2Metadata` | FIDO2_METADATA | SEQ `FIDO2_METADATA_SEQ` |
| fido2.entity | `Fido2CredentialParams` | FIDO2_CREDENTIAL_PARAMS | SEQ `FIDO2_CREDENTIAL_PARAMS_SEQ` |
| fido2.entity | `Fido2DemoAccessCode` | FIDO2_DEMO_ACCESS_CODE | `@Id String accesscode` |
| log.entity | `FidoLogs` | FIDO_LOGS | SEQ `FIDO_LOGS_SEQ` |
| log.entity | `FidoLogs20210101` | FIDO_LOGS_20210101 | SEQ `FIDO_LOGS_20210101_SEQ` |
| log.entity | `FidoLogs20210102` | FIDO_LOGS_20210102 | SEQ `FIDO_LOGS_20210102_SEQ` |
| log.entity | `BakFidoLogsBak` | BAK_FIDO_LOGS_BAK | SEQ `BAK_FIDO_LOGS_BAK_SEQ` |
| log.entity | `BakFidoLogsTest` | BAK_FIDO_LOGS_TEST | SEQ `BAK_FIDO_LOGS_TEST_SEQ` |
| log.entity | `CcfaAuditLog` | CCFA_AUDIT_LOG | SEQ `CCFA_AUDIT_LOG_SEQ` |
| log.entity | `CcfaExceptions` | CCFA_EXCEPTIONS | SEQ `CCFA_EXCEPTIONS_SEQ` |
| log.entity | `CcfaMailing` | CCFA_MAILING | SEQ `CCFA_MAILING_SEQ` (컬럼 `"TO"`) |
| statistics.entity | `FidoStatistics` | FIDO_STATISTICS | `@EmbeddedId FidoStatisticsId` |
| statistics.entity | `FidoStatisticsBak` | FIDO_STATISTICS_BAK | `@EmbeddedId FidoStatisticsId` (같은 Id 클래스 재사용) |
| statistics.entity | `CcfaStatistics` | CCFA_STATISTICS | SEQ `CCFA_STATISTICS_SEQ` (컬럼 `"LIMIT"`) |
| statistics.entity | `CcfaStatisticsFilter` | CCFA_STATISTICS_FILTER | `@EmbeddedId CcfaStatisticsFilterId` (컬럼 `"VALUE"`) |
| statistics.entity | `CcfaStatisticsOrder` | CCFA_STATISTICS_ORDER | `@EmbeddedId CcfaStatisticsOrderId` |
| company.entity | `CcfaCompany` | CCFA_COMPANY | SEQ `CCFA_COMPANY_SEQ` |
| company.entity | `CcfaFdsPolicy` | CCFA_FDS_POLICY | `@Id Long companyIdx` (채번 없음) |
| company.entity | `CcfaLicense` | CCFA_LICENSE | SEQ `CCFA_LICENSE_SEQ` |
| manager.entity | `CcfaManager` | CCFA_MANAGER | SEQ `CCFA_MANAGER_SEQ` |
| manager.entity | `CcfaManagerPwPolicy` | CCFA_MANAGER_PW_POLICY | SEQ `CCFA_MANAGER_PW_POLICY_SEQ` |
| system.entity | `CcfaCriteria` | CCFA_CRITERIA | SEQ `CCFA_CRITERIA_SEQ` |
| system.entity | `CcfaErrorTable` | CCFA_ERROR_TABLE | `@Id String errorCode` |
| system.entity | `CcfaFidoclient` | CCFA_FIDOCLIENT | `@Id String servercode` |
| system.entity | `CcfaFields` | CCFA_FIELDS | SEQ `CCFA_FIELDS_SEQ` |
| system.entity | `CcfaMenu` | CCFA_MENU | SEQ `CCFA_MENU_SEQ` |
| system.entity | `CcfaOption` | CCFA_OPTION | SEQ `CCFA_OPTION_SEQ` |
| system.entity | `CcfaOptions` | CCFA_OPTIONS | SEQ `CCFA_OPTIONS_SEQ` |
| system.entity | `CcfaSystemInfo` | CCFA_SYSTEM_INFO | `@Id String propKey` |
| system.entity | `CcfaSystemProp` | CCFA_SYSTEM_PROP | `@EmbeddedId CcfaSystemPropId` |
| aws.entity | `AwsInfo` | AWS_INFO | SEQ `AWS_INFO_SEQ` |

### 타입 규칙

| Oracle | Java | 비고 |
|---|---|---|
| NUMBER, NUMBER(n,0) | `Long` | 모두 정수 |
| VARCHAR2(n) | `String` | `@Column(name, length = n)` |
| CLOB | `String` + `@Lob` | 14개 컬럼 |
| TIMESTAMP | `LocalDateTime` | |
| VARCHAR2 시각 3개 | `String` | `CCFA_EXCEPTIONS.CREATEDTIME`, `CCFA_MAILING.SENDTIME`, `AWS_INFO.EXPIRATIONDATE` |

필드명은 컬럼명을 camelCase로 바꾼 것(`COMPANY_IDX → companyIdx`, `SERVICENAME → servicename`, `INTERGRITY_HASH → intergrityHash`; 오타도 그대로). 예약어 컬럼 3개는 `@Column(name = "\"TO\"")`, `"\"LIMIT\""`, `"\"VALUE\""`. 기본값·NOT NULL은 엔티티에 두지 않고 서비스에서 채운다(Task 7 이후).

- [x] **Step 1: 테스트 리소스 복사와 ERD 파서 작성**

```bash
cp docs/erd/kbfido-columns.txt src/test/resources/erd-columns.txt
```

`src/test/java/com/crosscert/fidoadmin/erd/ErdColumns.java`

```java
package com.crosscert.fidoadmin.erd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** src/test/resources/erd-columns.txt 를 테이블 → 컬럼 집합으로 파싱한다. */
public final class ErdColumns {

    private static final Pattern TABLE = Pattern.compile("^## ([A-Z0-9_]+) ");
    private static final Pattern COLUMN = Pattern.compile("^\\s*\\d+\\.\\s+([A-Z0-9_]+)\\s");

    private ErdColumns() {}

    public static Map<String, Set<String>> load() {
        try (InputStream in = ErdColumns.class.getResourceAsStream("/erd-columns.txt")) {
            if (in == null) throw new IllegalStateException("erd-columns.txt not found");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Set<String>> result = new LinkedHashMap<>();
            String current = null;
            for (String line : text.split("\n")) {
                Matcher t = TABLE.matcher(line);
                if (t.find()) { current = t.group(1); result.put(current, new LinkedHashSet<>()); continue; }
                Matcher c = COLUMN.matcher(line);
                if (current != null && c.find()) result.get(current).add(c.group(1));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [x] **Step 2: 실패하는 정합성 테스트 작성**

`src/test/java/com/crosscert/fidoadmin/erd/ErdConformanceTest.java`

```java
package com.crosscert.fidoadmin.erd;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** 엔티티 39개의 @Table/@Column 이름 집합이 ERD 추출본과 정확히 같은지 검사한다. */
class ErdConformanceTest {

    private static final String BASE = "com.crosscert.fidoadmin";

    @Test
    void everyErdTableHasExactlyMatchingEntity() throws Exception {
        Map<String, Set<String>> erd = ErdColumns.load();
        assertThat(erd).hasSize(39);
        assertThat(erd.values().stream().mapToInt(Set::size).sum()).isEqualTo(354);

        Map<String, Set<String>> entities = scanEntities();
        assertThat(entities.keySet()).containsExactlyInAnyOrderElementsOf(erd.keySet());
        erd.forEach((table, cols) ->
            assertThat(entities.get(table)).as("columns of %s", table).containsExactlyInAnyOrderElementsOf(cols));
    }

    private Map<String, Set<String>> scanEntities() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (var bd : scanner.findCandidateComponents(BASE)) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            Table table = type.getAnnotation(Table.class);
            assertThat(table).as("@Table on %s", type.getSimpleName()).isNotNull();
            result.put(unquote(table.name()), collectColumns(type));
        }
        return result;
    }

    private Set<String> collectColumns(Class<?> type) {
        Set<String> cols = new LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Transient.class)) continue;
            if (f.isAnnotationPresent(EmbeddedId.class)) {
                assertThat(f.getType().isAnnotationPresent(Embeddable.class)).isTrue();
                cols.addAll(collectColumns(f.getType()));
                continue;
            }
            Column c = f.getAnnotation(Column.class);
            assertThat(c).as("@Column missing on %s.%s", type.getSimpleName(), f.getName()).isNotNull();
            assertThat(c.name()).as("@Column name empty on %s.%s", type.getSimpleName(), f.getName()).isNotEmpty();
            cols.add(unquote(c.name()));
        }
        return cols;
    }

    private static String unquote(String s) {
        return s.replace("\"", "");
    }
}
```

- [x] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.erd.ErdConformanceTest' --no-daemon`
Expected: FAIL — 엔티티가 0개라 `containsExactlyInAnyOrderElementsOf` 실패.

- [x] **Step 4: JpaConfig 작성**

`src/main/java/com/crosscert/fidoadmin/config/JpaConfig.java`

```java
package com.crosscert.fidoadmin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.crosscert.fidoadmin")
@EnableTransactionManagement
public class JpaConfig {
}
```

- [x] **Step 5: 대표 엔티티 작성 (패턴별 예시 — 이 코드를 그대로 만든다)**

시퀀스 PK + 타임스탬프. `company/entity/CcfaCompany.java`

```java
package com.crosscert.fidoadmin.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_COMPANY — 고객사(테넌트). ERD 컬럼 19개. IDX 0 은 전역(시스템) 레코드. */
@Entity
@Table(name = "CCFA_COMPANY")
@Getter @Setter @NoArgsConstructor
public class CcfaCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaCompanySeq")
    @SequenceGenerator(name = "ccfaCompanySeq", sequenceName = "CCFA_COMPANY_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_NAME", length = 256) private String companyName;
    @Column(name = "COMPANY_TYPE", length = 20) private String companyType;
    @Column(name = "VENDOR_CODE", length = 20) private String vendorCode;
    @Column(name = "CONTACT", length = 512) private String contact;
    @Column(name = "CONTACT_PHONE", length = 50) private String contactPhone;
    @Column(name = "CONTACT_PHONE2", length = 50) private String contactPhone2;
    @Column(name = "CONTACT_ADDR", length = 2048) private String contactAddr;
    @Column(name = "ENABLE_TYPE", length = 20) private String enableType;
    @Column(name = "STARTTIME") private LocalDateTime starttime;
    @Column(name = "ENDTIME") private LocalDateTime endtime;
    @Column(name = "MAX_APPID") private Long maxAppid;
    @Column(name = "MAX_APPSERVER") private Long maxAppserver;
    @Column(name = "MAX_USER") private Long maxUser;
    @Column(name = "ETC", length = 4000) private String etc;
    @Column(name = "CREATOR") private Long creator;
    @Column(name = "UPDATOR") private Long updator;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
```

예약어 컬럼 + VARCHAR2 시각. `log/entity/CcfaMailing.java`

```java
package com.crosscert.fidoadmin.log.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_MAILING — 메일/SMS 발송 큐. ERD 컬럼 10개. TO 는 예약어라 따옴표 필수. */
@Entity
@Table(name = "CCFA_MAILING")
@Getter @Setter @NoArgsConstructor
public class CcfaMailing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaMailingSeq")
    @SequenceGenerator(name = "ccfaMailingSeq", sequenceName = "CCFA_MAILING_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "\"TO\"", length = 4000) private String to;
    @Column(name = "SUBJECT", length = 256) private String subject;
    @Column(name = "CONTENT", length = 4000) private String content;
    @Column(name = "STATUS", length = 256) private String status;
    @Column(name = "SMS_TO", length = 4000) private String smsTo;
    @Column(name = "SMS_CONTENT", length = 300) private String smsContent;
    @Column(name = "SMS_STATUS", length = 512) private String smsStatus;
    /** ERD 상 VARCHAR2(64). 문자열 그대로 둔다. */
    @Column(name = "SENDTIME", length = 64) private String sendtime;
}
```

CLOB. `log/entity/FidoLogs.java`

```java
package com.crosscert.fidoadmin.log.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO_LOGS — FIDO 처리 로그(append-only). ERD 컬럼 6개. */
@Entity
@Table(name = "FIDO_LOGS")
@Getter @Setter @NoArgsConstructor
public class FidoLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fidoLogsSeq")
    @SequenceGenerator(name = "fidoLogsSeq", sequenceName = "FIDO_LOGS_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERIALCODE", length = 128) private String serialcode;
    @Column(name = "SERVICENAME", length = 512) private String servicename;
    @Lob @Column(name = "JSONDATA") private String jsondata;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
```

복합키(예약어 포함). `statistics/entity/CcfaStatisticsFilterId.java`, `CcfaStatisticsFilter.java`

```java
package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class CcfaStatisticsFilterId implements Serializable {
    @Column(name = "STATISTICS_IDX") private Long statisticsIdx;
    @Column(name = "COLUMN_NAME", length = 256) private String columnName;
    @Column(name = "OP", length = 20) private String op;
    @Column(name = "\"VALUE\"", length = 1024) private String value;
}
```

```java
package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_STATISTICS_FILTER — PK 없음. 논리 식별자 4개 컬럼을 복합키로 매핑. ERD 컬럼 5개. */
@Entity
@Table(name = "CCFA_STATISTICS_FILTER")
@Getter @Setter @NoArgsConstructor
public class CcfaStatisticsFilter {
    @EmbeddedId private CcfaStatisticsFilterId id;
    @Column(name = "TYPE", length = 20) private String type;
}
```

복합키(통계). `statistics/entity/FidoStatisticsId.java`, `FidoStatistics.java`

```java
package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class FidoStatisticsId implements Serializable {
    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "SERVICE_NAME", length = 512) private String serviceName;
    @Column(name = "GROUPBY", length = 32) private String groupby;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
}
```

```java
package com.crosscert.fidoadmin.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FIDO_STATISTICS — PK 없음. ERD 컬럼 12개. */
@Entity
@Table(name = "FIDO_STATISTICS")
@Getter @Setter @NoArgsConstructor
public class FidoStatistics {
    @EmbeddedId private FidoStatisticsId id;
    @Column(name = "AUTH_S") private Long authS;
    @Column(name = "AUTH_F") private Long authF;
    @Column(name = "TC_S") private Long tcS;
    @Column(name = "TC_F") private Long tcF;
    @Column(name = "REG_S") private Long regS;
    @Column(name = "REG_F") private Long regF;
    @Column(name = "DEREG_S") private Long deregS;
    @Column(name = "DEREG_F") private Long deregF;
}
```

`FidoStatisticsBak`은 `@Table(name = "FIDO_STATISTICS_BAK")`만 다르고 본문은 `FidoStatistics`와 같다(같은 `FidoStatisticsId` 사용).

복합키(시스템 설정). `system/entity/CcfaSystemPropId.java`, `CcfaSystemProp.java`

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
}
```

```java
package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_SYSTEM_PROP — 고객사별 key-value. PK 없음. ERD 컬럼 5개. */
@Entity
@Table(name = "CCFA_SYSTEM_PROP")
@Getter @Setter @NoArgsConstructor
public class CcfaSystemProp {
    @EmbeddedId private CcfaSystemPropId id;
    @Column(name = "PROP_VALUE", length = 4000) private String propValue;
    @Column(name = "SHARE_TYPE", length = 20) private String shareType;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
```

`CcfaStatisticsOrderId`는 `statisticsIdx`(STATISTICS_IDX), `columnName`(COLUMN_NAME, 128) 두 필드이고 `CcfaStatisticsOrder`는 `@EmbeddedId id` + `type`(TYPE, 20)이다.

채번 없는 Long PK. `company/entity/CcfaFdsPolicy.java`

```java
package com.crosscert.fidoadmin.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_FDS_POLICY — COMPANY_IDX 가 PK(고객사당 1건). ERD 컬럼 10개. */
@Entity
@Table(name = "CCFA_FDS_POLICY")
@Getter @Setter @NoArgsConstructor
public class CcfaFdsPolicy {
    @Id @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "AND_IP", length = 4000) private String andIp;
    @Column(name = "AND_TERM", length = 32) private String andTerm;
    @Column(name = "AND_DEVICE", length = 16) private String andDevice;
    @Column(name = "AND_COUNTRY", length = 16) private String andCountry;
    @Column(name = "OR_IP", length = 4000) private String orIp;
    @Column(name = "OR_TERM", length = 32) private String orTerm;
    @Column(name = "OR_COUNTRY", length = 16) private String orCountry;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
```

문자열 PK. `system/entity/CcfaSystemInfo.java`

```java
package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCFA_SYSTEM_INFO — key-value. ERD 컬럼 3개. */
@Entity
@Table(name = "CCFA_SYSTEM_INFO")
@Getter @Setter @NoArgsConstructor
public class CcfaSystemInfo {
    @Id @Column(name = "PROP_KEY", length = 128) private String propKey;
    @Column(name = "PROP_VALUE", length = 1024) private String propValue;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
```

`CcfaErrorTable`(`@Id String errorCode` = ERROR_CODE 20), `CcfaFidoclient`(`@Id String servercode` = SERVERCODE 64), `Fido2DemoAccessCode`(`@Id String accesscode` = ACCESSCODE 128)는 같은 패턴이다.

운영자. `manager/entity/CcfaManager.java` (인증에서 바로 쓰므로 전체를 적는다)

```java
package com.crosscert.fidoadmin.manager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** CCFA_MANAGER — 운영자 계정. ERD 컬럼 16개. USER_PW 는 SHA-256 hex, 화면 DTO 에 절대 싣지 않는다. */
@Entity
@Table(name = "CCFA_MANAGER")
@Getter @Setter @NoArgsConstructor
@ToString(exclude = "userPw")
public class CcfaManager {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ccfaManagerSeq")
    @SequenceGenerator(name = "ccfaManagerSeq", sequenceName = "CCFA_MANAGER_SEQ", allocationSize = 1)
    @Column(name = "IDX")
    private Long idx;

    @Column(name = "USER_ID", length = 64) private String userId;
    @Column(name = "USER_PW", length = 128) private String userPw;
    @Column(name = "USER_NM", length = 50) private String userNm;
    @Column(name = "USER_EMAIL", length = 256) private String userEmail;
    @Column(name = "USER_PHONE", length = 20) private String userPhone;
    @Column(name = "COMPANY_IDX") private Long companyIdx;
    @Column(name = "STATUS", length = 20) private String status;
    @Column(name = "LOGIN", length = 20) private String login;
    @Column(name = "BLOCK_TIME") private LocalDateTime blockTime;
    @Column(name = "LAST_ACCESS") private LocalDateTime lastAccess;
    @Column(name = "ETC", length = 2048) private String etc;
    @Column(name = "ALRAM_TYPE", length = 32) private String alramType;
    @Column(name = "ALRAM_LEVEL", length = 20) private String alramLevel;
    @Column(name = "CREATEDTIME") private LocalDateTime createdtime;
    @Column(name = "UPDATEDTIME") private LocalDateTime updatedtime;
}
```

`manager/entity/CcfaManagerPwPolicy.java`: `idx`(SEQ `CCFA_MANAGER_PW_POLICY_SEQ`), `userId`(USER_ID 64), `accountLock`(ACCOUNT_LOCK 1), `pwFailCnt`(PW_FAIL_CNT Long), `createdtime`, `updatedtime`.

`log/entity/CcfaAuditLog.java`: `idx`(SEQ `CCFA_AUDIT_LOG_SEQ`), `companyIdx`, `companyName`(512), `type`(32), `userId`(USER_ID 64), `userName`(USER_NAME 32), `message`(4000), `ip`(15), `ua`(2048), `intergrityHash`(INTERGRITY_HASH 512), `createdtime`.

- [x] **Step 6: 나머지 엔티티 작성**

매핑표의 나머지 클래스를 `src/test/resources/erd-columns.txt`의 컬럼 순서·이름·길이대로 위 패턴에 맞춰 작성한다. 각 클래스 Javadoc에 `ERD 컬럼 N개`를 적는다. `Userinfo`는 `@ToString(exclude = {"pubkey", "certificate"})`, `AwsInfo`는 `@ToString(exclude = "amzToken")`을 붙인다.

- [x] **Step 7: 정합성 테스트 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.erd.ErdConformanceTest' --no-daemon`
Expected: PASS. 실패 메시지에 테이블명과 빠진/남는 컬럼이 나오므로 그대로 고친다.

- [x] **Step 8: Hibernate 부팅 검증 (Docker Oracle 필요)**

`src/test/java/com/crosscert/fidoadmin/erd/EntityBootTest.java`

```java
package com.crosscert.fidoadmin.erd;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatistics;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/** 로컬 Docker Oracle(application-local.yml)에 대해 매핑이 실제 컬럼과 맞는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class EntityBootTest {

    @Autowired EntityManager em;

    @Test
    void reservedWordColumnsAreReadable() {
        CcfaCompany global = em.find(CcfaCompany.class, 0L);
        assertThat(global).isNotNull();

        CcfaMailing mail = em.find(CcfaMailing.class, 1L);
        assertThat(mail.getTo()).isEqualTo("ops@kb.local");

        CcfaStatistics st = em.find(CcfaStatistics.class, 1L);
        assertThat(st.getLimit()).isEqualTo(30L);

        long filters = em.createQuery("select count(f) from CcfaStatisticsFilter f", Long.class).getSingleResult();
        assertThat(filters).isEqualTo(1L);
    }
}
```

Run: `docker compose -f docker/docker-compose.yml up -d` 후 `./gradlew test --tests 'com.crosscert.fidoadmin.erd.EntityBootTest' --no-daemon`
Expected: PASS. `ORA-00904: invalid identifier`가 나오면 해당 엔티티의 컬럼 이름/따옴표를 고친다.

- [x] **Step 9: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin src/test
git commit -m "feat: ERD 39개 테이블 엔티티와 정합성 테스트

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 5: 로그인 사용자 모델, TenantContext, 감사 로그

**Files:**
- Create: `src/main/java/com/crosscert/fidoadmin/auth/ManagerUserDetails.java`
- Create: `src/main/java/com/crosscert/fidoadmin/common/TenantContext.java`
- Create: `src/main/java/com/crosscert/fidoadmin/audit/AuditType.java`, `AuditLogWriter.java`, `AuditLogger.java`
- Create: `src/main/java/com/crosscert/fidoadmin/log/repository/CcfaAuditLogRepository.java`
- Test: `src/test/java/com/crosscert/fidoadmin/audit/AuditLoggerTest.java`, `src/test/java/com/crosscert/fidoadmin/common/TenantContextTest.java`

**Interfaces:**
- Produces:
  - `ManagerUserDetails(Long idx, String userId, String userNm, Long companyIdx, String companyName)` implements `UserDetails`; `isSuper()`는 `companyIdx == 0`; 권한 `ROLE_SUPER` 또는 `ROLE_COMPANY`. 비밀번호는 생성자 인수 `password`로 받되 `eraseCredentials()` 지원.
  - `TenantContext.current(): Optional<ManagerUserDetails>`, `TenantContext.require(): ManagerUserDetails`, `TenantContext.companyIdx(): Long`, `TenantContext.isSuper(): boolean`.
  - `enum AuditType { LOGIN, LOGOUT, CREATE, UPDATE, DELETE, STATUS }`
  - `AuditLogger.log(AuditType type, String message)` — 현재 로그인 사용자·현재 요청 기준. `AuditLogger.log(ManagerUserDetails actor, AuditType type, String message, String ip, String ua)`.
  - `CcfaAuditLogRepository extends JpaRepository<CcfaAuditLog, Long>, JpaSpecificationExecutor<CcfaAuditLog>`

- [x] **Step 1: ManagerUserDetails 작성**

```java
package com.crosscert.fidoadmin.auth;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class ManagerUserDetails implements UserDetails {

    public static final String ROLE_SUPER = "ROLE_SUPER";
    public static final String ROLE_COMPANY = "ROLE_COMPANY";

    private final Long idx;
    private final String userId;
    private final String userNm;
    private final Long companyIdx;
    private final String companyName;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private String password;

    public ManagerUserDetails(Long idx, String userId, String password, String userNm, Long companyIdx,
                              String companyName, boolean enabled, boolean accountNonLocked) {
        this.idx = idx;
        this.userId = userId;
        this.password = password;
        this.userNm = userNm;
        this.companyIdx = companyIdx == null ? 0L : companyIdx;
        this.companyName = companyName;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public boolean isSuper() { return companyIdx == 0L; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(isSuper() ? ROLE_SUPER : ROLE_COMPANY));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return userId; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    public void eraseCredentials() { this.password = null; }
}
```

- [x] **Step 2: TenantContext 테스트 작성**

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

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var user = new ManagerUserDetails(1L, "u", null, "이름", companyIdx, "회사", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test void superWhenCompanyZero() {
        login(0L);
        assertThat(TenantContext.isSuper()).isTrue();
        assertThat(TenantContext.companyIdx()).isEqualTo(0L);
    }

    @Test void companyRoleOtherwise() {
        login(7L);
        assertThat(TenantContext.isSuper()).isFalse();
        assertThat(TenantContext.companyIdx()).isEqualTo(7L);
        assertThat(TenantContext.require().getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    @Test void requireThrowsWhenAnonymous() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
    }
}
```

- [x] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.TenantContextTest' --no-daemon`
Expected: 컴파일 오류 `TenantContext` 없음.

- [x] **Step 4: TenantContext 구현**

```java
package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 현재 로그인 운영자와 테넌트(COMPANY_IDX) 조회. COMPANY_IDX 0 = SUPER. */
public final class TenantContext {

    private TenantContext() {}

    public static Optional<ManagerUserDetails> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ManagerUserDetails user)) return Optional.empty();
        return Optional.of(user);
    }

    public static ManagerUserDetails require() {
        return current().orElseThrow(() -> new IllegalStateException("로그인 사용자가 없습니다"));
    }

    public static Long companyIdx() { return require().getCompanyIdx(); }

    public static boolean isSuper() { return current().map(ManagerUserDetails::isSuper).orElse(false); }
}
```

- [x] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.TenantContextTest' --no-daemon`
Expected: 3개 통과.

- [x] **Step 6: AuditLogger 테스트 작성**

```java
package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLoggerTest {

    private final AuditLogWriter writer = mock(AuditLogWriter.class);
    private final AuditLogger logger = new AuditLogger(writer);
    private final ManagerUserDetails actor =
        new ManagerUserDetails(1L, "kbadmin", null, "KB운영자", 1L, "KB국민은행", true, true);

    @Test
    void writesAllColumnsAndIntegrityHash() {
        logger.log(actor, AuditType.UPDATE, "APPID UPDATE 1", "10.0.0.5", "Mozilla/5.0");

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer).write(captor.capture());
        CcfaAuditLog row = captor.getValue();
        assertThat(row.getCompanyIdx()).isEqualTo(1L);
        assertThat(row.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(row.getType()).isEqualTo("UPDATE");
        assertThat(row.getUserId()).isEqualTo("kbadmin");
        assertThat(row.getUserName()).isEqualTo("KB운영자");
        assertThat(row.getMessage()).isEqualTo("APPID UPDATE 1");
        assertThat(row.getIp()).isEqualTo("10.0.0.5");
        assertThat(row.getUa()).isEqualTo("Mozilla/5.0");
        assertThat(row.getCreatedtime()).isNotNull();
        String expected = Sha256PasswordEncoder.sha256Hex(
            "1|KB국민은행|UPDATE|kbadmin|KB운영자|APPID UPDATE 1|10.0.0.5|Mozilla/5.0|" + row.getCreatedtime());
        assertThat(row.getIntergrityHash()).isEqualTo(expected);
    }

    @Test
    void truncatesIpToFifteenCharsAndLongFields() {
        logger.log(actor, AuditType.LOGIN, "x".repeat(5000), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "u".repeat(3000));

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getIp()).hasSize(15);
        assertThat(captor.getValue().getMessage()).hasSize(4000);
        assertThat(captor.getValue().getUa()).hasSize(2048);
    }

    @Test
    void swallowsWriterFailure() {
        doThrow(new RuntimeException("db down")).when(writer).write(any());
        logger.log(actor, AuditType.DELETE, "m", "1.1.1.1", "ua"); // 예외가 전파되지 않아야 한다
    }
}
```

- [x] **Step 7: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.audit.AuditLoggerTest' --no-daemon`
Expected: 컴파일 오류.

- [x] **Step 8: 구현**

`audit/AuditType.java`

```java
package com.crosscert.fidoadmin.audit;

public enum AuditType { LOGIN, LOGOUT, CREATE, UPDATE, DELETE, STATUS }
```

`log/repository/CcfaAuditLogRepository.java`

```java
package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaAuditLogRepository extends JpaRepository<CcfaAuditLog, Long>, JpaSpecificationExecutor<CcfaAuditLog> {
}
```

`audit/AuditLogWriter.java` — 별도 빈으로 두어야 `REQUIRES_NEW` 프록시가 적용된다.

```java
package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogWriter {
    private final CcfaAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(CcfaAuditLog row) {
        repository.save(row);
    }
}
```

`audit/AuditLogger.java`

```java
package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** CCFA_AUDIT_LOG 기록. 실패해도 호출자 트랜잭션을 깨지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogWriter writer;

    /** 현재 로그인 사용자·현재 HTTP 요청 기준으로 기록한다. 로그인 사용자가 없으면 기록하지 않는다. */
    public void log(AuditType type, String message) {
        TenantContext.current().ifPresent(actor -> {
            HttpServletRequest req = currentRequest();
            log(actor, type, message, req == null ? null : req.getRemoteAddr(),
                req == null ? null : req.getHeader("User-Agent"));
        });
    }

    public void log(ManagerUserDetails actor, AuditType type, String message, String ip, String ua) {
        try {
            CcfaAuditLog row = new CcfaAuditLog();
            row.setCompanyIdx(actor.getCompanyIdx());
            row.setCompanyName(cut(actor.getCompanyName(), 512));
            row.setType(type.name());
            row.setUserId(cut(actor.getUserId(), 64));
            row.setUserName(cut(actor.getUserNm(), 32));
            row.setMessage(cut(message, 4000));
            row.setIp(cut(ip, 15));
            row.setUa(cut(ua, 2048));
            row.setCreatedtime(LocalDateTime.now());
            row.setIntergrityHash(Sha256PasswordEncoder.sha256Hex(String.join("|",
                String.valueOf(row.getCompanyIdx()), row.getCompanyName(), row.getType(), row.getUserId(),
                row.getUserName(), row.getMessage(), String.valueOf(row.getIp()), String.valueOf(row.getUa()),
                String.valueOf(row.getCreatedtime()))));
            writer.write(row);
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
```

`CcfaAuditLog.userName`이 null이면 `cut`이 null을 돌려주고 NOT NULL 컬럼이라 저장에 실패한다. `ManagerUserDetailsService`(Task 6)는 `USER_NM`이 null이면 `USER_ID`를 이름으로 넣는다.

- [x] **Step 9: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.audit.AuditLoggerTest' --no-daemon`
Expected: 3개 통과.

- [x] **Step 10: 커밋**

```bash
git add src/main/java/com/crosscert/fidoadmin/auth/ManagerUserDetails.java src/main/java/com/crosscert/fidoadmin/common/TenantContext.java src/main/java/com/crosscert/fidoadmin/audit src/main/java/com/crosscert/fidoadmin/log/repository src/test
git commit -m "feat: 로그인 사용자 모델, TenantContext, 감사 로그 기록기

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 6: Spring Security 로그인·잠금·역할

**Files:**
- Create: `manager/repository/CcfaManagerRepository.java`, `CcfaManagerPwPolicyRepository.java`
- Create: `company/repository/CcfaCompanyRepository.java`
- Create: `system/repository/CcfaSystemPropRepository.java`
- Create: `auth/ManagerUserDetailsService.java`, `auth/LoginAttemptService.java`, `auth/LoginSuccessHandler.java`, `auth/LoginFailureHandler.java`, `auth/AppLogoutSuccessHandler.java`, `auth/LoginController.java`
- Create: `config/SecurityConfig.java`
- Create: `src/main/resources/templates/login.html`
- Test: `auth/LoginAttemptServiceTest.java`, `auth/ManagerUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `ManagerUserDetails`, `Sha256PasswordEncoder`, `AuditLogger`, `AuditType`, 엔티티 `CcfaManager`, `CcfaManagerPwPolicy`, `CcfaCompany`, `CcfaSystemProp`/`CcfaSystemPropId`.
- Produces:
  - `CcfaManagerRepository.findByUserId(String): Optional<CcfaManager>`
  - `CcfaManagerPwPolicyRepository.findFirstByUserIdOrderByIdxDesc(String): Optional<CcfaManagerPwPolicy>`
  - `CcfaCompanyRepository extends JpaRepository<CcfaCompany, Long>, JpaSpecificationExecutor<CcfaCompany>`
  - `CcfaSystemPropRepository extends JpaRepository<CcfaSystemProp, CcfaSystemPropId>, JpaSpecificationExecutor<CcfaSystemProp>`
  - `LoginAttemptService.onSuccess(String userId)`, `onFailure(String userId)`, `unlock(String userId)`, `failLimit(): int`
  - `PasswordEncoder` 빈(`Sha256PasswordEncoder`), 로그인 URL `/login`, 로그아웃 `POST /logout`, 성공 후 `/`.

- [x] **Step 1: 리포지토리 4개 작성**

```java
package com.crosscert.fidoadmin.manager.repository;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaManagerRepository extends JpaRepository<CcfaManager, Long>, JpaSpecificationExecutor<CcfaManager> {
    Optional<CcfaManager> findByUserId(String userId);
}
```

```java
package com.crosscert.fidoadmin.manager.repository;

import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CcfaManagerPwPolicyRepository extends JpaRepository<CcfaManagerPwPolicy, Long> {
    Optional<CcfaManagerPwPolicy> findFirstByUserIdOrderByIdxDesc(String userId);
}
```

```java
package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaCompanyRepository extends JpaRepository<CcfaCompany, Long>, JpaSpecificationExecutor<CcfaCompany> {
}
```

```java
package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaSystemPropRepository extends JpaRepository<CcfaSystemProp, CcfaSystemPropId>, JpaSpecificationExecutor<CcfaSystemProp> {
}
```

- [x] **Step 2: LoginAttemptService 테스트 작성**

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginAttemptServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaSystemPropRepository props = mock(CcfaSystemPropRepository.class);
    LoginAttemptService service = new LoginAttemptService(managers, policies, props, 5);
    CcfaManager manager = new CcfaManager();

    @BeforeEach void setUp() {
        manager.setUserId("kbadmin");
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager));
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.empty());
    }

    @Test void successResetsCounterAndMarksOnline() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(3L); policy.setAccountLock("N");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));

        service.onSuccess("kbadmin");

        assertThat(policy.getPwFailCnt()).isZero();
        assertThat(manager.getLogin()).isEqualTo("ON-LINE");
        assertThat(manager.getLastAccess()).isNotNull();
        verify(policies).save(policy);
        verify(managers).save(manager);
    }

    @Test void failureIncrementsAndLocksAtLimit() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(4L); policy.setAccountLock("N");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));

        service.onFailure("kbadmin");

        assertThat(policy.getPwFailCnt()).isEqualTo(5L);
        assertThat(policy.getAccountLock()).isEqualTo("Y");
        assertThat(manager.getBlockTime()).isNotNull();
    }

    @Test void failureCreatesPolicyRowWhenMissing() {
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());

        service.onFailure("kbadmin");

        ArgumentCaptor<CcfaManagerPwPolicy> captor = ArgumentCaptor.forClass(CcfaManagerPwPolicy.class);
        verify(policies).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("kbadmin");
        assertThat(captor.getValue().getPwFailCnt()).isEqualTo(1L);
        assertThat(captor.getValue().getAccountLock()).isEqualTo("N");
        assertThat(captor.getValue().getCreatedtime()).isNotNull();
    }

    @Test void failureForUnknownUserDoesNothing() {
        when(managers.findByUserId("nobody")).thenReturn(Optional.empty());
        service.onFailure("nobody");
        verify(policies, org.mockito.Mockito.never()).save(any());
    }

    @Test void failLimitPrefersSystemProp() {
        CcfaSystemProp prop = new CcfaSystemProp();
        prop.setId(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)); prop.setPropValue("3");
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop));
        assertThat(service.failLimit()).isEqualTo(3);
    }

    @Test void failLimitFallsBackOnGarbage() {
        CcfaSystemProp prop = new CcfaSystemProp();
        prop.setId(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)); prop.setPropValue("abc");
        when(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(Optional.of(prop));
        assertThat(service.failLimit()).isEqualTo(5);
    }

    @Test void unlockClearsLockAndCounter() {
        CcfaManagerPwPolicy policy = new CcfaManagerPwPolicy();
        policy.setUserId("kbadmin"); policy.setPwFailCnt(5L); policy.setAccountLock("Y");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(policy));
        manager.setBlockTime(java.time.LocalDateTime.now());

        service.unlock("kbadmin");

        assertThat(policy.getAccountLock()).isEqualTo("N");
        assertThat(policy.getPwFailCnt()).isZero();
        assertThat(manager.getBlockTime()).isNull();
    }
}
```

- [x] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.LoginAttemptServiceTest' --no-daemon`
Expected: 컴파일 오류.

- [x] **Step 4: LoginAttemptService 구현**

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 성공/실패에 따른 CCFA_MANAGER, CCFA_MANAGER_PW_POLICY 갱신. */
@Service
public class LoginAttemptService {

    static final String PROP_FAIL_LIMIT = "PW_FAIL_LIMIT";

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final CcfaSystemPropRepository props;
    private final int defaultFailLimit;

    public LoginAttemptService(CcfaManagerRepository managers, CcfaManagerPwPolicyRepository policies,
                               CcfaSystemPropRepository props,
                               @Value("${fido-admin.password-fail-limit:5}") int defaultFailLimit) {
        this.managers = managers;
        this.policies = policies;
        this.props = props;
        this.defaultFailLimit = defaultFailLimit;
    }

    public int failLimit() {
        return props.findById(new CcfaSystemPropId(PROP_FAIL_LIMIT, 0L))
            .map(p -> {
                try { return Integer.parseInt(p.getPropValue().trim()); }
                catch (RuntimeException e) { return defaultFailLimit; }
            })
            .orElse(defaultFailLimit);
    }

    @Transactional
    public void onSuccess(String userId) {
        managers.findByUserId(userId).ifPresent(m -> {
            LocalDateTime now = LocalDateTime.now();
            m.setLogin("ON-LINE");
            m.setLastAccess(now);
            m.setUpdatedtime(now);
            managers.save(m);
            CcfaManagerPwPolicy p = policyOrNew(userId, now);
            p.setPwFailCnt(0L);
            p.setUpdatedtime(now);
            policies.save(p);
        });
    }

    @Transactional
    public void onFailure(String userId) {
        managers.findByUserId(userId).ifPresent(m -> {
            LocalDateTime now = LocalDateTime.now();
            CcfaManagerPwPolicy p = policyOrNew(userId, now);
            long cnt = (p.getPwFailCnt() == null ? 0L : p.getPwFailCnt()) + 1;
            p.setPwFailCnt(cnt);
            p.setUpdatedtime(now);
            if (cnt >= failLimit()) {
                p.setAccountLock("Y");
                m.setBlockTime(now);
                m.setUpdatedtime(now);
                managers.save(m);
            }
            policies.save(p);
        });
    }

    @Transactional
    public void onLogout(String userId) {
        managers.findByUserId(userId).ifPresent(m -> {
            m.setLogin("OFF-LINE");
            m.setUpdatedtime(LocalDateTime.now());
            managers.save(m);
        });
    }

    @Transactional
    public void unlock(String userId) {
        LocalDateTime now = LocalDateTime.now();
        CcfaManagerPwPolicy p = policyOrNew(userId, now);
        p.setAccountLock("N");
        p.setPwFailCnt(0L);
        p.setUpdatedtime(now);
        policies.save(p);
        managers.findByUserId(userId).ifPresent(m -> {
            m.setBlockTime(null);
            m.setUpdatedtime(now);
            managers.save(m);
        });
    }

    private CcfaManagerPwPolicy policyOrNew(String userId, LocalDateTime now) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId).orElseGet(() -> {
            CcfaManagerPwPolicy p = new CcfaManagerPwPolicy();
            p.setUserId(userId);
            p.setAccountLock("N");
            p.setPwFailCnt(0L);
            p.setCreatedtime(now);
            p.setUpdatedtime(now);
            return p;
        });
    }
}
```

- [x] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.LoginAttemptServiceTest' --no-daemon`
Expected: 7개 통과.

- [x] **Step 6: ManagerUserDetailsService 테스트 작성**

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class ManagerUserDetailsServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    ManagerUserDetailsService service = new ManagerUserDetailsService(managers, policies, companies);

    private CcfaManager manager(String status, long companyIdx) {
        CcfaManager m = new CcfaManager();
        m.setIdx(1L); m.setUserId("kbadmin"); m.setUserPw("hash"); m.setUserNm("KB운영자");
        m.setStatus(status); m.setCompanyIdx(companyIdx);
        return m;
    }

    @Test void loadsActiveManagerWithCompanyRole() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행");
        when(companies.findById(1L)).thenReturn(Optional.of(c));

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");

        assertThat(u.isEnabled()).isTrue();
        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(u.getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    @Test void inactiveStatusIsDisabled() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("비활성", 0L)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        when(companies.findById(0L)).thenReturn(Optional.empty());

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");
        assertThat(u.isEnabled()).isFalse();
        assertThat(u.isSuper()).isTrue();
        assertThat(u.getCompanyName()).isEqualTo("전역");
    }

    @Test void lockedPolicyLocksAccount() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)));
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setAccountLock("Y");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        when(companies.findById(1L)).thenReturn(Optional.empty());

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");
        assertThat(u.isAccountNonLocked()).isFalse();
    }

    @Test void unknownUserThrows() {
        when(managers.findByUserId("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("x")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test void nullNameFallsBackToUserId() {
        CcfaManager m = manager("활성", 1L); m.setUserNm(null);
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        when(companies.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.loadUserByUsername("kbadmin")).extracting("userNm").isEqualTo("kbadmin");
    }
}
```

- [x] **Step 7: 실패 확인 후 구현**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.ManagerUserDetailsServiceTest' --no-daemon` → 컴파일 오류 확인.

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerUserDetailsService implements UserDetailsService {

    static final String STATUS_ACTIVE = "활성";

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final CcfaCompanyRepository companies;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        CcfaManager m = managers.findByUserId(username)
            .orElseThrow(() -> new UsernameNotFoundException("운영자 없음: " + username));
        boolean locked = policies.findFirstByUserIdOrderByIdxDesc(username)
            .map(p -> "Y".equalsIgnoreCase(p.getAccountLock())).orElse(false);
        long companyIdx = m.getCompanyIdx() == null ? 0L : m.getCompanyIdx();
        String companyName = companies.findById(companyIdx).map(CcfaCompany::getCompanyName)
            .orElse(companyIdx == 0L ? "전역" : "고객사 " + companyIdx);
        String name = m.getUserNm() == null || m.getUserNm().isBlank() ? m.getUserId() : m.getUserNm();
        return new ManagerUserDetails(m.getIdx(), m.getUserId(), m.getUserPw(), name, companyIdx, companyName,
            STATUS_ACTIVE.equals(m.getStatus()), !locked);
    }
}
```

Run 다시: 5개 통과.

- [x] **Step 8: 핸들러 3개 작성**

`auth/LoginSuccessHandler.java`

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService attempts;
    private final AuditLogger audit;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        ManagerUserDetails user = (ManagerUserDetails) authentication.getPrincipal();
        attempts.onSuccess(user.getUserId());
        audit.log(user, AuditType.LOGIN, "로그인 성공", request.getRemoteAddr(), request.getHeader("User-Agent"));
        setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
```

`auth/LoginFailureHandler.java`

```java
package com.crosscert.fidoadmin.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService attempts;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String userId = request.getParameter("username");
        String reason;
        if (exception instanceof LockedException) {
            reason = "locked";
        } else if (exception instanceof DisabledException) {
            reason = "disabled";
        } else if (exception instanceof BadCredentialsException) {
            if (userId != null && !userId.isBlank()) attempts.onFailure(userId);
            reason = "bad";
        } else {
            reason = "error";
        }
        setDefaultFailureUrl("/login?error=" + reason);
        super.onAuthenticationFailure(request, response, exception);
    }
}
```

`auth/AppLogoutSuccessHandler.java`

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private final LoginAttemptService attempts;
    private final AuditLogger audit;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        if (authentication != null && authentication.getPrincipal() instanceof ManagerUserDetails user) {
            attempts.onLogout(user.getUserId());
            audit.log(user, AuditType.LOGOUT, "로그아웃", request.getRemoteAddr(), request.getHeader("User-Agent"));
        }
        setDefaultTargetUrl("/login?logout");
        super.onLogoutSuccess(request, response, authentication);
    }
}
```

- [x] **Step 9: SecurityConfig 작성**

```java
package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler;
import com.crosscert.fidoadmin.auth.LoginFailureHandler;
import com.crosscert.fidoadmin.auth.LoginSuccessHandler;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Sha256PasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginSuccessHandler success,
                                           LoginFailureHandler failure, AppLogoutSuccessHandler logout) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/webjars/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/companies/**", "/licenses/**", "/managers/**", "/system/**").hasRole("SUPER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(success)
                .failureHandler(failure))
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessHandler(logout)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .expiredUrl("/login?expired"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
```

CSRF는 기본 활성이다. Thymeleaf `th:action` 폼에는 토큰이 자동 삽입된다.

- [x] **Step 10: LoginController와 login.html 작성**

```java
package com.crosscert.fidoadmin.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
```

`src/main/resources/templates/login.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>FIDO Admin 로그인</title>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
  <link rel="stylesheet" th:href="@{/css/admin.css}">
</head>
<body class="bg-light">
<main class="container" style="max-width: 420px; margin-top: 12vh;">
  <div class="card shadow-sm">
    <div class="card-body p-4">
      <h1 class="h4 mb-4 text-center">FIDO Admin</h1>
      <div class="alert alert-danger py-2" th:if="${param.error != null and param.error[0] == 'bad'}">아이디 또는 비밀번호가 올바르지 않습니다.</div>
      <div class="alert alert-danger py-2" th:if="${param.error != null and param.error[0] == 'locked'}">비밀번호 실패 횟수 초과로 잠긴 계정입니다. 관리자에게 문의하세요.</div>
      <div class="alert alert-warning py-2" th:if="${param.error != null and param.error[0] == 'disabled'}">비활성 상태의 계정입니다.</div>
      <div class="alert alert-danger py-2" th:if="${param.error != null and param.error[0] == 'error'}">로그인 처리 중 오류가 발생했습니다.</div>
      <div class="alert alert-info py-2" th:if="${param.logout != null}">로그아웃되었습니다.</div>
      <div class="alert alert-warning py-2" th:if="${param.expired != null}">다른 곳에서 로그인되어 세션이 종료되었습니다.</div>
      <form th:action="@{/login}" method="post">
        <div class="mb-3">
          <label for="username" class="form-label">아이디</label>
          <input id="username" name="username" class="form-control" autocomplete="username" required autofocus>
        </div>
        <div class="mb-3">
          <label for="password" class="form-label">비밀번호</label>
          <input id="password" name="password" type="password" class="form-control" autocomplete="current-password" required>
        </div>
        <button type="submit" class="btn btn-primary w-100">로그인</button>
      </form>
    </div>
  </div>
</main>
</body>
</html>
```

`src/main/resources/static/css/admin.css` (초기 내용, Task 7에서 확장)

```css
:root { --fa-sidebar-w: 240px; }
body { font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans KR", system-ui, sans-serif; }
```

- [x] **Step 11: 수동 검증 (Docker Oracle 필요)**

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

브라우저에서 `http://localhost:8080/` → `/login`으로 이동하는지, `superuser / Admin1234!`로 로그인하면 `/`(아직 404여도 됨)로 가는지, 잘못된 비밀번호를 5회 넣으면 `?error=locked`가 뜨는지 확인. 그런 다음 아래로 잠금 해제.

```bash
docker exec fido-admin-oracle sqlplus -s kbfido/kbfido@localhost/FREEPDB1 <<'SQL'
UPDATE CCFA_MANAGER_PW_POLICY SET ACCOUNT_LOCK='N', PW_FAIL_CNT=0 WHERE USER_ID='superuser';
UPDATE CCFA_MANAGER SET BLOCK_TIME=NULL WHERE USER_ID='superuser';
COMMIT;
SQL
```

`CCFA_AUDIT_LOG`에 `LOGIN` 행이 추가되는지 확인: `SELECT TYPE, USER_ID, IP FROM CCFA_AUDIT_LOG ORDER BY IDX DESC FETCH FIRST 3 ROWS ONLY;`

- [x] **Step 12: 커밋**

```bash
git add src/
git commit -m "feat: Spring Security 로그인, 계정 잠금, SUPER/COMPANY 역할

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 7: 레이아웃, 메뉴, 오류 페이지, 예외 핸들러

**Files:**
- Create: `common/MenuItem.java`, `common/MenuRegistry.java`, `common/TenantMismatchException.java`, `common/GlobalExceptionHandler.java`
- Create: `config/WebMvcConfig.java`
- Create: `templates/layout/base.html`, `templates/fragments/sidebar.html`, `pagination.html`, `alert.html`, `confirm-modal.html`
- Create: `templates/error/403.html`, `404.html`, `500.html`
- Modify: `static/css/admin.css`
- Test: `common/MenuRegistryTest.java`

**Interfaces:**
- Produces:
  - `record MenuItem(String group, String title, String href, boolean superOnly)`
  - `MenuRegistry.itemsFor(boolean isSuper): List<MenuItem>` (Thymeleaf에서 `@menuRegistry`로 접근), `MenuRegistry.groups(List<MenuItem>): LinkedHashMap<String, List<MenuItem>>`
  - `class TenantMismatchException extends RuntimeException` → 404
  - 레이아웃 사용법: 각 페이지는 `th:replace="~{layout/base :: layout(~{::title}, ~{::main})}"` 로 `<title>`과 `<main>`을 넘긴다.
  - 프래그먼트: `fragments/pagination :: pagination(page, baseUrl)`, `fragments/alert :: flash`, `fragments/confirm-modal :: modal`.

- [x] **Step 1: MenuRegistry 테스트 작성**

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MenuRegistryTest {

    MenuRegistry registry = new MenuRegistry();

    @Test void superSeesEveryMenu() {
        assertThat(registry.itemsFor(true)).hasSize(MenuRegistry.ALL.size());
    }

    @Test void companyDoesNotSeeSuperOnlyMenus() {
        assertThat(registry.itemsFor(false)).noneMatch(MenuItem::superOnly);
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href).contains("/", "/appids", "/users", "/logs/fido")
            .doesNotContain("/companies", "/managers", "/system/props");
    }

    @Test void groupsPreserveOrder() {
        var groups = MenuRegistry.groups(registry.itemsFor(true));
        assertThat(groups.keySet()).containsExactly("대시보드", "고객사", "운영자", "FIDO", "FIDO2", "로그", "시스템");
    }
}
```

- [x] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.MenuRegistryTest' --no-daemon` → 컴파일 오류.

- [x] **Step 3: MenuItem, MenuRegistry 구현**

```java
package com.crosscert.fidoadmin.common;

public record MenuItem(String group, String title, String href, boolean superOnly) {}
```

```java
package com.crosscert.fidoadmin.common;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. 2부에서 화면이 추가될 때 항목을 늘린다. */
@Component("menuRegistry")
public class MenuRegistry {

    static final List<MenuItem> ALL = List.of(
        new MenuItem("대시보드", "통계", "/", false),
        new MenuItem("고객사", "고객사", "/companies", true),
        new MenuItem("고객사", "FDS 정책", "/fds-policies", false),
        new MenuItem("고객사", "라이선스", "/licenses", true),
        new MenuItem("운영자", "운영자", "/managers", true),
        new MenuItem("운영자", "내 비밀번호 변경", "/me/password", false),
        new MenuItem("FIDO", "앱 ID", "/appids", false),
        new MenuItem("FIDO", "앱 서버", "/appservers", false),
        new MenuItem("FIDO", "사용자", "/users", false),
        new MenuItem("FIDO", "챌린지", "/challenges", false),
        new MenuItem("FIDO", "서명", "/signs", false),
        new MenuItem("FIDO", "거래 해시", "/transaction-hashes", false),
        new MenuItem("FIDO", "거래 확인", "/transaction-confirmations", false),
        new MenuItem("FIDO", "인증기기 기준", "/criteria", false),
        new MenuItem("FIDO2", "메타데이터", "/fido2/metadata", false),
        new MenuItem("FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", false),
        new MenuItem("FIDO2", "데모 접근코드", "/fido2/demo-access-codes", false),
        new MenuItem("로그", "FIDO 로그", "/logs/fido", false),
        new MenuItem("로그", "감사 로그", "/logs/audit", false),
        new MenuItem("로그", "예외 로그", "/logs/exceptions", false),
        new MenuItem("로그", "메일/SMS 큐", "/logs/mailing", false),
        new MenuItem("시스템", "시스템 설정", "/system/props", true),
        new MenuItem("시스템", "시스템 정보", "/system/info", true),
        new MenuItem("시스템", "에러 코드", "/system/error-codes", true),
        new MenuItem("시스템", "FIDO 서버", "/system/fido-clients", true),
        new MenuItem("시스템", "어드민 기준", "/system/criteria", true),
        new MenuItem("시스템", "메뉴 정의", "/system/menus", true),
        new MenuItem("시스템", "코드 그룹/코드", "/system/options", true),
        new MenuItem("시스템", "필드 정의", "/system/fields", true));

    public List<MenuItem> itemsFor(boolean isSuper) {
        return isSuper ? ALL : ALL.stream().filter(m -> !m.superOnly()).toList();
    }

    public static LinkedHashMap<String, List<MenuItem>> groups(List<MenuItem> items) {
        LinkedHashMap<String, List<MenuItem>> map = new LinkedHashMap<>();
        for (MenuItem m : items) map.computeIfAbsent(m.group(), k -> new java.util.ArrayList<>()).add(m);
        return map;
    }
}
```

- [x] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.MenuRegistryTest' --no-daemon` → 3개 통과.

- [x] **Step 5: 예외 클래스와 핸들러**

```java
package com.crosscert.fidoadmin.common;

/** 다른 고객사의 데이터에 접근했을 때. 존재 자체를 숨기기 위해 404 로 처리한다. */
public class TenantMismatchException extends RuntimeException {
    public TenantMismatchException(String message) { super(message); }
}
```

```java
package com.crosscert.fidoadmin.common;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EntityNotFoundException.class, TenantMismatchException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Exception e) {
        log.debug("404: {}", e.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(AccessDeniedException e) {
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String internal(Exception e) {
        log.error("처리되지 않은 예외", e);
        return "error/500";
    }
}
```

`config/WebMvcConfig.java` — 오류 페이지를 Spring Security 필터 밖의 `/error`에서도 같은 템플릿으로 보여준다.

```java
package com.crosscert.fidoadmin.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error/403").setViewName("error/403");
        registry.addViewController("/error/404").setViewName("error/404");
        registry.addViewController("/error/500").setViewName("error/500");
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> errorPages() {
        return factory -> factory.addErrorPages(
            new ErrorPage(HttpStatus.FORBIDDEN, "/error/403"),
            new ErrorPage(HttpStatus.NOT_FOUND, "/error/404"),
            new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500"));
    }
}
```

`SecurityConfig`의 permitAll 목록에 `"/error/**"`를 추가한다(기존 `"/error"`를 `"/error/**"`로 바꾼다).

- [x] **Step 6: 레이아웃 템플릿**

`templates/layout/base.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:fragment="layout(title, content)">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title th:replace="${title}">FIDO Admin</title>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
  <link rel="stylesheet" th:href="@{/css/admin.css}">
</head>
<body>
<div class="fa-shell">
  <nav class="fa-sidebar" th:replace="~{fragments/sidebar :: sidebar}"></nav>
  <div class="fa-main">
    <header class="fa-topbar d-flex align-items-center justify-content-between px-3">
      <span class="fw-semibold">FIDO Admin</span>
      <div class="d-flex align-items-center gap-3 small">
        <span sec:authentication="principal.userNm">이름</span>
        <span class="text-secondary" sec:authentication="principal.companyName">고객사</span>
        <span class="badge text-bg-secondary" sec:authorize="hasRole('SUPER')">SUPER</span>
        <form th:action="@{/logout}" method="post" class="m-0">
          <button class="btn btn-outline-secondary btn-sm" type="submit">로그아웃</button>
        </form>
      </div>
    </header>
    <div class="fa-content p-3">
      <div th:replace="~{fragments/alert :: flash}"></div>
      <main th:replace="${content}"></main>
    </div>
  </div>
</div>
<div th:replace="~{fragments/confirm-modal :: modal}"></div>
<script th:src="@{/webjars/bootstrap/js/bootstrap.bundle.min.js}"></script>
<script th:src="@{/js/admin.js}"></script>
</body>
</html>
```

`templates/fragments/sidebar.html` — 현재 경로 강조는 모든 모델에 `currentPath`를 넣는 `CurrentPathAdvice`(아래)를 사용한다.

```html
<nav xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
     class="fa-sidebar" th:fragment="sidebar"
     th:with="isSuper=${#authorization.expression('hasRole(''SUPER'')')},
              groups=${T(com.crosscert.fidoadmin.common.MenuRegistry).groups(@menuRegistry.itemsFor(isSuper))}">
  <div class="fa-brand px-3 py-3">FIDO Admin</div>
  <div th:each="g : ${groups}" class="mb-2">
    <div class="fa-group px-3 text-uppercase small text-secondary" th:text="${g.key}">그룹</div>
    <a th:each="m : ${g.value}" th:href="@{${m.href}}" th:text="${m.title}"
       th:classappend="${currentPath == m.href} ? 'active' : ''"
       class="fa-link d-block px-3 py-1">메뉴</a>
  </div>
</nav>
```

`config/CurrentPathAdvice.java`

```java
package com.crosscert.fidoadmin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentPathAdvice {
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
```

`templates/fragments/alert.html`

```html
<div xmlns:th="http://www.thymeleaf.org" th:fragment="flash">
  <div class="alert alert-success alert-dismissible" th:if="${flashSuccess}">
    <span th:text="${flashSuccess}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  </div>
  <div class="alert alert-danger alert-dismissible" th:if="${flashError}">
    <span th:text="${flashError}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  </div>
</div>
```

`templates/fragments/pagination.html` — `page`는 `org.springframework.data.domain.Page`, `baseUrl`은 목록 경로. 검색 조건은 컨트롤러가 모델에 넣는 `searchQs`(`page`를 제외한 `&key=value...` 문자열, Task 8의 `SearchForm.toQueryString()`)로 유지한다.

```html
<nav xmlns:th="http://www.thymeleaf.org" th:fragment="pagination(page, baseUrl)" th:if="${page.totalPages > 1}"
     th:with="start=${T(java.lang.Math).max(0, page.number - 4)}, end=${T(java.lang.Math).min(page.totalPages - 1, page.number + 4)}, qs=${searchQs ?: ''}">
  <ul class="pagination pagination-sm mb-0">
    <li class="page-item" th:classappend="${page.first} ? 'disabled'">
      <a class="page-link" th:href="@{${baseUrl + '?page=' + (page.number - 1) + qs}}">이전</a>
    </li>
    <li class="page-item" th:each="i : ${#numbers.sequence(start, end)}" th:classappend="${i == page.number} ? 'active'">
      <a class="page-link" th:href="@{${baseUrl + '?page=' + i + qs}}" th:text="${i + 1}">1</a>
    </li>
    <li class="page-item" th:classappend="${page.last} ? 'disabled'">
      <a class="page-link" th:href="@{${baseUrl + '?page=' + (page.number + 1) + qs}}">다음</a>
    </li>
  </ul>
</nav>
```

`templates/fragments/confirm-modal.html` — 삭제 확인. 버튼에 `data-confirm-form="<form id>"`를 붙이면 `admin.js`가 모달을 띄우고 확인 시 그 폼을 제출한다.

```html
<div xmlns:th="http://www.thymeleaf.org" th:fragment="modal" class="modal fade" id="confirmModal" tabindex="-1">
  <div class="modal-dialog modal-sm">
    <div class="modal-content">
      <div class="modal-body">정말 삭제하시겠습니까?</div>
      <div class="modal-footer py-2">
        <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">취소</button>
        <button type="button" class="btn btn-sm btn-danger" id="confirmModalOk">삭제</button>
      </div>
    </div>
  </div>
</div>
```

`static/js/admin.js`

```js
document.addEventListener('DOMContentLoaded', function () {
  var modalEl = document.getElementById('confirmModal');
  if (!modalEl || typeof bootstrap === 'undefined') return;
  var modal = new bootstrap.Modal(modalEl);
  var targetFormId = null;
  document.querySelectorAll('[data-confirm-form]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      targetFormId = btn.getAttribute('data-confirm-form');
      var msg = btn.getAttribute('data-confirm-message');
      if (msg) modalEl.querySelector('.modal-body').textContent = msg;
      modal.show();
    });
  });
  document.getElementById('confirmModalOk').addEventListener('click', function () {
    var form = targetFormId && document.getElementById(targetFormId);
    if (form) form.submit();
    modal.hide();
  });
});
```

`static/css/admin.css` (전체 교체)

```css
:root { --fa-sidebar-w: 232px; --fa-topbar-h: 48px; }
body { font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans KR", system-ui, sans-serif; background: #f5f6f8; }
.fa-shell { display: flex; min-height: 100vh; }
.fa-sidebar { width: var(--fa-sidebar-w); flex: 0 0 var(--fa-sidebar-w); background: #1f2937; color: #e5e7eb; }
.fa-brand { font-weight: 700; font-size: 1.05rem; border-bottom: 1px solid #374151; }
.fa-group { letter-spacing: .06em; margin-top: .75rem; }
.fa-link { color: #d1d5db; text-decoration: none; font-size: .92rem; }
.fa-link:hover { background: #374151; color: #fff; }
.fa-link.active { background: #2563eb; color: #fff; }
.fa-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.fa-topbar { height: var(--fa-topbar-h); background: #fff; border-bottom: 1px solid #e5e7eb; }
.fa-content { flex: 1; }
.fa-table td, .fa-table th { white-space: nowrap; }
.fa-detail th { width: 220px; background: #f9fafb; }
.fa-mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: .85rem; }
.fa-pre { white-space: pre-wrap; word-break: break-all; max-height: 420px; overflow: auto; }
@media (max-width: 900px) { .fa-sidebar { display: none; } }
```

- [x] **Step 7: 오류 페이지 3개**

`templates/error/404.html` (403, 500도 같은 구조에 문구만 다르게)

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8"><title>페이지를 찾을 수 없음</title>
  <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
  <link rel="stylesheet" th:href="@{/css/admin.css}">
</head>
<body class="bg-light">
<main class="container text-center" style="margin-top: 15vh;">
  <h1 class="display-6">404</h1>
  <p class="text-secondary">요청한 페이지나 데이터를 찾을 수 없습니다.</p>
  <a th:href="@{/}" class="btn btn-primary btn-sm">대시보드로</a>
</main>
</body>
</html>
```

403: 제목 `접근 권한 없음`, 본문 `이 화면에 접근할 권한이 없습니다.` / 500: 제목 `서버 오류`, 본문 `처리 중 오류가 발생했습니다. 관리자에게 문의하세요.`

- [x] **Step 8: 레이아웃 렌더링 검증용 임시 대시보드 (Task 11에서 대체)**

`dashboard/DashboardController.java`에 최소 구현을 둔다.

```java
package com.crosscert.fidoadmin.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    @GetMapping("/")
    public String index() { return "dashboard/index"; }
}
```

`templates/dashboard/index.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>대시보드</title></head>
<body>
<main>
  <h1 class="h4 mb-3">대시보드</h1>
  <p class="text-secondary">통계 화면은 Task 11 에서 구현한다.</p>
</main>
</body>
</html>
```

- [x] **Step 9: 레이아웃 웹 테스트**

`src/test/java/com/crosscert/fidoadmin/common/LayoutWebTest.java`

```java
package com.crosscert.fidoadmin.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.dashboard.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class LayoutWebTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    static ManagerUserDetails user(long companyIdx) {
        return new ManagerUserDetails(1L, "u", null, "홍길동", companyIdx, "KB", true, true);
    }

    @Test void anonymousRedirectsToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void superSeesSystemMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(0L))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/system/props")))
            .andExpect(content().string(containsString("홍길동")));
    }

    @Test void companyDoesNotSeeSystemMenu() throws Exception {
        mvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/system/props"))))
            .andExpect(content().string(containsString("/appids")));
    }

    @Test void companyGetsForbiddenOnSuperUrl() throws Exception {
        mvc.perform(get("/companies").with(SecurityMockMvcRequestPostProcessors.user(user(1L))))
            .andExpect(status().isForbidden());
    }
}
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.LayoutWebTest' --no-daemon` → 4개 통과. 템플릿 오류가 나면 스택트레이스의 템플릿 줄 번호를 보고 고친다.

- [x] **Step 10: 수동 확인 후 커밋**

`./gradlew bootRun --args='--spring.profiles.active=local'` 후 로그인하면 사이드바·상단바·임시 대시보드가 보이고 `/nothing`은 404 페이지가 뜬다.

```bash
git add src/
git commit -m "feat: 레이아웃, 고정 메뉴, 오류 페이지, 전역 예외 처리

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 8: 공통 CRUD 기반 (SearchForm, Specs, CrudService, CrudController, ReadOnlyController)

**Files:**
- Create: `common/SearchForm.java`, `common/Specs.java`, `common/AdminRepository.java`, `common/CrudService.java`, `common/CrudController.java`, `common/ReadOnlyController.java`
- Test: `common/SearchFormTest.java`, `common/SpecsTest.java`, `common/CrudServiceTest.java`

**Interfaces:**
- Consumes: `TenantContext`, `TenantMismatchException`, `AuditLogger`, `AuditType`.
- Produces:
  - `interface AdminRepository<E, ID> extends JpaRepository<E, ID>, JpaSpecificationExecutor<E>` — 모든 화면용 리포지토리는 이걸 상속한다. Task 6의 `CcfaCompanyRepository`, `CcfaManagerRepository`, `CcfaSystemPropRepository`, Task 5의 `CcfaAuditLogRepository`를 `extends AdminRepository<...>`로 바꾼다.
  - `class SearchForm { int page=0; int size=20; String sort; Long companyIdx; LocalDate fromDate; LocalDate toDate; Pageable toPageable(Sort defaultSort); String toQueryString(); protected Map<String,Object> extraParams(); LocalDateTime fromDateTime(); LocalDateTime toDateTimeExclusive(); }`
  - `Specs.eq(attr, v)`, `Specs.like(attr, v)`(대소문자 무시, `%v%`), `Specs.between(attr, LocalDateTime from, LocalDateTime toExclusive)`, `Specs.all(Specification...)` — null 인수는 무시.
  - `abstract class CrudService<E, ID, S extends SearchForm>`: `Page<E> search(S, Pageable)`, `E get(ID)`, `E create(E)`, `E update(ID, Consumer<E>)`, `void delete(ID)`. 훅: `toSpecification(S)`, `companyIdxAttribute()`(없으면 null), `companyIdxOf(E)`, `setCompanyIdx(E, Long)`, `idOf(E)`, `tableName()`, `defaultSort()`, `applyDefaults(E)`, `touchCreated(E, LocalDateTime)`, `touchUpdated(E, LocalDateTime)`, `beforeDelete(E)`.
  - `abstract class CrudController<E, ID, F, S extends SearchForm>` 와 `abstract class ReadOnlyController<E, ID, S extends SearchForm>` — 아래 코드의 추상 메서드가 계약이다.

- [x] **Step 1: SearchForm, Specs 테스트**

`common/SearchFormTest.java`

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SearchFormTest {

    static class Sub extends SearchForm {
        String keyword = "홍 길동";
        @Override protected Map<String, Object> extraParams() {
            Map<String, Object> m = new LinkedHashMap<>(); m.put("keyword", keyword); return m;
        }
    }

    @Test void queryStringEncodesAndSkipsNullsAndPage() {
        Sub f = new Sub();
        f.setPage(3); f.setSize(50); f.setCompanyIdx(1L); f.setFromDate(LocalDate.of(2026, 9, 1));
        assertThat(f.toQueryString())
            .isEqualTo("&size=50&companyIdx=1&fromDate=2026-09-01&keyword=%ED%99%8D+%EA%B8%B8%EB%8F%99");
    }

    @Test void pageableUsesDefaultSortWhenNoneGiven() {
        SearchForm f = new SearchForm();
        Pageable p = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort().getOrderFor("idx").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test void pageableParsesSortParam() {
        SearchForm f = new SearchForm();
        f.setSort("companyName,asc");
        assertThat(f.toPageable(Sort.unsorted()).getSort().getOrderFor("companyName").getDirection())
            .isEqualTo(Sort.Direction.ASC);
    }

    @Test void sizeIsClamped() {
        SearchForm f = new SearchForm();
        f.setSize(5000);
        assertThat(f.toPageable(Sort.unsorted()).getPageSize()).isEqualTo(200);
        f.setSize(0);
        assertThat(f.toPageable(Sort.unsorted()).getPageSize()).isEqualTo(20);
    }

    @Test void dateRangeIsHalfOpen() {
        SearchForm f = new SearchForm();
        f.setFromDate(LocalDate.of(2026, 9, 1)); f.setToDate(LocalDate.of(2026, 9, 30));
        assertThat(f.fromDateTime()).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay());
        assertThat(f.toDateTimeExclusive()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
        assertThat(new SearchForm().fromDateTime()).isNull();
    }
}
```

`common/SpecsTest.java` — Specification 은 실제 DB 없이 `toPredicate`에 mock 을 넣어 검증한다.

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class SpecsTest {

    @Test void nullValuesProduceNullSpecs() {
        assertThat(Specs.<Object>eq("a", null)).isNull();
        assertThat(Specs.<Object>like("a", "  ")).isNull();
        assertThat(Specs.<Object>between("a", null, null)).isNull();
    }

    @Test void allIgnoresNullSpecsAndReturnsNullPredicateWhenEmpty() {
        Specification<Object> s = Specs.all(null, null);
        assertThat(s.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class))).isNull();
    }
}
```

- [x] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.SearchFormTest' --tests 'com.crosscert.fidoadmin.common.SpecsTest' --no-daemon` → 컴파일 오류.

- [x] **Step 3: SearchForm, Specs, AdminRepository 구현**

```java
package com.crosscert.fidoadmin.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

/** 목록 화면 공통 검색 조건. 하위 클래스는 필드를 추가하고 extraParams() 에 넣는다. */
@Getter @Setter
public class SearchForm {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    private int page = 0;
    private int size = DEFAULT_SIZE;
    /** "속성,asc|desc" */
    private String sort;
    private Long companyIdx;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate;

    public Pageable toPageable(Sort defaultSort) {
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Sort sortObj = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortObj = Sort.by(dir, parts[0].trim());
        }
        return PageRequest.of(Math.max(page, 0), s, sortObj);
    }

    public LocalDateTime fromDateTime() { return fromDate == null ? null : fromDate.atStartOfDay(); }
    public LocalDateTime toDateTimeExclusive() { return toDate == null ? null : toDate.plusDays(1).atStartOfDay(); }

    /** page 를 제외한 "&k=v&k=v" 문자열. 페이지네이션 링크에 붙인다. */
    public String toQueryString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("size", size);
        params.put("sort", sort);
        params.put("companyIdx", companyIdx);
        params.put("fromDate", fromDate);
        params.put("toDate", toDate);
        params.putAll(extraParams());
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (v == null || v.toString().isBlank()) return;
            sb.append('&').append(k).append('=').append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    protected Map<String, Object> extraParams() { return Map.of(); }
}
```

```java
package com.crosscert.fidoadmin.common;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** null 안전 Specification 헬퍼. 값이 비면 null 을 돌려주고 all() 이 무시한다. */
public final class Specs {

    private Specs() {}

    public static <E> Specification<E> eq(String attr, Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) return null;
        return (root, q, cb) -> cb.equal(root.get(attr), value);
    }

    public static <E> Specification<E> like(String attr, String value) {
        if (value == null || value.isBlank()) return null;
        String pattern = "%" + value.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get(attr)), pattern);
    }

    public static <E> Specification<E> between(String attr, LocalDateTime from, LocalDateTime toExclusive) {
        if (from == null && toExclusive == null) return null;
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get(attr), from));
            if (toExclusive != null) ps.add(cb.lessThan(root.get(attr), toExclusive));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    @SafeVarargs
    public static <E> Specification<E> all(Specification<E>... specs) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            for (Specification<E> s : specs) {
                if (s == null) continue;
                Predicate p = s.toPredicate(root, q, cb);
                if (p != null) ps.add(p);
            }
            return ps.isEmpty() ? null : cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
```

```java
package com.crosscert.fidoadmin.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface AdminRepository<E, ID> extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {
}
```

Task 5·6의 리포지토리 4개를 `extends AdminRepository<엔티티, ID>`로 바꾼다.

- [x] **Step 4: 통과 확인**

Run: 위 두 테스트 → 7개 통과.

- [x] **Step 5: CrudService 테스트**

```java
package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CrudServiceTest {

    interface LicenseRepo extends AdminRepository<CcfaLicense, Long> {}

    LicenseRepo repo = mock(LicenseRepo.class);
    AuditLogger audit = mock(AuditLogger.class);

    /** COMPANY_IDX 가 있는 테이블의 대표 구현. */
    CrudService<CcfaLicense, Long, SearchForm> service = new CrudService<>(repo, audit) {
        @Override protected Specification<CcfaLicense> toSpecification(SearchForm f) { return null; }
        @Override protected String companyIdxAttribute() { return "companyIdx"; }
        @Override protected Long companyIdxOf(CcfaLicense e) { return e.getCompanyIdx(); }
        @Override protected void setCompanyIdx(CcfaLicense e, Long c) { e.setCompanyIdx(c); }
        @Override public String idOf(CcfaLicense e) { return String.valueOf(e.getIdx()); }
        @Override protected String tableName() { return "CCFA_LICENSE"; }
        @Override protected void touchCreated(CcfaLicense e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
        @Override protected void touchUpdated(CcfaLicense e, LocalDateTime now) { e.setUpdatedtime(now); }
    };

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaLicense license(long idx, long companyIdx) {
        CcfaLicense l = new CcfaLicense(); l.setIdx(idx); l.setCompanyIdx(companyIdx); return l;
    }

    @Test void getRejectsOtherTenant() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)));
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(TenantMismatchException.class);
    }

    @Test void superCanGetAnyTenant() {
        login(0L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 2L)));
        assertThat(service.get(9L).getCompanyIdx()).isEqualTo(2L);
    }

    @Test void getThrowsWhenMissing() {
        login(0L);
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test void createForcesTenantAndTimestampsAndAudits() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaLicense l = inv.getArgument(0); l.setIdx(100L); return l; });
        CcfaLicense in = license(0L, 999L);

        CcfaLicense out = service.create(in);

        assertThat(out.getCompanyIdx()).isEqualTo(1L);
        assertThat(out.getCreatedtime()).isNotNull();
        verify(audit).log(AuditType.CREATE, "CCFA_LICENSE CREATE 100");
    }

    @Test void superKeepsGivenTenantOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(license(0L, 5L)).getCompanyIdx()).isEqualTo(5L);
    }

    @Test void updateMutatesWithinTenant() {
        login(1L);
        CcfaLicense existing = license(9L, 1L);
        when(repo.findById(9L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(9L, l -> l.setServiceName("kbstar"));

        assertThat(existing.getServiceName()).isEqualTo("kbstar");
        assertThat(existing.getUpdatedtime()).isNotNull();
        verify(audit).log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 9");
    }

    @Test void deleteChecksTenantAndAudits() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(license(9L, 1L)));
        service.delete(9L);
        verify(repo).delete(any(CcfaLicense.class));
        verify(audit).log(AuditType.DELETE, "CCFA_LICENSE DELETE 9");
    }

    @Test void searchAddsTenantFilterForCompanyRole() {
        login(1L);
        Page<CcfaLicense> page = new PageImpl<>(java.util.List.of());
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        service.search(new SearchForm(), PageRequest.of(0, 20, Sort.by("idx")));

        ArgumentCaptor<Specification<CcfaLicense>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), eq(PageRequest.of(0, 20, Sort.by("idx"))));
        assertThat(captor.getValue()).isNotNull();
    }
}
```

- [x] **Step 6: 실패 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.CrudServiceTest' --no-daemon` → 컴파일 오류.

- [x] **Step 7: CrudService 구현**

```java
package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면용 CRUD 공통 처리: 테넌트 필터·검사, 기본값, 타임스탬프, 감사 로그.
 * 하위 클래스는 @Service 를 붙이고 훅만 구현한다.
 */
public abstract class CrudService<E, ID, S extends SearchForm> {

    protected final AdminRepository<E, ID> repository;
    protected final AuditLogger audit;

    protected CrudService(AdminRepository<E, ID> repository, AuditLogger audit) {
        this.repository = repository;
        this.audit = audit;
    }

    // ---- 훅 ----
    protected abstract Specification<E> toSpecification(S form);
    /** COMPANY_IDX 에 해당하는 엔티티 속성명. 없으면 null (테넌트 필터 없음, SUPER 전용 화면). */
    protected abstract String companyIdxAttribute();
    protected abstract Long companyIdxOf(E entity);
    protected abstract void setCompanyIdx(E entity, Long companyIdx);
    public abstract String idOf(E entity);
    protected abstract String tableName();
    public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    protected void applyDefaults(E entity) {}
    protected void touchCreated(E entity, LocalDateTime now) {}
    protected void touchUpdated(E entity, LocalDateTime now) {}
    protected void beforeDelete(E entity) {}

    // ---- 공개 API ----
    @Transactional(readOnly = true)
    public Page<E> search(S form, Pageable pageable) {
        Specification<E> spec = toSpecification(form);
        String attr = companyIdxAttribute();
        if (attr != null) {
            Long filter = TenantContext.isSuper() ? form.getCompanyIdx() : TenantContext.companyIdx();
            spec = Specs.all(spec, Specs.eq(attr, filter));
        }
        return repository.findAll(spec == null ? Specs.all() : spec, pageable);
    }

    @Transactional(readOnly = true)
    public E get(ID id) {
        E e = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(tableName() + " " + id));
        checkTenant(e);
        return e;
    }

    @Transactional
    public E create(E entity) {
        if (companyIdxAttribute() != null && !TenantContext.isSuper()) {
            setCompanyIdx(entity, TenantContext.companyIdx());
        }
        applyDefaults(entity);
        touchCreated(entity, LocalDateTime.now());
        E saved = repository.save(entity);
        audit.log(AuditType.CREATE, tableName() + " CREATE " + idOf(saved));
        return saved;
    }

    @Transactional
    public E update(ID id, Consumer<E> mutator) {
        E e = get(id);
        mutator.accept(e);
        if (companyIdxAttribute() != null && !TenantContext.isSuper()) {
            setCompanyIdx(e, TenantContext.companyIdx());
        }
        touchUpdated(e, LocalDateTime.now());
        E saved = repository.save(e);
        audit.log(AuditType.UPDATE, tableName() + " UPDATE " + idOf(saved));
        return saved;
    }

    @Transactional
    public void delete(ID id) {
        E e = get(id);
        beforeDelete(e);
        repository.delete(e);
        audit.log(AuditType.DELETE, tableName() + " DELETE " + idOf(e));
    }

    protected void checkTenant(E e) {
        if (companyIdxAttribute() == null || TenantContext.isSuper()) return;
        Long owner = companyIdxOf(e);
        if (owner == null || !owner.equals(TenantContext.companyIdx())) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }
}
```

- [x] **Step 8: 통과 확인**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.common.CrudServiceTest' --no-daemon` → 8개 통과.

- [x] **Step 9: CrudController, ReadOnlyController 구현**

```java
package com.crosscert.fidoadmin.common;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 목록/상세/등록/수정/삭제 공통 컨트롤러. 하위 클래스는 @Controller @RequestMapping("/기본경로") 를 붙인다.
 * 뷰: {viewDir}/list, {viewDir}/detail, {viewDir}/form
 */
public abstract class CrudController<E, ID, F, S extends SearchForm> {

    protected abstract CrudService<E, ID, S> service();
    /** "/companies" 처럼 슬래시로 시작하는 기본 경로. */
    protected abstract String basePath();
    /** "company/company" 처럼 templates/ 아래 디렉터리. */
    protected abstract String viewDir();
    protected abstract S newSearchForm();
    protected abstract F newForm();
    protected abstract F toForm(E entity);
    protected abstract E toEntity(F form);
    protected abstract void applyForm(F form, E entity);
    /** 폼 화면에 필요한 선택 목록 등. */
    protected void populateFormModel(Model model) {}
    protected void populateListModel(Model model) {}
    protected void populateDetailModel(E entity, Model model) {}

    @GetMapping
    public String list(@ModelAttribute("search") S search, Model model) {
        Page<E> page = service().search(search, search.toPageable(service().defaultSort()));
        model.addAttribute("page", page);
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", basePath());
        populateListModel(model);
        return viewDir() + "/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", newForm());
        model.addAttribute("isNew", true);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") F form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) return backToForm(model, true);
        try {
            E saved = service().create(toEntity(form));
            redirect.addFlashAttribute("flashSuccess", "등록되었습니다.");
            return "redirect:" + basePath() + "/" + service().idOf(saved);
        } catch (DataIntegrityViolationException e) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.");
            return backToForm(model, true);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("item", entity);
        model.addAttribute("basePath", basePath());
        populateDetailModel(entity, model);
        return viewDir() + "/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("form", toForm(entity));
        model.addAttribute("isNew", false);
        model.addAttribute("id", id);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable ID id, @Valid @ModelAttribute("form") F form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("id", id);
        if (binding.hasErrors()) return backToForm(model, false);
        try {
            service().update(id, entity -> applyForm(form, entity));
            redirect.addFlashAttribute("flashSuccess", "수정되었습니다.");
            return "redirect:" + basePath() + "/" + id;
        } catch (DataIntegrityViolationException e) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.");
            return backToForm(model, false);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable ID id, RedirectAttributes redirect) {
        try {
            service().delete(id);
            redirect.addFlashAttribute("flashSuccess", "삭제되었습니다.");
            return "redirect:" + basePath();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:" + basePath() + "/" + id;
        }
    }

    private String backToForm(Model model, boolean isNew) {
        model.addAttribute("isNew", isNew);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }
}
```

```java
package com.crosscert.fidoadmin.common;

import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/** 조회 전용 화면: 목록 + 상세. */
public abstract class ReadOnlyController<E, ID, S extends SearchForm> {

    protected abstract CrudService<E, ID, S> service();
    protected abstract String basePath();
    protected abstract String viewDir();
    protected void populateListModel(Model model) {}
    protected void populateDetailModel(E entity, Model model) {}

    @GetMapping
    public String list(@ModelAttribute("search") S search, Model model) {
        Page<E> page = service().search(search, search.toPageable(service().defaultSort()));
        model.addAttribute("page", page);
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", basePath());
        populateListModel(model);
        return viewDir() + "/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("item", entity);
        model.addAttribute("basePath", basePath());
        populateDetailModel(entity, model);
        return viewDir() + "/detail";
    }
}
```

`@ModelAttribute("search") S search`와 `@PathVariable ID id`는 Spring 이 하위 컨트롤러 클래스의 제네릭 인수로 실제 타입을 해석한다(`HandlerMethod`가 containing class 기준으로 타입 변수를 푼다).

- [x] **Step 10: 전체 테스트 후 커밋**

Run: `./gradlew test --no-daemon` → 모두 통과(Docker 필요한 `EntityBootTest`는 Oracle 컨테이너가 떠 있어야 한다).

```bash
git add src/
git commit -m "feat: 공통 CRUD 기반 (검색 폼, Specification 헬퍼, 서비스·컨트롤러 추상 클래스)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 9: 참조 CRUD 화면 — 고객사 (CCFA_COMPANY)

**Files:**
- Create: `fido/repository/AppidRepository.java`, `fido/repository/UserinfoRepository.java`
- Modify: `manager/repository/CcfaManagerRepository.java` (`countByCompanyIdx` 추가)
- Create: `company/service/CompanyLookup.java`, `company/service/CompanyService.java`
- Create: `company/web/CompanySearchForm.java`, `company/web/CompanyForm.java`, `company/web/CompanyController.java`
- Create: `templates/company/company/list.html`, `form.html`, `detail.html`
- Test: `company/service/CompanyServiceTest.java`, `company/web/CompanyControllerWebTest.java`

**Interfaces:**
- Consumes: Task 8 전부, `CcfaCompanyRepository`.
- Produces:
  - `CompanyLookup.all(): List<CcfaCompany>`(IDX 오름차순), `CompanyLookup.names(): Map<Long, String>`, `CompanyLookup.name(Long): String`(없으면 `"#idx"`). 2부의 모든 화면이 고객사명 표시·선택에 사용.
  - `AppidRepository.countByCompanyIdx(Long)`, `UserinfoRepository.countByCompanyIdx(Long)`, `CcfaManagerRepository.countByCompanyIdx(Long)`.
  - 화면 경로 `/companies` (SUPER 전용).

- [x] **Step 1: 리포지토리**

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Appid;

public interface AppidRepository extends AdminRepository<Appid, Long> {
    long countByCompanyIdx(Long companyIdx);
}
```

```java
package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Userinfo;

public interface UserinfoRepository extends AdminRepository<Userinfo, Long> {
    long countByCompanyIdx(Long companyIdx);
}
```

`CcfaManagerRepository`에 `long countByCompanyIdx(Long companyIdx);` 추가.

- [x] **Step 2: CompanyService 테스트**

```java
package com.crosscert.fidoadmin.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CompanyServiceTest {

    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    AppidRepository appids = mock(AppidRepository.class);
    UserinfoRepository users = mock(UserinfoRepository.class);
    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CompanyService service = new CompanyService(companies, mock(AuditLogger.class), appids, users, managers);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaCompany company(long idx) { CcfaCompany c = new CcfaCompany(); c.setIdx(idx); return c; }

    @Test void deleteBlockedWhenDependentsExist() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(appids.countByCompanyIdx(1L)).thenReturn(2L);
        when(users.countByCompanyIdx(1L)).thenReturn(0L);
        when(managers.countByCompanyIdx(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("앱 ID 2건");
    }

    @Test void deleteGlobalCompanyIsAlwaysBlocked() {
        when(companies.findById(0L)).thenReturn(Optional.of(company(0L)));
        assertThatThrownBy(() -> service.delete(0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test void defaultsFilledOnCreate() {
        when(companies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaCompany c = service.create(new CcfaCompany());
        assertThat(c.getEnableType()).isEqualTo("Y");
        assertThat(c.getMaxAppid()).isZero();
        assertThat(c.getMaxAppserver()).isZero();
        assertThat(c.getMaxUser()).isZero();
        assertThat(c.getStarttime()).isNotNull();
        assertThat(c.getEndtime()).isEqualTo(java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        assertThat(c.getCreator()).isEqualTo(1L);
    }
}
```

- [x] **Step 3: 실패 확인 후 CompanyLookup, CompanyService 구현**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.service.CompanyServiceTest' --no-daemon` → 컴파일 오류.

```java
package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객사 IDX → 이름 조회. 화면 표시와 select 옵션에 사용. */
@Service
@RequiredArgsConstructor
public class CompanyLookup {

    private final CcfaCompanyRepository repository;

    @Transactional(readOnly = true)
    public List<CcfaCompany> all() {
        return repository.findAll(Sort.by("idx"));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (CcfaCompany c : all()) map.put(c.getIdx(), c.getCompanyName());
        return map;
    }

    @Transactional(readOnly = true)
    public String name(Long idx) {
        if (idx == null) return "";
        return repository.findById(idx).map(CcfaCompany::getCompanyName).orElse("#" + idx);
    }
}
```

```java
package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.web.CompanySearchForm;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_COMPANY. SUPER 전용이므로 테넌트 필터 없음. IDX 0 은 전역 레코드라 삭제 불가. */
@Service
public class CompanyService extends CrudService<CcfaCompany, Long, CompanySearchForm> {

    private final AppidRepository appids;
    private final UserinfoRepository users;
    private final CcfaManagerRepository managers;

    public CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids,
                          UserinfoRepository users, CcfaManagerRepository managers) {
        super(repository, audit);
        this.appids = appids;
        this.users = users;
        this.managers = managers;
    }

    @Override protected Specification<CcfaCompany> toSpecification(CompanySearchForm f) {
        return Specs.all(
            Specs.like("companyName", f.getCompanyName()),
            Specs.eq("companyType", f.getCompanyType()),
            Specs.eq("enableType", f.getEnableType()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaCompany e) { return null; }
    @Override protected void setCompanyIdx(CcfaCompany e, Long c) {}
    @Override public String idOf(CcfaCompany e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_COMPANY"; }

    @Override protected void applyDefaults(CcfaCompany e) {
        if (e.getEnableType() == null) e.setEnableType("Y");
        if (e.getMaxAppid() == null) e.setMaxAppid(0L);
        if (e.getMaxAppserver() == null) e.setMaxAppserver(0L);
        if (e.getMaxUser() == null) e.setMaxUser(0L);
        if (e.getStarttime() == null) e.setStarttime(LocalDateTime.now());
        if (e.getEndtime() == null) e.setEndtime(LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        if (e.getCreator() == null) e.setCreator(TenantContext.require().getIdx());
    }
    @Override protected void touchCreated(CcfaCompany e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaCompany e, LocalDateTime now) {
        e.setUpdatedtime(now);
        e.setUpdator(TenantContext.require().getIdx());
    }

    @Override protected void beforeDelete(CcfaCompany e) {
        if (e.getIdx() != null && e.getIdx() == 0L) throw new IllegalStateException("전역(IDX 0) 고객사는 삭제할 수 없습니다.");
        List<String> deps = new ArrayList<>();
        long a = appids.countByCompanyIdx(e.getIdx()); if (a > 0) deps.add("앱 ID " + a + "건");
        long u = users.countByCompanyIdx(e.getIdx()); if (u > 0) deps.add("사용자 " + u + "건");
        long m = managers.countByCompanyIdx(e.getIdx()); if (m > 0) deps.add("운영자 " + m + "건");
        if (!deps.isEmpty()) throw new IllegalStateException("하위 데이터가 있어 삭제할 수 없습니다: " + String.join(", ", deps));
    }
}
```

Run 다시 → 3개 통과.

- [x] **Step 4: 검색 폼·입력 폼·컨트롤러**

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CompanySearchForm extends SearchForm {
    private String companyName;
    private String companyType;
    private String enableType;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyName", companyName); m.put("companyType", companyType); m.put("enableType", enableType);
        return m;
    }
}
```

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** CCFA_COMPANY 입력 폼. 길이 제한은 ERD 값. */
@Getter @Setter
public class CompanyForm {
    @NotBlank @Size(max = 256) private String companyName;
    @Size(max = 20) private String companyType;
    @Size(max = 20) private String vendorCode;
    @Size(max = 512) private String contact;
    @Size(max = 50) private String contactPhone;
    @Size(max = 50) private String contactPhone2;
    @Size(max = 2048) private String contactAddr;
    @NotBlank @Size(max = 20) private String enableType = "Y";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime starttime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime endtime;
    @NotNull @Min(0) private Long maxAppid = 0L;
    @NotNull @Min(0) private Long maxAppserver = 0L;
    @NotNull @Min(0) private Long maxUser = 0L;
    @Size(max = 4000) private String etc;

    public static CompanyForm from(CcfaCompany c) {
        CompanyForm f = new CompanyForm();
        f.companyName = c.getCompanyName(); f.companyType = c.getCompanyType(); f.vendorCode = c.getVendorCode();
        f.contact = c.getContact(); f.contactPhone = c.getContactPhone(); f.contactPhone2 = c.getContactPhone2();
        f.contactAddr = c.getContactAddr(); f.enableType = c.getEnableType(); f.starttime = c.getStarttime();
        f.endtime = c.getEndtime(); f.maxAppid = c.getMaxAppid(); f.maxAppserver = c.getMaxAppserver();
        f.maxUser = c.getMaxUser(); f.etc = c.getEtc();
        return f;
    }

    public void applyTo(CcfaCompany c) {
        c.setCompanyName(companyName); c.setCompanyType(companyType); c.setVendorCode(vendorCode);
        c.setContact(contact); c.setContactPhone(contactPhone); c.setContactPhone2(contactPhone2);
        c.setContactAddr(contactAddr); c.setEnableType(enableType); c.setStarttime(starttime);
        c.setEndtime(endtime); c.setMaxAppid(maxAppid); c.setMaxAppserver(maxAppserver);
        c.setMaxUser(maxUser); c.setEtc(etc);
    }
}
```

```java
package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController extends CrudController<CcfaCompany, Long, CompanyForm, CompanySearchForm> {

    private final CompanyService service;

    @Override protected CrudService<CcfaCompany, Long, CompanySearchForm> service() { return service; }
    @Override protected String basePath() { return "/companies"; }
    @Override protected String viewDir() { return "company/company"; }
    @Override protected CompanySearchForm newSearchForm() { return new CompanySearchForm(); }
    @Override protected CompanyForm newForm() { return new CompanyForm(); }
    @Override protected CompanyForm toForm(CcfaCompany e) { return CompanyForm.from(e); }
    @Override protected CcfaCompany toEntity(CompanyForm f) { CcfaCompany c = new CcfaCompany(); f.applyTo(c); return c; }
    @Override protected void applyForm(CompanyForm f, CcfaCompany e) { f.applyTo(e); }
}
```

- [x] **Step 5: 템플릿 3개**

`templates/company/company/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>고객사</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0">고객사</h1>
    <a th:href="@{/companies/new}" class="btn btn-primary btn-sm">등록</a>
  </div>

  <form method="get" th:action="@{/companies}" class="row g-2 align-items-end mb-3">
    <div class="col-auto">
      <label class="form-label small mb-0">이름</label>
      <input name="companyName" th:value="${search.companyName}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">유형</label>
      <input name="companyType" th:value="${search.companyType}" class="form-control form-control-sm">
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">사용</label>
      <select name="enableType" class="form-select form-select-sm">
        <option value="">전체</option>
        <option value="Y" th:selected="${search.enableType == 'Y'}">Y</option>
        <option value="N" th:selected="${search.enableType == 'N'}">N</option>
      </select>
    </div>
    <div class="col-auto">
      <button class="btn btn-outline-secondary btn-sm">검색</button>
      <a th:href="@{/companies}" class="btn btn-link btn-sm">초기화</a>
    </div>
  </form>

  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light">
        <tr><th>IDX</th><th>이름</th><th>유형</th><th>벤더코드</th><th>연락처</th><th>사용</th><th>최대 사용자</th><th>등록일</th></tr>
      </thead>
      <tbody>
        <tr th:each="c : ${page.content}">
          <td th:text="${c.idx}">0</td>
          <td><a th:href="@{/companies/{id}(id=${c.idx})}" th:text="${c.companyName}">이름</a></td>
          <td th:text="${c.companyType}"></td>
          <td th:text="${c.vendorCode}"></td>
          <td th:text="${c.contact}"></td>
          <td th:text="${c.enableType}"></td>
          <td th:text="${c.maxUser}"></td>
          <td th:text="${#temporals.format(c.createdtime, 'yyyy-MM-dd HH:mm')}"></td>
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

`templates/company/company/form.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title th:text="${isNew} ? '고객사 등록' : '고객사 수정'">고객사</title></head>
<body>
<main>
  <h1 class="h4 mb-3" th:text="${isNew} ? '고객사 등록' : '고객사 수정'">고객사</h1>
  <form th:action="${isNew} ? @{/companies} : @{/companies/{id}(id=${id})}" th:object="${form}" method="post"
        class="bg-white border rounded p-3" style="max-width: 760px;">
    <div class="alert alert-danger py-2" th:if="${#fields.hasGlobalErrors()}">
      <div th:each="e : ${#fields.globalErrors()}" th:text="${e}"></div>
    </div>
    <div class="row g-3">
      <div class="col-md-6">
        <label class="form-label">이름 <span class="text-danger">*</span></label>
        <input th:field="*{companyName}" class="form-control" th:classappend="${#fields.hasErrors('companyName')} ? 'is-invalid'">
        <div class="invalid-feedback" th:errors="*{companyName}"></div>
      </div>
      <div class="col-md-3"><label class="form-label">유형</label><input th:field="*{companyType}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">벤더코드</label><input th:field="*{vendorCode}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">담당자/이메일</label><input th:field="*{contact}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">전화</label><input th:field="*{contactPhone}" class="form-control"></div>
      <div class="col-md-3"><label class="form-label">전화2</label><input th:field="*{contactPhone2}" class="form-control"></div>
      <div class="col-12"><label class="form-label">주소</label><input th:field="*{contactAddr}" class="form-control"></div>
      <div class="col-md-3">
        <label class="form-label">사용 <span class="text-danger">*</span></label>
        <select th:field="*{enableType}" class="form-select"><option value="Y">Y</option><option value="N">N</option></select>
      </div>
      <div class="col-md-3"><label class="form-label">최대 앱 ID</label><input type="number" min="0" th:field="*{maxAppid}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{maxAppid}"></div></div>
      <div class="col-md-3"><label class="form-label">최대 앱 서버</label><input type="number" min="0" th:field="*{maxAppserver}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{maxAppserver}"></div></div>
      <div class="col-md-3"><label class="form-label">최대 사용자</label><input type="number" min="0" th:field="*{maxUser}" class="form-control"><div class="invalid-feedback d-block" th:errors="*{maxUser}"></div></div>
      <div class="col-md-6"><label class="form-label">시작일시</label><input type="datetime-local" th:field="*{starttime}" class="form-control"></div>
      <div class="col-md-6"><label class="form-label">종료일시</label><input type="datetime-local" th:field="*{endtime}" class="form-control"></div>
      <div class="col-12"><label class="form-label">비고</label><textarea th:field="*{etc}" rows="3" class="form-control"></textarea></div>
    </div>
    <div class="mt-3 d-flex gap-2">
      <button class="btn btn-primary">저장</button>
      <a th:href="${isNew} ? @{/companies} : @{/companies/{id}(id=${id})}" class="btn btn-outline-secondary">취소</a>
    </div>
  </form>
</main>
</body>
</html>
```

`templates/company/company/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>고객사 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|고객사 #${item.idx}|">고객사</h1>
    <div class="d-flex gap-2">
      <a th:href="@{/companies/{id}/edit(id=${item.idx})}" class="btn btn-outline-primary btn-sm">수정</a>
      <form th:action="@{/companies/{id}/delete(id=${item.idx})}" method="post" id="deleteForm" class="m-0">
        <button type="button" class="btn btn-outline-danger btn-sm" data-confirm-form="deleteForm"
                th:if="${item.idx != 0}">삭제</button>
      </form>
      <a th:href="@{/companies}" class="btn btn-link btn-sm">목록</a>
    </div>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0">
      <tbody>
        <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
        <tr><th>이름</th><td th:text="${item.companyName}"></td></tr>
        <tr><th>유형</th><td th:text="${item.companyType}"></td></tr>
        <tr><th>벤더코드</th><td th:text="${item.vendorCode}"></td></tr>
        <tr><th>담당자/이메일</th><td th:text="${item.contact}"></td></tr>
        <tr><th>전화</th><td th:text="${item.contactPhone}"></td></tr>
        <tr><th>전화2</th><td th:text="${item.contactPhone2}"></td></tr>
        <tr><th>주소</th><td th:text="${item.contactAddr}"></td></tr>
        <tr><th>사용</th><td th:text="${item.enableType}"></td></tr>
        <tr><th>시작일시</th><td th:text="${#temporals.format(item.starttime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>종료일시</th><td th:text="${#temporals.format(item.endtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>최대 앱 ID</th><td th:text="${item.maxAppid}"></td></tr>
        <tr><th>최대 앱 서버</th><td th:text="${item.maxAppserver}"></td></tr>
        <tr><th>최대 사용자</th><td th:text="${item.maxUser}"></td></tr>
        <tr><th>비고</th><td class="fa-pre" th:text="${item.etc}"></td></tr>
        <tr><th>생성자</th><td th:text="${item.creator}"></td></tr>
        <tr><th>수정자</th><td th:text="${item.updator}"></td></tr>
        <tr><th>등록일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
        <tr><th>수정일시</th><td th:text="${#temporals.format(item.updatedtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      </tbody>
    </table>
  </div>
</main>
</body>
</html>
```

`#temporals`는 `thymeleaf-extras-java8time`이 Thymeleaf 3.1에 내장되어 있어 별도 의존성이 없다. null 이면 빈 문자열이 출력된다.

- [x] **Step 6: 웹 테스트**

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
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
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

@WebMvcTest(controllers = CompanyController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class CompanyControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean CompanyService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void listRendersRows() throws Exception {
        CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(c)));
        mvc.perform(get("/companies").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/list"))
            .andExpect(content().string(containsString("KB국민은행")));
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/companies").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void createWithBlankNameShowsFormAgain() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        CcfaCompany saved = new CcfaCompany(); saved.setIdx(77L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("77");
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "신규").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/companies/77"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).param("companyName", "x")).andExpect(status().isForbidden());
    }
}
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.company.web.CompanyControllerWebTest' --no-daemon` → 5개 통과.

- [x] **Step 7: 수동 확인 후 커밋**

`bootRun` 후 `superuser`로 `/companies` 목록·검색·등록·수정·삭제(하위 데이터가 있는 IDX 1은 차단 메시지, IDX 2 는 AWS_INFO 만 있으므로 삭제 가능)를 확인하고 `CCFA_AUDIT_LOG`에 `CREATE/UPDATE/DELETE` 행이 남는지 본다.

```bash
git add src/
git commit -m "feat: 고객사 CRUD 화면 (참조 구현)과 CompanyLookup

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 10: 참조 조회 화면 — 감사 로그 (CCFA_AUDIT_LOG)

**Files:**
- Create: `log/service/AuditLogQueryService.java`
- Create: `log/web/AuditLogSearchForm.java`, `log/web/AuditLogController.java`
- Create: `templates/log/audit/list.html`, `detail.html`
- Test: `log/web/AuditLogControllerWebTest.java`

**Interfaces:**
- Consumes: `ReadOnlyController`, `CrudService`, `CcfaAuditLogRepository`(AdminRepository), `CompanyLookup`.
- Produces: 화면 `/logs/audit`. COMPANY 역할은 자기 고객사 로그만 본다.

- [x] **Step 1: 검색 폼과 서비스**

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AuditLogSearchForm extends SearchForm {
    private String userId;
    private String type;
    private String message;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId); m.put("type", type); m.put("message", message);
        return m;
    }
}
```

```java
package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import com.crosscert.fidoadmin.log.web.AuditLogSearchForm;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_AUDIT_LOG 조회 전용. create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class AuditLogQueryService extends CrudService<CcfaAuditLog, Long, AuditLogSearchForm> {

    public AuditLogQueryService(CcfaAuditLogRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<CcfaAuditLog> toSpecification(AuditLogSearchForm f) {
        return Specs.all(
            Specs.like("userId", f.getUserId()),
            Specs.eq("type", f.getType()),
            Specs.like("message", f.getMessage()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaAuditLog e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaAuditLog e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaAuditLog e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_AUDIT_LOG"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createdtime", "idx"); }
}
```

- [x] **Step 2: 컨트롤러**

```java
package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/audit")
@RequiredArgsConstructor
public class AuditLogController extends ReadOnlyController<CcfaAuditLog, Long, AuditLogSearchForm> {

    private final AuditLogQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaAuditLog, Long, AuditLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/audit"; }
    @Override protected String viewDir() { return "log/audit"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("types", AuditType.values());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
}
```

- [x] **Step 3: 템플릿**

`templates/log/audit/list.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>감사 로그</title></head>
<body>
<main>
  <h1 class="h4 mb-3">감사 로그</h1>
  <form method="get" th:action="@{/logs/audit}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">사용자</label><input name="userId" th:value="${search.userId}" class="form-control form-control-sm"></div>
    <div class="col-auto">
      <label class="form-label small mb-0">유형</label>
      <select name="type" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="t : ${types}" th:value="${t}" th:text="${t}" th:selected="${search.type == t.name()}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">메시지</label><input name="message" th:value="${search.message}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">검색</button> <a th:href="@{/logs/audit}" class="btn btn-link btn-sm">초기화</a></div>
  </form>
  <div class="table-responsive bg-white border rounded">
    <table class="table table-sm table-hover fa-table mb-0">
      <thead class="table-light"><tr><th>IDX</th><th>일시</th><th>고객사</th><th>유형</th><th>사용자</th><th>메시지</th><th>IP</th></tr></thead>
      <tbody>
        <tr th:each="r : ${page.content}">
          <td><a th:href="@{/logs/audit/{id}(id=${r.idx})}" th:text="${r.idx}">0</a></td>
          <td th:text="${#temporals.format(r.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td>
          <td th:text="${r.companyName}"></td>
          <td><span class="badge text-bg-secondary" th:text="${r.type}"></span></td>
          <td th:text="${r.userId}"></td>
          <td th:text="${#strings.abbreviate(r.message, 80)}"></td>
          <td th:text="${r.ip}"></td>
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

`templates/log/audit/detail.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>감사 로그 상세</title></head>
<body>
<main>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h1 class="h4 mb-0" th:text="|감사 로그 #${item.idx}|"></h1>
    <a th:href="@{/logs/audit}" class="btn btn-link btn-sm">목록</a>
  </div>
  <div class="bg-white border rounded">
    <table class="table table-sm fa-detail mb-0"><tbody>
      <tr><th>IDX</th><td th:text="${item.idx}"></td></tr>
      <tr><th>일시</th><td th:text="${#temporals.format(item.createdtime, 'yyyy-MM-dd HH:mm:ss')}"></td></tr>
      <tr><th>고객사</th><td th:text="|${item.companyName} (${item.companyIdx})|"></td></tr>
      <tr><th>유형</th><td th:text="${item.type}"></td></tr>
      <tr><th>사용자</th><td th:text="|${item.userName} (${item.userId})|"></td></tr>
      <tr><th>메시지</th><td class="fa-pre" th:text="${item.message}"></td></tr>
      <tr><th>IP</th><td th:text="${item.ip}"></td></tr>
      <tr><th>User-Agent</th><td class="fa-pre" th:text="${item.ua}"></td></tr>
      <tr><th>무결성 해시</th><td class="fa-mono" th:text="${item.intergrityHash}"></td></tr>
    </tbody></table>
  </div>
</main>
</body>
</html>
```

- [x] **Step 4: 웹 테스트**

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

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.service.AuditLogQueryService;
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

@WebMvcTest(controllers = AuditLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class AuditLogControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuditLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);

    @Test void companyUserSeesListWithoutCompanyFilter() throws Exception {
        CcfaAuditLog r = new CcfaAuditLog(); r.setIdx(5L); r.setMessage("APPID UPDATE 1"); r.setType("UPDATE");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(r)));
        mvc.perform(get("/logs/audit").param("type", "UPDATE").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("APPID UPDATE 1")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        ArgumentCaptor<AuditLogSearchForm> captor = ArgumentCaptor.forClass(AuditLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getType()).isEqualTo("UPDATE");
    }

    @Test void superUserSeesCompanyFilter() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/logs/audit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"companyIdx\"")));
    }
}
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.log.web.AuditLogControllerWebTest' --no-daemon` → 2개 통과.

- [x] **Step 5: 수동 확인 후 커밋**

`kbadmin`으로 로그인하면 `/logs/audit`에 COMPANY_IDX 1 로그만 보이고 고객사 선택이 없다. `superuser`는 전체와 고객사 필터가 보인다.

```bash
git add src/
git commit -m "feat: 감사 로그 조회 화면 (조회 전용 참조 구현)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 11: 대시보드 (FIDO_STATISTICS 집계 + Chart.js)

**Files:**
- Create: `dashboard/DailyStat.java`, `dashboard/StatTotals.java`, `dashboard/DashboardSearchForm.java`, `dashboard/StatisticsQueryService.java`
- Modify: `dashboard/DashboardController.java`, `templates/dashboard/index.html`
- Test: `dashboard/StatTotalsTest.java`, `dashboard/DashboardControllerWebTest.java`

**Interfaces:**
- Consumes: `NamedParameterJdbcTemplate`(spring-jdbc, JPA 스타터에 포함), `TenantContext`, `CompanyLookup`.
- Produces:
  - `record DailyStat(LocalDate date, long authS, long authF, long tcS, long tcF, long regS, long regF, long deregS, long deregF)`
  - `record StatTotals(long authS, long authF, long tcS, long tcF, long regS, long regF, long deregS, long deregF) { static StatTotals of(List<DailyStat>) }`
  - `StatisticsQueryService.daily(DashboardSearchForm): List<DailyStat>`, `groupbys(): List<String>`, `serviceNames(Long companyIdx): List<String>`

- [x] **Step 1: StatTotals 테스트와 레코드**

```java
package com.crosscert.fidoadmin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatTotalsTest {
    @Test void sumsEveryColumn() {
        StatTotals t = StatTotals.of(List.of(
            new DailyStat(LocalDate.of(2026, 9, 1), 10, 1, 2, 0, 3, 0, 1, 0),
            new DailyStat(LocalDate.of(2026, 9, 2), 5, 2, 1, 1, 0, 1, 0, 1)));
        assertThat(t).isEqualTo(new StatTotals(15, 3, 3, 1, 3, 1, 1, 1));
        assertThat(t.authTotal()).isEqualTo(18);
        assertThat(t.authSuccessRate()).isEqualTo(83.3);
    }
    @Test void emptyIsZeroAndRateIsZero() {
        assertThat(StatTotals.of(List.of()).authSuccessRate()).isZero();
    }
}
```

```java
package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;

public record DailyStat(LocalDate date, long authS, long authF, long tcS, long tcF,
                        long regS, long regF, long deregS, long deregF) {}
```

```java
package com.crosscert.fidoadmin.dashboard;

import java.util.List;

public record StatTotals(long authS, long authF, long tcS, long tcF, long regS, long regF, long deregS, long deregF) {

    public static StatTotals of(List<DailyStat> rows) {
        long[] s = new long[8];
        for (DailyStat r : rows) {
            s[0] += r.authS(); s[1] += r.authF(); s[2] += r.tcS(); s[3] += r.tcF();
            s[4] += r.regS(); s[5] += r.regF(); s[6] += r.deregS(); s[7] += r.deregF();
        }
        return new StatTotals(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]);
    }

    public long authTotal() { return authS + authF; }

    /** 소수 첫째 자리까지. 분모 0 이면 0. */
    public double authSuccessRate() {
        return authTotal() == 0 ? 0.0 : Math.round(authS * 1000.0 / authTotal()) / 10.0;
    }
}
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.dashboard.StatTotalsTest' --no-daemon` → 2개 통과.

- [x] **Step 2: 검색 폼과 조회 서비스**

```java
package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter @Setter
public class DashboardSearchForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate = LocalDate.now().minusDays(29);
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate = LocalDate.now();
    private Long companyIdx;
    private String serviceName;
    private String groupby;
}
```

```java
package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIDO_STATISTICS 를 일자별로 합산한다. 읽기 전용 SQL 만 쓴다.
 * GROUPBY 값은 운영 데이터에 따라 다르므로 화면에서 고르게 하고, 없으면 첫 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class StatisticsQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<String> groupbys() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT GROUPBY FROM FIDO_STATISTICS ORDER BY GROUPBY", String.class);
    }

    @Transactional(readOnly = true)
    public List<String> serviceNames(Long companyIdx) {
        Map<String, Object> p = new HashMap<>();
        p.put("companyIdx", companyIdx);
        return jdbc.queryForList(
            "SELECT DISTINCT SERVICE_NAME FROM FIDO_STATISTICS WHERE (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx) ORDER BY SERVICE_NAME",
            p, String.class);
    }

    @Transactional(readOnly = true)
    public List<DailyStat> daily(DashboardSearchForm f) {
        Long companyIdx = TenantContext.isSuper() ? f.getCompanyIdx() : TenantContext.companyIdx();
        Map<String, Object> p = new HashMap<>();
        p.put("groupby", f.getGroupby());
        p.put("from", Timestamp.valueOf(f.getFromDate().atStartOfDay()));
        p.put("to", Timestamp.valueOf(f.getToDate().plusDays(1).atStartOfDay()));
        p.put("companyIdx", companyIdx);
        p.put("serviceName", f.getServiceName() == null || f.getServiceName().isBlank() ? null : f.getServiceName());
        String sql = """
            SELECT TRUNC(CREATEDTIME) AS D,
                   NVL(SUM(AUTH_S),0) AUTH_S, NVL(SUM(AUTH_F),0) AUTH_F, NVL(SUM(TC_S),0) TC_S, NVL(SUM(TC_F),0) TC_F,
                   NVL(SUM(REG_S),0) REG_S, NVL(SUM(REG_F),0) REG_F, NVL(SUM(DEREG_S),0) DEREG_S, NVL(SUM(DEREG_F),0) DEREG_F
              FROM FIDO_STATISTICS
             WHERE GROUPBY = :groupby
               AND CREATEDTIME >= :from AND CREATEDTIME < :to
               AND (:companyIdx IS NULL OR COMPANY_IDX = :companyIdx)
               AND (:serviceName IS NULL OR SERVICE_NAME = :serviceName)
             GROUP BY TRUNC(CREATEDTIME)
             ORDER BY 1
            """;
        return jdbc.query(sql, p, (rs, i) -> new DailyStat(
            rs.getTimestamp("D").toLocalDateTime().toLocalDate(),
            rs.getLong("AUTH_S"), rs.getLong("AUTH_F"), rs.getLong("TC_S"), rs.getLong("TC_F"),
            rs.getLong("REG_S"), rs.getLong("REG_F"), rs.getLong("DEREG_S"), rs.getLong("DEREG_F")));
    }
}
```

Oracle 에서 `:companyIdx IS NULL` 에 null 을 바인딩하면 타입을 알 수 없어 `ORA-17004` 가 날 수 있다. 그 경우 `MapSqlParameterSource` 로 바꿔 `addValue("companyIdx", companyIdx, java.sql.Types.NUMERIC)`, `addValue("serviceName", ..., java.sql.Types.VARCHAR)` 로 타입을 명시한다. 로컬 Oracle 에서 Step 5 로 확인한다.

- [x] **Step 3: 컨트롤러 교체**

```java
package com.crosscert.fidoadmin.dashboard;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StatisticsQueryService stats;
    private final CompanyLookup companies;

    @GetMapping("/")
    public String index(@ModelAttribute("search") DashboardSearchForm search, Model model) {
        List<String> groupbys = stats.groupbys();
        if ((search.getGroupby() == null || search.getGroupby().isBlank()) && !groupbys.isEmpty()) {
            search.setGroupby(groupbys.get(0));
        }
        Long companyForNames = TenantContext.isSuper() ? search.getCompanyIdx() : TenantContext.companyIdx();
        List<DailyStat> daily = search.getGroupby() == null ? List.of() : stats.daily(search);
        model.addAttribute("groupbys", groupbys);
        model.addAttribute("serviceNames", stats.serviceNames(companyForNames));
        model.addAttribute("daily", daily);
        model.addAttribute("totals", StatTotals.of(daily));
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
        return "dashboard/index";
    }
}
```

- [x] **Step 4: 템플릿 교체**

`templates/dashboard/index.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6"
      th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>대시보드</title></head>
<body>
<main>
  <h1 class="h4 mb-3">대시보드</h1>
  <form method="get" th:action="@{/}" class="row g-2 align-items-end mb-3">
    <div class="col-auto" sec:authorize="hasRole('SUPER')">
      <label class="form-label small mb-0">고객사</label>
      <select name="companyIdx" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="c : ${companies}" th:value="${c.idx}" th:text="${c.companyName}" th:selected="${search.companyIdx == c.idx}"></option>
      </select>
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">서비스</label>
      <select name="serviceName" class="form-select form-select-sm">
        <option value="">전체</option>
        <option th:each="s : ${serviceNames}" th:value="${s}" th:text="${s}" th:selected="${search.serviceName == s}"></option>
      </select>
    </div>
    <div class="col-auto">
      <label class="form-label small mb-0">집계 단위</label>
      <select name="groupby" class="form-select form-select-sm">
        <option th:each="g : ${groupbys}" th:value="${g}" th:text="${g}" th:selected="${search.groupby == g}"></option>
      </select>
    </div>
    <div class="col-auto"><label class="form-label small mb-0">시작일</label><input type="date" name="fromDate" th:value="${search.fromDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><label class="form-label small mb-0">종료일</label><input type="date" name="toDate" th:value="${search.toDate}" class="form-control form-control-sm"></div>
    <div class="col-auto"><button class="btn btn-outline-secondary btn-sm">조회</button></div>
  </form>

  <div class="row g-3 mb-3">
    <div class="col-6 col-lg-3"><div class="bg-white border rounded p-3"><div class="small text-secondary">인증 성공 / 실패</div><div class="h5 mb-0"><span th:text="${#numbers.formatInteger(totals.authS, 0, 'COMMA')}">0</span> / <span class="text-danger" th:text="${#numbers.formatInteger(totals.authF, 0, 'COMMA')}">0</span></div><div class="small text-secondary" th:text="|성공률 ${totals.authSuccessRate}%|"></div></div></div>
    <div class="col-6 col-lg-3"><div class="bg-white border rounded p-3"><div class="small text-secondary">등록 성공 / 실패</div><div class="h5 mb-0"><span th:text="${#numbers.formatInteger(totals.regS, 0, 'COMMA')}">0</span> / <span class="text-danger" th:text="${#numbers.formatInteger(totals.regF, 0, 'COMMA')}">0</span></div></div></div>
    <div class="col-6 col-lg-3"><div class="bg-white border rounded p-3"><div class="small text-secondary">해지 성공 / 실패</div><div class="h5 mb-0"><span th:text="${#numbers.formatInteger(totals.deregS, 0, 'COMMA')}">0</span> / <span class="text-danger" th:text="${#numbers.formatInteger(totals.deregF, 0, 'COMMA')}">0</span></div></div></div>
    <div class="col-6 col-lg-3"><div class="bg-white border rounded p-3"><div class="small text-secondary">거래확인 성공 / 실패</div><div class="h5 mb-0"><span th:text="${#numbers.formatInteger(totals.tcS, 0, 'COMMA')}">0</span> / <span class="text-danger" th:text="${#numbers.formatInteger(totals.tcF, 0, 'COMMA')}">0</span></div></div></div>
  </div>

  <div class="bg-white border rounded p-3">
    <div class="small text-secondary mb-2">일별 인증·등록 추이</div>
    <canvas id="dailyChart" height="90"></canvas>
    <p class="text-secondary small mb-0 mt-2" th:if="${#lists.isEmpty(daily)}">기간 내 통계가 없습니다.</p>
  </div>

  <script th:inline="javascript">
    /*<![CDATA[*/
    window.__daily = /*[[${daily}]]*/ [];
    /*]]>*/
  </script>
  <script th:src="@{/webjars/chart.js/dist/chart.umd.js}"></script>
  <script>
    (function () {
      var rows = window.__daily || [];
      var el = document.getElementById('dailyChart');
      if (!el || !rows.length || typeof Chart === 'undefined') return;
      new Chart(el, {
        type: 'line',
        data: {
          labels: rows.map(function (r) { return r.date; }),
          datasets: [
            { label: '인증 성공', data: rows.map(function (r) { return r.authS; }), borderColor: '#2563eb', tension: .2 },
            { label: '인증 실패', data: rows.map(function (r) { return r.authF; }), borderColor: '#dc2626', tension: .2 },
            { label: '등록 성공', data: rows.map(function (r) { return r.regS; }), borderColor: '#16a34a', tension: .2 }
          ]
        },
        options: { responsive: true, interaction: { mode: 'index', intersect: false }, scales: { y: { beginAtZero: true } } }
      });
    })();
  </script>
</main>
</body>
</html>
```

`th:inline="javascript"`가 `DailyStat` 레코드를 JSON 으로 직렬화한다(`date`는 `"2026-09-01"` 문자열). chart.js WebJar 의 실제 파일 경로는 `build/` 아래 jar 를 열어 `META-INF/resources/webjars/chart.js/4.5.0/dist/chart.umd.js` 인지 확인한다. 다르면 `th:src`를 맞춘다.

- [x] **Step 5: 웹 테스트와 수동 확인**

```java
package com.crosscert.fidoadmin.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class DashboardControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatisticsQueryService stats;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    @Test void rendersTotalsAndSerializedRows() throws Exception {
        when(stats.groupbys()).thenReturn(List.of("day"));
        when(stats.serviceNames(any())).thenReturn(List.of("kbstar"));
        when(stats.daily(any())).thenReturn(List.of(new DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 0, 0, 10, 0, 0, 0)));
        var user = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        mvc.perform(get("/").with(user(user)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")))
            .andExpect(content().string(containsString("\"date\":\"2026-09-01\"")));
    }
}
```

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.dashboard.DashboardControllerWebTest' --no-daemon` → 통과.

수동: `bootRun` 후 `/`에 시드 30일 데이터의 카드와 선 그래프가 나오고, 브라우저 개발자 도구 네트워크 탭에 `localhost:8080` 이외의 호출이 **없어야** 한다.

- [x] **Step 6: 커밋**

```bash
git add src/
git commit -m "feat: FIDO_STATISTICS 대시보드 (합계 카드, 일별 추이 차트)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 12: 내 비밀번호 변경

**Files:**
- Create: `auth/PasswordChangeForm.java`, `auth/PasswordChangeService.java`, `auth/PasswordChangeController.java`
- Create: `templates/auth/password.html`
- Test: `auth/PasswordChangeServiceTest.java`

**Interfaces:**
- Consumes: `CcfaManagerRepository`, `PasswordEncoder`(Sha256), `AuditLogger`, `TenantContext`.
- Produces: 화면 `GET/POST /me/password`. `PasswordChangeService.change(String userId, String current, String next)` — 현재 비밀번호 불일치면 `IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.")`.

- [x] **Step 1: 서비스 테스트**

```java
package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordChangeServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    PasswordChangeService service = new PasswordChangeService(managers, new Sha256PasswordEncoder(), mock(AuditLogger.class));

    private CcfaManager manager() {
        CcfaManager m = new CcfaManager();
        m.setUserId("kbadmin");
        m.setUserPw("c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461"); // Company1234!
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m));
        return m;
    }

    @Test void changesHashWhenCurrentMatches() {
        CcfaManager m = manager();
        service.change("kbadmin", "Company1234!", "NewPass5678!");
        assertThat(m.getUserPw()).isEqualTo(Sha256PasswordEncoder.sha256Hex("NewPass5678!"));
        assertThat(m.getUpdatedtime()).isNotNull();
    }

    @Test void rejectsWrongCurrent() {
        manager();
        assertThatThrownBy(() -> service.change("kbadmin", "wrong", "NewPass5678!"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("현재 비밀번호");
    }
}
```

- [x] **Step 2: 실패 확인 후 구현**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.auth.PasswordChangeServiceTest' --no-daemon` → 컴파일 오류.

```java
package com.crosscert.fidoadmin.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PasswordChangeForm {
    @NotBlank private String currentPassword;
    @NotBlank @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$", message = "영문, 숫자, 특수문자를 모두 포함해야 합니다.")
    private String newPassword;
    @NotBlank private String confirmPassword;
}
```

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final CcfaManagerRepository managers;
    private final PasswordEncoder encoder;
    private final AuditLogger audit;

    @Transactional
    public void change(String userId, String current, String next) {
        CcfaManager m = managers.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("운영자를 찾을 수 없습니다."));
        if (!encoder.matches(current, m.getUserPw())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        m.setUserPw(encoder.encode(next));
        m.setUpdatedtime(LocalDateTime.now());
        managers.save(m);
        audit.log(AuditType.UPDATE, "CCFA_MANAGER PASSWORD " + userId);
    }
}
```

```java
package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.common.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/me/password")
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService service;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "auth/password";
    }

    @PostMapping
    public String change(@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors() && !form.getNewPassword().equals(form.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (binding.hasErrors()) return "auth/password";
        try {
            service.change(TenantContext.require().getUserId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (IllegalArgumentException e) {
            binding.rejectValue("currentPassword", "invalid", e.getMessage());
            return "auth/password";
        }
        redirect.addFlashAttribute("flashSuccess", "비밀번호가 변경되었습니다.");
        return "redirect:/";
    }
}
```

`templates/auth/password.html`

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org" th:replace="~{layout/base :: layout(~{::title}, ~{::main})}">
<head><title>내 비밀번호 변경</title></head>
<body>
<main>
  <h1 class="h4 mb-3">내 비밀번호 변경</h1>
  <form th:action="@{/me/password}" th:object="${form}" method="post" class="bg-white border rounded p-3" style="max-width: 480px;">
    <div class="mb-3">
      <label class="form-label">현재 비밀번호</label>
      <input type="password" th:field="*{currentPassword}" class="form-control" th:classappend="${#fields.hasErrors('currentPassword')} ? 'is-invalid'" autocomplete="current-password">
      <div class="invalid-feedback" th:errors="*{currentPassword}"></div>
    </div>
    <div class="mb-3">
      <label class="form-label">새 비밀번호</label>
      <input type="password" th:field="*{newPassword}" class="form-control" th:classappend="${#fields.hasErrors('newPassword')} ? 'is-invalid'" autocomplete="new-password">
      <div class="invalid-feedback" th:errors="*{newPassword}"></div>
      <div class="form-text">8~64자, 영문·숫자·특수문자 포함</div>
    </div>
    <div class="mb-3">
      <label class="form-label">새 비밀번호 확인</label>
      <input type="password" th:field="*{confirmPassword}" class="form-control" th:classappend="${#fields.hasErrors('confirmPassword')} ? 'is-invalid'" autocomplete="new-password">
      <div class="invalid-feedback" th:errors="*{confirmPassword}"></div>
    </div>
    <button class="btn btn-primary">변경</button>
  </form>
</main>
</body>
</html>
```

Run 다시 → 2개 통과.

- [x] **Step 3: 수동 확인 후 커밋**

`kbadmin`으로 로그인해 비밀번호를 바꾸고 로그아웃 후 새 비밀번호로 로그인되는지 확인한다. 확인 후 시드 비밀번호로 되돌린다:

```bash
docker exec fido-admin-oracle sqlplus -s kbfido/kbfido@localhost/FREEPDB1 <<'SQL'
UPDATE CCFA_MANAGER SET USER_PW='c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461' WHERE USER_ID='kbadmin';
COMMIT;
SQL
```

```bash
git add src/
git commit -m "feat: 내 비밀번호 변경 화면

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 13: Testcontainers 통합 테스트 (채번, 예약어, 복합키, CLOB)

**Files:**
- Create: `src/test/java/com/crosscert/fidoadmin/integration/OracleContainerSupport.java`, `RepositoryIntegrationTest.java`
- Create: `src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: `docker/init/01-schema.sql`, `02-seed.sql` (컨테이너 초기화에 재사용), 리포지토리들.
- Produces: `./gradlew test`에서 Docker 가 있으면 실행되고 없으면 건너뛰는 통합 테스트.

- [x] **Step 1: 테스트 프로파일과 컨테이너 지원 클래스**

`src/test/resources/application-test.yml`

```yaml
spring:
  jpa:
    show-sql: false
```

```java
package com.crosscert.fidoadmin.integration;

import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

/** docker/init 의 스키마·시드를 그대로 넣은 Oracle Free 컨테이너. Docker 가 없으면 테스트를 건너뛴다. */
@Testcontainers(disabledWithoutDocker = true)
public abstract class OracleContainerSupport {

    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim")
        .withUsername("kbfido")
        .withPassword("kbfido")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/init/01-schema.sql")), "/container-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/init/02-seed.sql")), "/container-entrypoint-initdb.d/02-seed.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", ORACLE::getJdbcUrl);
        r.add("spring.datasource.username", ORACLE::getUsername);
        r.add("spring.datasource.password", ORACLE::getPassword);
    }
}
```

`OracleContainer`의 기본 DB 이름이 `FREEPDB1`이고 사용자 `kbfido`가 그 안에 만들어지므로 init 스크립트의 `ALTER SESSION SET CONTAINER = FREEPDB1; ... CURRENT_SCHEMA = KBFIDO;`가 그대로 맞는다.

- [x] **Step 2: 통합 테스트 작성**

```java
package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilter;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilterId;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest extends OracleContainerSupport {

    @Autowired EntityManager em;
    @Autowired CcfaCompanyRepository companies;
    @Autowired CcfaSystemPropRepository props;

    @Test void sequenceAssignsIdxOnInsert() {
        CcfaCompany c = new CcfaCompany();
        c.setCompanyName("통합테스트"); c.setEnableType("Y");
        c.setMaxAppid(0L); c.setMaxAppserver(0L); c.setMaxUser(0L);
        c.setCreatedtime(LocalDateTime.now()); c.setUpdatedtime(LocalDateTime.now());
        CcfaCompany saved = companies.saveAndFlush(c);
        assertThat(saved.getIdx()).isGreaterThanOrEqualTo(1000L); // 시퀀스는 1000 부터
    }

    @Test void reservedWordColumnsRoundTrip() {
        CcfaMailing m = new CcfaMailing();
        m.setCompanyIdx(1L); m.setTo("a@b.c"); m.setSubject("s"); m.setSendtime("2026-09-16 10:00:00");
        em.persist(m); em.flush(); em.clear();
        assertThat(em.find(CcfaMailing.class, m.getIdx()).getTo()).isEqualTo("a@b.c");

        CcfaStatisticsFilterId id = new CcfaStatisticsFilterId();
        id.setStatisticsIdx(1L); id.setColumnName("COMPANY_IDX"); id.setOp("="); id.setValue("1");
        CcfaStatisticsFilter f = new CcfaStatisticsFilter(); f.setId(id); f.setType("F");
        em.persist(f); em.flush(); em.clear();
        assertThat(em.find(CcfaStatisticsFilter.class, id).getType()).isEqualTo("F");
    }

    @Test void compositeKeySystemPropRoundTrip() {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("INTEGRATION_KEY", 2L)); p.setPropValue("v"); p.setShareType("NO");
        p.setUpdatedtime(LocalDateTime.now());
        props.saveAndFlush(p);
        assertThat(props.findById(new CcfaSystemPropId("INTEGRATION_KEY", 2L))).isPresent();
        assertThat(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)).get().getPropValue()).isEqualTo("5");
    }

    @Test void clobRoundTrip() {
        String big = "x".repeat(10_000);
        FidoLogs l = new FidoLogs();
        l.setCompanyIdx(1L); l.setSerialcode("IT"); l.setServicename("kbstar"); l.setJsondata(big);
        l.setCreatedtime(LocalDateTime.now());
        em.persist(l); em.flush(); em.clear();
        assertThat(em.find(FidoLogs.class, l.getIdx()).getJsondata()).hasSize(10_000);
    }
}
```

- [x] **Step 3: 실행**

Run: `./gradlew test --tests 'com.crosscert.fidoadmin.integration.RepositoryIntegrationTest' --no-daemon`
Expected: Docker 실행 중이면 4개 통과(최초 실행은 이미지 초기화로 2~3분). Docker 가 없으면 `skipped`.

`ORA-01400`(NOT NULL 위반)이 나면 해당 엔티티의 테스트 데이터에 ERD의 NOT NULL 컬럼 값을 채운다.

- [x] **Step 4: 커밋**

```bash
git add src/test
git commit -m "test: Testcontainers Oracle 통합 테스트 (채번, 예약어, 복합키, CLOB)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 14: README와 1부 최종 검증

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md` (체크박스 갱신)

- [x] **Step 1: README 작성**

```markdown
# FIDO Admin

기존 KBFIDO Oracle 스키마를 **변경 없이** 사용하는 관리자 웹. Java 17, Spring Boot 3.5, Thymeleaf, Spring Data JPA.

## 실행

1. Docker Desktop 실행 후 로컬 Oracle 기동 (로컬 검증 전용):
   `docker compose -f docker/docker-compose.yml up -d` — `docker compose -f docker/docker-compose.yml ps` 에서 `healthy` 확인.
2. 애플리케이션: `./gradlew bootRun --args='--spring.profiles.active=local'`
3. `http://localhost:8080` — 계정 `superuser / Admin1234!` (SUPER), `kbadmin / Company1234!` (고객사 운영자)

운영 환경은 `local` 프로파일 대신 환경 변수 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 를 준다.

## 테스트

`./gradlew test` — Docker 가 있으면 Testcontainers Oracle 통합 테스트까지 실행되고, 없으면 건너뛴다.
`EntityBootTest` 는 로컬 Docker Oracle(`local` 프로파일)이 떠 있어야 통과한다.

## 시퀀스 이름 교체

시퀀스 이름은 임시로 `<TABLE>_SEQ` 다. 실제 이름은 운영 DB 에서 아래로 확인한다.

    SELECT table_name, column_name, data_default
      FROM user_tab_columns
     WHERE UPPER(data_default) LIKE '%NEXTVAL%'
     ORDER BY table_name;

교체 위치: 각 엔티티의 `@SequenceGenerator(sequenceName = ...)`, `docker/init/01-schema.sql`.
교체 전까지 운영 DB INSERT 는 보장하지 않는다.

## 규칙

- `docker/init/*.sql` 은 로컬 검증 전용. 운영 DB 에 적용하지 않는다.
- 엔티티 컬럼은 `docs/erd/kbfido-columns.txt` 와 1:1. `ErdConformanceTest` 가 검증한다.
- 외부 CSS/JS/폰트 호출 없음. 정적 자원은 WebJars 와 `src/main/resources/static`.

## 문서

- 설계: `docs/superpowers/specs/2026-09-16-fido-admin-design.md`
- 구현 계획 1부: `docs/superpowers/plans/2026-09-16-fido-admin-part1-foundation.md`
```

- [x] **Step 2: 전체 테스트와 수동 점검**

```bash
./gradlew clean test --no-daemon
```

Expected: 전부 통과(Docker 기동 상태). 그런 다음 `bootRun`으로 아래를 확인한다.

1. `/login` → `superuser` 로그인 → 대시보드 카드·차트 표시
2. 사이드바에서 `/companies` 목록·검색·등록·수정·삭제, `/logs/audit`에 방금 행위가 기록됨
3. `kbadmin` 로그인 → 사이드바에 시스템·고객사·운영자 메뉴 없음, `/companies` 직접 접근 시 403 페이지
4. `/me/password` 변경 후 재로그인
5. 브라우저 네트워크 탭에 외부 호스트 요청 없음
6. `/nothing` → 404 페이지

- [x] **Step 3: 커밋**

```bash
git add README.md docs/
git commit -m "docs: README와 1부 계획 진행 상태

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## 2부 예고

2부 계획(`2026-09-16-fido-admin-part2-screens.md`)은 1부 완료 후 작성한다. 1부의 `CrudController`/`CrudService`/`ReadOnlyController`와 `company/`, `log/audit` 참조 구현을 그대로 따라 나머지 26개 화면(FDS 정책, 라이선스, 운영자, 앱 ID, 앱 서버, 사용자(상태 변경), 챌린지, 서명, 거래 해시, 거래 확인, 인증기기 기준, FIDO2 3개, FIDO 로그, 예외 로그, 메일/SMS 큐, 시스템 8개)을 추가한다. 메뉴 항목은 `MenuRegistry.ALL`에 이미 들어 있으므로 화면이 없는 동안은 404 가 뜬다.
