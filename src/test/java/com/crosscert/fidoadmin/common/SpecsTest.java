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
    /** @EmbeddedId 안의 테넌트 키("id.companyIdx")도 해석돼야 한다. */
    @Test void eqResolvesDottedPath() {
        var spec = Specs.<com.crosscert.fidoadmin.company.entity.CcfaLicense>eq("id.companyIdx", 3L);
        assertThat(spec).isNotNull();
    }
}
