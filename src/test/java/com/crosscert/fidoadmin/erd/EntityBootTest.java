package com.crosscert.fidoadmin.erd;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatistics;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/** 로컬 Docker Oracle(application-local.yml)에 대해 매핑이 실제 컬럼과 맞는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class EntityBootTest {

    @Autowired EntityManager em;

    @Test
    void reservedWordColumnsAreReadable() {
        CcfaCompany global = em.find(CcfaCompany.class, 0L);
        assertThat(global).isNotNull();

        CcfaMailing mail = em.find(CcfaMailing.class, 1L);
        assertThat(mail.getTo()).isEqualTo("ops@kb.local");

        CcfaStatistics st = em.find(CcfaStatistics.class, 1L);
        assertThat(st.getLimit()).isEqualTo(30L);

        long filters = em.createQuery("select count(f) from CcfaStatisticsFilter f", Long.class).getSingleResult();
        assertThat(filters).isEqualTo(1L);
    }
}
