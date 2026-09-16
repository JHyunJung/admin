package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilter;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilterId;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest extends OracleContainerSupport {

    @Autowired EntityManager em;
    @Autowired CcfaCompanyRepository companies;
    @Autowired CcfaSystemPropRepository props;

    @Test void sequenceAssignsIdxOnInsert() {
        CcfaCompany c = new CcfaCompany();
        c.setCompanyName("통합테스트"); c.setEnableType("Y");
        c.setMaxAppid(0L); c.setMaxAppserver(0L); c.setMaxUser(0L);
        c.setCreatedtime(LocalDateTime.now()); c.setUpdatedtime(LocalDateTime.now());
        CcfaCompany saved = companies.saveAndFlush(c);
        assertThat(saved.getIdx()).isGreaterThanOrEqualTo(1000L); // 시퀀스는 1000 부터
    }

    @Test void reservedWordColumnsRoundTrip() {
        CcfaMailing m = new CcfaMailing();
        m.setCompanyIdx(1L); m.setTo("a@b.c"); m.setSubject("s"); m.setSendtime("2026-09-16 10:00:00");
        em.persist(m); em.flush(); em.clear();
        assertThat(em.find(CcfaMailing.class, m.getIdx()).getTo()).isEqualTo("a@b.c");

        CcfaStatisticsFilterId id = new CcfaStatisticsFilterId();
        id.setStatisticsIdx(1L); id.setColumnName("COMPANY_IDX"); id.setOp("="); id.setValue("1");
        CcfaStatisticsFilter f = new CcfaStatisticsFilter(); f.setId(id); f.setType("F");
        em.persist(f); em.flush(); em.clear();
        assertThat(em.find(CcfaStatisticsFilter.class, id).getType()).isEqualTo("F");
    }

    @Test void compositeKeySystemPropRoundTrip() {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("INTEGRATION_KEY", 2L)); p.setPropValue("v"); p.setShareType("NO");
        p.setUpdatedtime(LocalDateTime.now());
        props.saveAndFlush(p);
        assertThat(props.findById(new CcfaSystemPropId("INTEGRATION_KEY", 2L))).isPresent();
        assertThat(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)).get().getPropValue()).isEqualTo("5");
    }

    @Test void clobRoundTrip() {
        String big = "x".repeat(10_000);
        FidoLogs l = new FidoLogs();
        l.setCompanyIdx(1L); l.setSerialcode("IT"); l.setServicename("kbstar"); l.setJsondata(big);
        l.setCreatedtime(LocalDateTime.now());
        em.persist(l); em.flush(); em.clear();
        assertThat(em.find(FidoLogs.class, l.getIdx()).getJsondata()).hasSize(10_000);
    }
}
