package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

/**
 * 감사 로그가 실제 Oracle 에 저장된 뒤 다시 읽어도 INTERGRITY_HASH 가 재계산되는지 확인한다.
 * 한글(멀티바이트) 값은 BYTE 의미 컬럼에서 ORA-12899 를 냈던 회귀 지점이므로 함께 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp") // 로컬 도커 Oracle 이 없으면 건너뛴다
class AuditLogRoundTripTest {

    @Autowired lateinit var em: EntityManager

    @Test
    fun storedRowReproducesItsIntegrityHash() {
        val actor = ManagerUserDetails(
            1L, "kbadmin", null, "가".repeat(200), 1L, "국민은행".repeat(200), true, true)

        // AuditLogger 가 만든 행을 그대로 실제 DB 에 넣는다.
        val writer = mock(AuditLogWriter::class.java)
        doAnswer { inv ->
            em.persist(inv.getArgument(0, CcfaAuditLog::class.java))
            null
        }.`when`(writer).write(any())

        AuditLogger(writer, TenantContext(SelectedTenant()), mock(CompanyLookup::class.java)).log(actor, AuditType.UPDATE,
            "한글 메시지 ".repeat(500), "10.0.0.5", "브라우저 ".repeat(500))

        em.flush()
        em.clear()

        val reloaded = em.createQuery(
                "select a from CcfaAuditLog a where a.userId = :u order by a.idx desc", CcfaAuditLog::class.java)
            .setParameter("u", "kbadmin")
            .setMaxResults(1)
            .singleResult

        // 저장 후 읽은 행으로 재계산한 해시가 저장된 해시와 같아야 한다.
        assertThat(AuditLogger.integrityHash(reloaded)).isEqualTo(reloaded.intergrityHash)
    }
}
