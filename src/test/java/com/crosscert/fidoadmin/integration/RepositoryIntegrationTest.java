package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.statistics.entity.CcfaStatistics;
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
        assertThat(c.getIdx()).isNull();

        CcfaCompany saved = companies.saveAndFlush(c);

        // 모든 시퀀스가 1000 에서 시작하므로 ">= 1000" 만으로는 다른 시퀀스를 써도 통과한다.
        // 실제로 CCFA_COMPANY_SEQ 가 쓰였는지 CURRVAL 로 확인한다.
        Long currval = ((Number) em.createNativeQuery("SELECT CCFA_COMPANY_SEQ.CURRVAL FROM DUAL")
            .getSingleResult()).longValue();
        assertThat(saved.getIdx()).isEqualTo(currval);
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

        // "LIMIT" 은 어떤 테스트도 건드리지 않아, 매핑이 깨져도 통과했다.
        CcfaStatistics st = new CcfaStatistics();
        st.setOwnerIdx(1L); st.setTitle("통합"); st.setLimit(42L);
        st.setOpenType("close"); st.setRealtime("custom");   // ERD NOT NULL
        st.setCreatedtime(LocalDateTime.now()); st.setUpdatedtime(LocalDateTime.now());
        em.persist(st); em.flush(); em.clear();
        assertThat(em.find(CcfaStatistics.class, st.getIdx()).getLimit()).isEqualTo(42L);
    }

    @Test void compositeKeySystemPropRoundTrip() {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("INTEGRATION_KEY", 2L)); p.setPropValue("v"); p.setShareType("NO");
        p.setUpdatedtime(LocalDateTime.now());
        props.saveAndFlush(p);

        // 같은 PROP_KEY 에 COMPANY_IDX 만 다른 행을 하나 더 넣어, 키 두 요소가 모두
        // 행을 구분하는지 확인한다. 영속성 컨텍스트 캐시로 통과하지 않도록 clear 한다.
        CcfaSystemProp other = new CcfaSystemProp();
        other.setId(new CcfaSystemPropId("INTEGRATION_KEY", 3L)); other.setPropValue("other");
        other.setShareType("NO"); other.setUpdatedtime(LocalDateTime.now());
        props.saveAndFlush(other);
        em.clear();

        assertThat(props.findById(new CcfaSystemPropId("INTEGRATION_KEY", 2L)).get().getPropValue()).isEqualTo("v");
        assertThat(props.findById(new CcfaSystemPropId("INTEGRATION_KEY", 3L)).get().getPropValue()).isEqualTo("other");
        assertThat(props.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L)).get().getPropValue()).isEqualTo("5");
    }

    @Test void clobRoundTrip() {
        String big = "x".repeat(10_000);
        FidoLogs l = new FidoLogs();
        l.setCompanyIdx(1L); l.setSerialcode("IT"); l.setServicename("kbstar"); l.setJsondata(big);
        l.setCreatedtime(LocalDateTime.now());
        em.persist(l); em.flush(); em.clear();
        assertThat(em.find(FidoLogs.class, l.getIdx()).getJsondata()).isEqualTo(big); // 길이만 보면 내용 손상을 놓친다
    }
}
