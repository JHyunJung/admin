package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SearchFormTest {

    static class Sub extends SearchForm {
        String keyword = "홍 길동";
        @Override protected Map<String, Object> extraParams() {
            Map<String, Object> m = new LinkedHashMap<>(); m.put("keyword", keyword); return m;
        }
    }

    @Test void queryStringEncodesAndSkipsNullsAndPage() {
        Sub f = new Sub();
        f.setPage(3); f.setSize(50); f.setCompanyIdx(1L); f.setFromDate(LocalDate.of(2026, 9, 1));
        assertThat(f.toQueryString())
            .isEqualTo("&size=50&companyIdx=1&fromDate=2026-09-01&keyword=%ED%99%8D+%EA%B8%B8%EB%8F%99");
    }

    @Test void pageableUsesDefaultSortWhenNoneGiven() {
        SearchForm f = new SearchForm();
        Pageable p = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort().getOrderFor("idx").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test void pageableParsesSortParam() {
        SearchForm f = new SearchForm();
        f.setSort("companyName,asc");
        assertThat(f.toPageable(Sort.unsorted(), java.util.Set.of("companyName"))
                .getSort().getOrderFor("companyName").getDirection())
            .isEqualTo(Sort.Direction.ASC);
    }

    @Test void sizeIsClamped() {
        SearchForm f = new SearchForm();
        f.setSize(5000);
        assertThat(f.toPageable(Sort.unsorted()).getPageSize()).isEqualTo(200);
        f.setSize(0);
        assertThat(f.toPageable(Sort.unsorted()).getPageSize()).isEqualTo(20);
    }

    @Test void dateRangeIsHalfOpen() {
        SearchForm f = new SearchForm();
        f.setFromDate(LocalDate.of(2026, 9, 1)); f.setToDate(LocalDate.of(2026, 9, 30));
        assertThat(f.fromDateTime()).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay());
        assertThat(f.toDateTimeExclusive()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
        assertThat(new SearchForm().fromDateTime()).isNull();
    }
    /**
     * 문법은 맞지만 매핑되지 않은 속성(doesNotExist)은 조회 시 예외가 되어 500 이 된다.
     * 허용 목록에 없으면 기본 정렬로 되돌아가야 한다.
     */
    @Test void unknownSortPropertyFallsBackToDefault() {
        SearchForm f = new SearchForm();
        f.setSort("doesNotExist,asc");
        var pageable = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"), java.util.Set.of("idx"));
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void allowedSortPropertyIsApplied() {
        SearchForm f = new SearchForm();
        f.setSort("companyName,asc");
        var pageable = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"), java.util.Set.of("idx", "companyName"));
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "companyName"));
    }
}
