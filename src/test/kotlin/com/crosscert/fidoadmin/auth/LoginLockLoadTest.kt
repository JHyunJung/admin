package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/** PESSIMISTIC_WRITE 가 실제 Oracle 에서 FOR UPDATE 로 나가는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp") // 로컬 도커 Oracle 이 없으면 건너뛴다
class LoginLockLoadTest {

    @Autowired lateinit var managers: CcfaManagerRepository
    @Autowired lateinit var em: EntityManager

    @Test
    @Transactional
    fun forUpdateLockIsAcquired() {
        assertThat(managers.findByUserIdForUpdate("kbadmin")).isPresent()
        // 잠금 모드가 실제로 적용됐는지 확인
        val m = managers.findByUserIdForUpdate("kbadmin").orElseThrow()
        assertThat(em.getLockMode(m)).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }
}
