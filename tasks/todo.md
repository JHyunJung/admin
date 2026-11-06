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
- [ ] 2. entity / form / DTO 변환 (Lombok 의존 제거)
- [ ] 3. repository / service 변환
- [ ] 4. web(controller) / auth / config 변환
- [ ] 5. statistics batch / system / 나머지 유틸 변환
- [ ] 6. 테스트 변환
- [ ] 7. Lombok 제거, build.gradle → build.gradle.kts, src/main/java → src/main/kotlin 정리

## Review
