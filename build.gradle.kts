plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
}

group = "com.crosscert"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// JPA 지연 로딩 프록시가 엔티티를 상속할 수 있도록 연다.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.github.ben-manes.caffeine:caffeine") // 목록 건수·FIDO 로그 검색 결과 단기 캐시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("org.webjars:webjars-locator-lite")
    implementation("org.webjars:bootstrap:5.3.7")
    implementation("org.webjars.npm:chart.js:4.5.0")
    implementation("org.webjars.npm:bootstrap-icons:1.13.1")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    // ojdbc 단독으로는 AL32UTF8 외 문자셋(KO16MSWIN949 등)을 해석하지 못해
    // 한글이 물음표로 읽힌다. orai18n 이 있어야 변환 테이블이 붙는다.
    runtimeOnly("com.oracle.database.nls:orai18n:23.7.0.25.01")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:oracle-free")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// DB 접속 정보 암호문 만들기: DB_CONFIG_KEY=<Base64 32바이트> DB_CONFIG_VALUE=<평문> ./gradlew -q encryptDbConfig
tasks.register<JavaExec>("encryptDbConfig") {
    group = "application"
    description = "DB_CONFIG_VALUE 를 DB_CONFIG_KEY 로 암호화해 ENC(...) 로 출력한다."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.crosscert.fidoadmin.DbConfigEncryptMain"
}
