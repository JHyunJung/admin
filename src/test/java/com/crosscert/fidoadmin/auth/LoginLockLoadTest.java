package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** PESSIMISTIC_WRITE 가 실제 Oracle 에서 FOR UPDATE 로 나가는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class LoginLockLoadTest {

    @Autowired CcfaManagerRepository managers;
    @Autowired EntityManager em;

    @Test
    @Transactional
    void forUpdateLockIsAcquired() {
        assertThat(managers.findByUserIdForUpdate("kbadmin")).isPresent();
        // 잠금 모드가 실제로 적용됐는지 확인
        var m = managers.findByUserIdForUpdate("kbadmin").orElseThrow();
        assertThat(em.getLockMode(m)).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
