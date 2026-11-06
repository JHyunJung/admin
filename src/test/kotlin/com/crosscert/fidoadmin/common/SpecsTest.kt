package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.company.entity.CcfaLicense
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.jpa.domain.Specification

class SpecsTest {

    @Test fun nullValuesProduceNullSpecs() {
        assertThat(Specs.eq<Any>("a", null)).isNull()
        assertThat(Specs.like<Any>("a", "  ")).isNull()
        assertThat(Specs.between<Any>("a", null, null)).isNull()
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun allIgnoresNullSpecsAndReturnsNullPredicateWhenEmpty() {
        val s: Specification<Any> = Specs.all(null, null)
        assertThat(s.toPredicate(mock(Root::class.java) as Root<Any>, mock(CriteriaQuery::class.java), mock(CriteriaBuilder::class.java))).isNull()
    }
    /**
     * @EmbeddedId 안의 테넌트 키("id.companyIdx")도 단계별로 해석돼야 한다.
     * Specification 이 null 이 아닌지만 보면 수정 전에도 통과하므로, 실제로 평가해
     * root.get("id").get("companyIdx") 순으로 내려갔는지 확인한다.
     */
    @Test fun eqResolvesDottedPath() {
        val spec = Specs.eq<CcfaLicense>("id.companyIdx", 3L)
        assertThat(CriteriaProbe.equalsValueFor(spec, "companyIdx")).isEqualTo(3L)
        assertThat(CriteriaProbe.pathStepsFor(spec)).containsExactly("id", "companyIdx")
    }
}
