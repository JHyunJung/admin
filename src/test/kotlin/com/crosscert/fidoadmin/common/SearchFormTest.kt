package com.crosscert.fidoadmin.common

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

class SearchFormTest {

    class Sub : SearchForm() {
        var keyword: String? = "홍 길동"
        override fun extraParams(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); m["keyword"] = keyword; return m
        }
    }

    /** companyIdx 필드는 없다 — 테넌트는 세션이 정하며 검색 조건이 아니다. */
    @Test fun queryStringEncodesAndSkipsNullsAndPage() {
        val f = Sub()
        f.page = 3; f.size = 50; f.fromDate = LocalDate.of(2026, 9, 1)
        assertThat(f.toQueryString())
            .isEqualTo("&size=50&fromDate=2026-09-01&keyword=%ED%99%8D+%EA%B8%B8%EB%8F%99")
    }

    @Test fun pageableUsesDefaultSortWhenNoneGiven() {
        val f = SearchForm()
        val p: Pageable = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"))
        assertThat(p.pageNumber).isZero()
        assertThat(p.pageSize).isEqualTo(20)
        assertThat(p.sort.getOrderFor("idx")!!.direction).isEqualTo(Sort.Direction.DESC)
    }

    @Test fun pageableParsesSortParam() {
        val f = SearchForm()
        f.sort = "companyName,asc"
        assertThat(f.toPageable(Sort.unsorted(), setOf("companyName"))
                .sort.getOrderFor("companyName")!!.direction)
            .isEqualTo(Sort.Direction.ASC)
    }

    @Test fun sizeIsClamped() {
        val f = SearchForm()
        f.size = 5000
        assertThat(f.toPageable(Sort.unsorted()).pageSize).isEqualTo(200)
        f.size = 0
        assertThat(f.toPageable(Sort.unsorted()).pageSize).isEqualTo(20)
    }

    @Test fun dateRangeIsHalfOpen() {
        val f = SearchForm()
        f.fromDate = LocalDate.of(2026, 9, 1); f.toDate = LocalDate.of(2026, 9, 30)
        assertThat(f.fromDateTime()).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay())
        assertThat(f.toDateTimeExclusive()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay())
        assertThat(SearchForm().fromDateTime()).isNull()
    }
    /**
     * 문법은 맞지만 매핑되지 않은 속성(doesNotExist)은 조회 시 예외가 되어 500 이 된다.
     * 허용 목록에 없으면 기본 정렬로 되돌아가야 한다.
     */
    @Test fun unknownSortPropertyFallsBackToDefault() {
        val f = SearchForm()
        f.sort = "doesNotExist,asc"
        val pageable = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"), setOf("idx"))
        assertThat(pageable.sort).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun allowedSortPropertyIsApplied() {
        val f = SearchForm()
        f.sort = "companyName,asc"
        val pageable = f.toPageable(Sort.by(Sort.Direction.DESC, "idx"), setOf("idx", "companyName"))
        assertThat(pageable.sort).isEqualTo(Sort.by(Sort.Direction.ASC, "companyName"))
    }
}
