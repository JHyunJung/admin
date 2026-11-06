package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.log.entity.FidoLogs
import com.crosscert.fidoadmin.statistics.entity.CcfaStatistics
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilter
import com.crosscert.fidoadmin.statistics.entity.CcfaStatisticsFilterId
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var companies: CcfaCompanyRepository
    @Autowired lateinit var props: CcfaSystemPropRepository

    @Test
    fun sequenceAssignsIdxOnInsert() {
        val c = CcfaCompany()
        c.companyName = "통합테스트"; c.enableType = "Y"
        c.maxAppid = 0L; c.maxAppserver = 0L; c.maxUser = 0L
        c.createdtime = LocalDateTime.now(); c.updatedtime = LocalDateTime.now()
        assertThat(c.idx).isNull()

        val saved = companies.saveAndFlush(c)

        // 모든 시퀀스가 1000 에서 시작하므로 ">= 1000" 만으로는 다른 시퀀스를 써도 통과한다.
        // 실제로 CCFA_COMPANY_SEQ 가 쓰였는지 CURRVAL 로 확인한다.
        val currval: Long = (em.createNativeQuery("SELECT CCFA_COMPANY_SEQ.CURRVAL FROM DUAL")
            .singleResult as Number).toLong()
        assertThat(saved.idx).isEqualTo(currval)
    }

    @Test
    fun reservedWordColumnsRoundTrip() {
        val m = CcfaMailing()
        m.companyIdx = 1L; m.to = "a@b.c"; m.subject = "s"; m.sendtime = "2026-09-16 10:00:00"
        em.persist(m); em.flush(); em.clear()
        assertThat(em.find(CcfaMailing::class.java, m.idx).to).isEqualTo("a@b.c")

        val id = CcfaStatisticsFilterId()
        id.statisticsIdx = 1L; id.columnName = "COMPANY_IDX"; id.op = "="; id.value = "1"
        val f = CcfaStatisticsFilter(); f.id = id; f.type = "F"
        em.persist(f); em.flush(); em.clear()
        assertThat(em.find(CcfaStatisticsFilter::class.java, id).type).isEqualTo("F")

        // "LIMIT" 은 어떤 테스트도 건드리지 않아, 매핑이 깨져도 통과했다.
        val st = CcfaStatistics()
        st.ownerIdx = 1L; st.title = "통합"; st.limit = 42L
        st.openType = "close"; st.realtime = "custom"   // DB NOT NULL
        st.createdtime = LocalDateTime.now(); st.updatedtime = LocalDateTime.now()
        em.persist(st); em.flush(); em.clear()
        assertThat(em.find(CcfaStatistics::class.java, st.idx).limit).isEqualTo(42L)
    }

    @Test
    fun compositeKeySystemPropRoundTrip() {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId("INTEGRATION_KEY", 2L); p.propValue = "v"; p.shareType = "NO"
        p.updatedtime = LocalDateTime.now()
        props.saveAndFlush(p)

        // 같은 PROP_KEY 에 COMPANY_IDX 만 다른 행을 하나 더 넣어, 키 두 요소가 모두
        // 행을 구분하는지 확인한다. 영속성 컨텍스트 캐시로 통과하지 않도록 clear 한다.
        val other = CcfaSystemProp()
        other.id = CcfaSystemPropId("INTEGRATION_KEY", 3L); other.propValue = "other"
        other.shareType = "NO"; other.updatedtime = LocalDateTime.now()
        props.saveAndFlush(other)
        em.clear()

        assertThat(props.findById(CcfaSystemPropId("INTEGRATION_KEY", 2L)).get().propValue).isEqualTo("v")
        assertThat(props.findById(CcfaSystemPropId("INTEGRATION_KEY", 3L)).get().propValue).isEqualTo("other")
        assertThat(props.findById(CcfaSystemPropId("PW_FAIL_LIMIT", 0L)).get().propValue).isEqualTo("5")
    }

    @Test
    fun clobRoundTrip() {
        val big = "x".repeat(10_000)
        val l = FidoLogs()
        l.companyIdx = 1L; l.serialcode = "IT"; l.servicename = "kbstar"; l.jsondata = big
        l.createdtime = LocalDateTime.now()
        em.persist(l); em.flush(); em.clear()
        assertThat(em.find(FidoLogs::class.java, l.idx).jsondata).isEqualTo(big) // 길이만 보면 내용 손상을 놓친다
    }
}
