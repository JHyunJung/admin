# Kotlin 전환 (브랜치 refactor/kotlin)

목표: 외부 동작(URL·화면·DB 쿼리) 그대로, Java 283 + 테스트 141 파일을 Kotlin 으로.
기준선: `./gradlew test` 933 tests, 0 fail, 7 skip (2026-11-06, main 1c9f635).

## 원칙
- 단계마다 `./gradlew test` 전체 통과 후 커밋. 중간에 멈춰도 빌드 가능 상태 유지.
- 엔티티는 `data class` 금지. kotlin-jpa / allOpen 으로 프록시·기본 생성자 처리.
- 전환 기간에는 `kotlin("plugin.lombok")` 로 Kotlin 이 Lombok 생성 멤버를 보게 한다.
- `!!` 남발 금지. 컬럼 nullability 는 스키마 기준으로 판단.
- Mockito 유지(mockito-kotlin), MockK 전환은 범위 밖.

## 진행 방식 (변경)
kotlin lombok 플러그인 덕분에 계층 순서가 필요 없어져, 패키지 그룹별 병렬 변환(worktree 6개) 후 통합한다.
- A: root·common·config·audit (+schema 테스트) / B: auth·signup·manager / C: fido
- D: fido2·company·aws·dashboard / E: log·statistics / F: system
- 통합 후: integration 테스트 변환, 전체 테스트, Lombok 제거·kts 전환

## 단계
- [x] 1. Gradle: Kotlin JVM/Spring/JPA/Lombok 플러그인, jackson-module-kotlin, 혼합 빌드 통과
- [x] 2. entity / form / DTO 변환 (Lombok 의존 제거)
- [x] 3. repository / service 변환
- [x] 4. web(controller) / auth / config 변환
- [x] 5. statistics batch / system / 나머지 유틸 변환
- [x] 6. 테스트 변환
- [x] 7. Lombok 제거, build.gradle → build.gradle.kts, src/main/java → src/main/kotlin 정리

## Review (2026-11-06)
- 결과: Java 0 / Kotlin 425 파일. `./gradlew clean build` 통과, 933 tests / 0 fail / 7 skip (기준선과 동일, integration 44개는 실제 Oracle 컨테이너에서 실행).
- 동작 비교: main(Java) jar 와 Kotlin jar 를 같은 로컬 DB 에 띄우고 SUPER·COMPANY 두 계정으로 40개 경로를 요청해
  응답 코드·HTML 본문(CSRF 토큰 제외)을 비교 → 차이는 관리자 목록의 '최근 로그인' 시각 1줄(로그인 시각 차이)뿐.
- SQL: Java 텍스트 블록과 Kotlin 문자열을 규칙대로 계산해 대조, 전부 공백까지 일치 (Oracle SQL_ID 유지).
- 의도된 차이
  - record → `@JvmRecord data class`: `toString` 형식 `X[a=1]` → `X(a=1)` (의존 코드 없음)
  - 같은 패키지 테스트가 부르던 protected 메서드 몇 개를 public/internal 로 (URL 매핑 없음)
  - `ManagerUserDetails` 직렬화 형태가 바뀜 → 배포 시 기존 세션은 다시 로그인 필요
  - 테스트 보강 2건: 이벤트 미발행 검증이 실제 `publishEvent(Object)` 를 보게 (system, fido)
- 함정 정리: `tasks/lessons.md`
