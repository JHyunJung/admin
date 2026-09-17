package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class SpecsTest {

    @Test void nullValuesProduceNullSpecs() {
        assertThat(Specs.<Object>eq("a", null)).isNull();
        assertThat(Specs.<Object>like("a", "  ")).isNull();
        assertThat(Specs.<Object>between("a", null, null)).isNull();
    }

    @Test void allIgnoresNullSpecsAndReturnsNullPredicateWhenEmpty() {
        Specification<Object> s = Specs.all(null, null);
        assertThat(s.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class))).isNull();
    }
    /**
     * @EmbeddedId 안의 테넌트 키("id.companyIdx")도 단계별로 해석돼야 한다.
     * Specification 이 null 이 아닌지만 보면 수정 전에도 통과하므로, 실제로 평가해
     * root.get("id").get("companyIdx") 순으로 내려갔는지 확인한다.
     */
    @Test void eqResolvesDottedPath() {
        var spec = Specs.<com.crosscert.fidoadmin.company.entity.CcfaLicense>eq("id.companyIdx", 3L);
        assertThat(CriteriaProbe.equalsValueFor(spec, "companyIdx")).isEqualTo(3L);
        assertThat(CriteriaProbe.pathStepsFor(spec)).containsExactly("id", "companyIdx");
    }
}
