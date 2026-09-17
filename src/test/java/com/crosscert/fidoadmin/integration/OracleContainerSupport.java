package com.crosscert.fidoadmin.integration;

import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

/**
 * docker/init 의 스키마·시드를 그대로 넣은 Oracle Free 컨테이너. Docker 가 없으면 테스트를 건너뛴다.
 *
 * <p><b>컨테이너를 클래스마다 내리지 않는다.</b> 이 필드는 모든 하위 클래스가 공유하는데,
 * {@code @Container} 를 달면 JUnit 이 <em>각 클래스가 끝날 때마다</em> 컨테이너를 중지한다.
 * 그러면 먼저 끝난 클래스가 아직 실행 중인 다른 클래스의 DB 를 내려 버려
 * {@code ORA-12541 리스너 없음} 으로 무더기 실패한다(Oracle 백엔드 테스트 클래스가
 * 둘 이상일 때만 드러나는 문제다).
 *
 * <p>대신 직접 한 번만 기동하고 내리지 않는다. JVM 이 끝나면 Ryuk 이 정리한다.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class OracleContainerSupport {

    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim")
        .withUsername("kbfido")
        .withPassword("kbfido")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/init/01-schema.sql")), "/container-entrypoint-initdb.d/01-schema.sql")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/init/02-seed.sql")), "/container-entrypoint-initdb.d/02-seed.sql");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            ORACLE.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", ORACLE::getJdbcUrl);
        r.add("spring.datasource.username", ORACLE::getUsername);
        r.add("spring.datasource.password", ORACLE::getPassword);
    }
}
