package com.crosscert.fidoadmin.schema

import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.statistics.entity.CcfaStatistics
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

/** 로컬 Docker Oracle(application-local.yml)에 대해 매핑이 실제 컬럼과 맞는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp") // 로컬 도커 Oracle 이 없으면 건너뛴다
class EntityBootTest {

    @Autowired lateinit var em: EntityManager

    @Test
    fun reservedWordColumnsAreReadable() {
        val global = em.find(CcfaCompany::class.java, 0L)
        assertThat(global).isNotNull()

        val mail = em.find(CcfaMailing::class.java, 1L)
        assertThat(mail.to).isEqualTo("ops@kb.local")

        val st = em.find(CcfaStatistics::class.java, 1L)
        assertThat(st.limit).isEqualTo(30L)

        val filters = em.createQuery("select count(f) from CcfaStatisticsFilter f", Long::class.javaObjectType).singleResult
        assertThat(filters).isEqualTo(1L)
    }
}
