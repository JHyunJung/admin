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
