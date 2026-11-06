# Java → Kotlin 변환 규칙 (refactor/kotlin)

순수 리팩터링이다. URL, 화면, DB 쿼리, 로그 문구, 예외 메시지, 공개 API 이름은 바꾸지 않는다.
예시: `src/main/kotlin/com/crosscert/fidoadmin/fido/entity/Appid.kt`, `.../fido/web/AppidForm.kt`

## 파일 이동
- `git mv src/main/java/<pkg>/X.java src/main/kotlin/<pkg>/X.kt` 후 내용을 Kotlin 으로 다시 쓴다
  (테스트는 `src/test/java` → `src/test/kotlin`). 패키지 이름은 그대로.
- 주석(한국어 Javadoc 포함)은 KDoc 으로 그대로 옮긴다. 내용 삭제 금지.

## 엔티티 (JPA)
- 일반 `class` + 본문의 `var` 프로퍼티. **data class 금지**. 기본 생성자/open 은 kotlin-jpa·allOpen 플러그인이 처리.
- 필드 타입은 nullable + `= null` 기본값 (Java 필드와 같은 의미). `@Id` 는 `Long?` 등.
- `@Column` 등 JPA 애너테이션은 그대로 붙이면 필드로 간다.
- 프로퍼티 이름 = Java 필드 이름 (Spring Data 파생 쿼리·Specs 의 `root.get("name")` 이 이름에 의존).
- Lombok `@EqualsAndHashCode`/`@ToString` 이 있으면 같은 의미로 손으로 구현 (`@IdClass`/`@Embeddable` 키 등).

## 폼·DTO
- Bean Validation·커스텀 제약(`@ByteSize` 등)은 반드시 `@field:` 를 붙인다 (`@field:NotBlank`).
- 기본값 있는 Java 필드는 같은 기본값으로.
- Lombok `@Value`/불변 DTO 는 생성자 프로퍼티(`class X(val a: String, ...)`)로. Java 쪽 호출부가 `getA()` 를 쓰면 그대로 동작한다.
  Thymeleaf/Jackson 이 쓰는 클래스는 getter 이름이 같아야 한다.

## boolean
- Lombok `boolean enabled` → getter `isEnabled()`. Kotlin 에서 같은 JVM 이름을 내려면 `val isEnabled: Boolean`
  (getter `isEnabled()`). 단 JPA 엔티티 필드나 Spring Data 파생 쿼리에 쓰이는 boolean 은 필드 이름이 바뀌므로 주의하고
  필요하면 `@get:JvmName` 대신 원래 이름을 유지하는 방법을 고른다. 판단이 어려우면 보고서에 적는다.
- `Boolean`(래퍼)는 Lombok 이 `getX()` 를 만든다 → Kotlin `var x: Boolean?` 그대로.

## static / 상수
- Java 쪽(아직 변환 안 된 코드, 테스트, Thymeleaf `T(...)`)이 부르는 static 메서드는 `companion object` + `@JvmStatic`,
  상수는 `const val` 또는 `@JvmField`. 유틸 클래스는 `object` + `@JvmStatic` 로 (호출부 `X.foo()` 유지).
- private 생성자만 있는 유틸 클래스 → `object`.

## Lombok
- `@RequiredArgsConstructor` → 주 생성자 `class S(private val repo: Repo)`.
- `@Slf4j` → `private val log = LoggerFactory.getLogger(X::class.java)` (companion 안이나 top-level private).
  로그 문구·레벨 그대로. 문자열 템플릿 대신 `{}` 플레이스홀더 유지.
- 전환 기간에는 Kotlin 이 아직 Java 인 Lombok 클래스의 getter 를 볼 수 있다(kotlin lombok 플러그인).

## null 처리
- Java 에서 null 이 될 수 있는 값은 nullable 로. `!!` 는 원래 Java 가 NPE 를 낼 자리에서만, 가급적 피한다.
- Spring 이 주입하는 빈, 생성자 인자 등 non-null 이 확실한 것은 non-null 타입.
- 오버라이드하는 Spring/Java 인터페이스 시그니처는 원래 nullability 에 맞춘다.

## 스타일
- Kotlin 관용구는 의미가 같을 때만 (`?.let`, `when`, `apply`, 컬렉션 함수). 동작이 바뀔 수 있는 단순화 금지.
- 체크 예외 `throws` 는 Java 호출부가 catch 하면 `@Throws` 유지.
- `@Transactional`, `@Cacheable` 등 프록시 대상은 kotlin-spring 플러그인이 open 처리하므로 그대로.
- `Optional` 반환 API 는 바꾸지 않는다(호출부 영향). 리포지토리 시그니처도 그대로.

## 테스트 변환
- JUnit5 + Mockito 유지. Kotlin 에서 `any()`/`eq()` 는 `org.mockito.kotlin` 의 것을 쓴다 (null 문제 회피).
- 테스트 이름은 백틱 함수 이름 대신 원래 메서드 이름 + `@DisplayName` 그대로.
- assert 문, 기대값을 바꾸지 않는다. 테스트를 지우거나 `@Disabled` 하지 않는다.

## 검증
- 다른 패키지 담당과 동시에 작업한다. 남의 패키지 파일은 수정하지 않는다
  (컴파일 때문에 꼭 필요하면 최소 수정하고 보고서에 적는다).
- `./gradlew compileKotlin compileJava compileTestKotlin compileTestJava -q` 통과,
  담당 패키지 테스트 `./gradlew test --tests 'com.crosscert.fidoadmin.<pkg>.*'` 통과.
- Testcontainers 통합 테스트(`integration` 패키지)는 돌리지 않는다(동시 실행 부담). 최종 통합 때 한 번에 돌린다.
