package com.crosscert.fidoadmin.common

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.regex.Pattern
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat

/** 목록 화면 공통 검색 조건. 하위 클래스는 필드를 추가하고 extraParams() 에 넣는다. */
open class SearchForm {

    var page: Int = 0
    var size: Int = DEFAULT_SIZE
    /** "속성,asc|desc" */
    var sort: String? = null
    // companyIdx 삭제 — 테넌트는 세션이 정하며 검색 조건이 아니다
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) var fromDate: LocalDate? = null
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE) var toDate: LocalDate? = null

    open fun toPageable(defaultSort: Sort): Pageable = toPageable(defaultSort, setOf())

    /**
     * 정렬은 화면별 허용 목록으로만 받는다. 형태만 검사하면 `doesNotExist` 처럼
     * 문법은 맞지만 매핑되지 않은 속성이 조회 시 예외가 되어 500 이 된다.
     * 허용 목록에 없으면 조용히 기본 정렬로 되돌린다.
     */
    open fun toPageable(defaultSort: Sort, sortableProperties: Set<String>): Pageable {
        val s = if (size <= 0) DEFAULT_SIZE else Math.min(size, MAX_SIZE)
        var sortObj = defaultSort
        val sort = this.sort
        if (sort != null && !sort.javaIsBlank()) {
            val parts = sort.split(",", limit = 2)
            val property = parts[0].javaTrim()
            if (SORTABLE.matcher(property).matches() && sortableProperties.contains(property)) {
                val dir = if (parts.size > 1 && "asc".equals(parts[1].javaTrim(), ignoreCase = true))
                    Sort.Direction.ASC else Sort.Direction.DESC
                sortObj = Sort.by(dir, property)
            }
        }
        return PageRequest.of(Math.max(page, 0), s, sortObj)
    }

    open fun fromDateTime(): LocalDateTime? = fromDate?.atStartOfDay()
    open fun toDateTimeExclusive(): LocalDateTime? = toDate?.plusDays(1)?.atStartOfDay()

    /** page 를 제외한 "&k=v&k=v" 문자열. 페이지네이션 링크에 붙인다. */
    open fun toQueryString(): String {
        val params = LinkedHashMap<String, Any?>()
        params["size"] = size
        params["sort"] = sort
        params["fromDate"] = fromDate
        params["toDate"] = toDate
        params.putAll(extraParams())
        return join(params)
    }

    /**
     * 검색 조건만으로 만든 키(페이지·크기·정렬 제외). 목록 건수 캐시가 쓴다.
     * 하위 폼은 페이지 이동 링크가 조건을 이어 가도록 모든 조건을 [extraParams] 에 넣으므로,
     * 이 키가 같으면 조건이 같다.
     */
    open fun conditionKey(): String {
        val params = LinkedHashMap<String, Any?>()
        params["fromDate"] = fromDate
        params["toDate"] = toDate
        params.putAll(extraParams())
        return join(params)
    }

    protected open fun extraParams(): Map<String, Any?> = mapOf()

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 200

        /** 정렬 속성명 허용 형태(자바 식별자, 점 경로 허용). 그 밖은 기본 정렬로 되돌린다. */
        private val SORTABLE: Pattern =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63}){0,3}")

        private fun join(params: Map<String, Any?>): String {
            val sb = StringBuilder()
            params.forEach { (k, v) ->
                if (v == null || v.toString().javaIsBlank()) return@forEach
                sb.append('&').append(k).append('=').append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8))
            }
            return sb.toString()
        }
    }
}
