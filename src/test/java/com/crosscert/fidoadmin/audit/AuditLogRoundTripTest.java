package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 감사 로그가 실제 Oracle 에 저장된 뒤 다시 읽어도 INTERGRITY_HASH 가 재계산되는지 확인한다.
 * 한글(멀티바이트) 값은 BYTE 의미 컬럼에서 ORA-12899 를 냈던 회귀 지점이므로 함께 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class AuditLogRoundTripTest {

    @Autowired EntityManager em;

    @Test
    void storedRowReproducesItsIntegrityHash() {
        ManagerUserDetails actor = new ManagerUserDetails(
            1L, "kbadmin", null, "가".repeat(200), 1L, "국민은행".repeat(200), true, true);

        // AuditLogger 가 만든 행을 그대로 실제 DB 에 넣는다.
        AuditLogWriter writer = mock(AuditLogWriter.class);
        doAnswer(inv -> {
            em.persist(inv.getArgument(0, CcfaAuditLog.class));
            return null;
        }).when(writer).write(any());

        new AuditLogger(writer, new TenantContext(new SelectedTenant()), mock(CompanyLookup.class)).log(actor, AuditType.UPDATE,
            "한글 메시지 ".repeat(500), "10.0.0.5", "브라우저 ".repeat(500));

        em.flush();
        em.clear();

        CcfaAuditLog reloaded = em.createQuery(
                "select a from CcfaAuditLog a where a.userId = :u order by a.idx desc", CcfaAuditLog.class)
            .setParameter("u", "kbadmin")
            .setMaxResults(1)
            .getSingleResult();

        // 저장 후 읽은 행으로 재계산한 해시가 저장된 해시와 같아야 한다.
        assertThat(AuditLogger.integrityHash(reloaded)).isEqualTo(reloaded.getIntergrityHash());
    }
}
