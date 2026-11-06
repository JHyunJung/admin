package com.crosscert.fidoadmin.common

import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import java.time.LocalDateTime
import java.util.Locale
import java.util.regex.Pattern
import org.springframework.data.jpa.domain.Specification

/** null 안전 Specification 헬퍼. 값이 비면 null 을 돌려주고 all() 이 무시한다. */
object Specs {

    private val DOT: Pattern = Pattern.compile("\\.")

    @JvmStatic
    fun <E> eq(attr: String, value: Any?): Specification<E>? {
        if (value == null || (value is String && value.javaIsBlank())) return null
        return Specification { root, _, cb -> cb.equal(path<Any>(root, attr), value) }
    }

    @JvmStatic
    fun <E> like(attr: String, value: String?): Specification<E>? {
        if (value == null || value.javaIsBlank()) return null
        val pattern = "%" + value.javaTrim().lowercase(Locale.getDefault()) + "%"
        return Specification { root, _, cb -> cb.like(cb.lower(path(root, attr)), pattern) }
    }

    /**
     * "id.companyIdx" 처럼 점으로 구분된 경로를 단계별로 해석한다.
     * CCFA_SYSTEM_PROP·통계 테이블은 테넌트 키가 @EmbeddedId 안에 있어
     * root.get("id.companyIdx") 로는 해석되지 않는다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> path(root: Path<*>, attr: String): Path<T> {
        var p: Path<*> = root
        for (part in DOT.split(attr)) { // String.split("\\.") 와 같은 규칙(끝의 빈 조각은 버린다)
            p = p.get<Any>(part)
        }
        return p as Path<T>
    }

    @JvmStatic
    fun <E> between(attr: String, from: LocalDateTime?, toExclusive: LocalDateTime?): Specification<E>? {
        if (from == null && toExclusive == null) return null
        return Specification { root, _, cb ->
            val ps = ArrayList<Predicate>()
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get(attr), from))
            if (toExclusive != null) ps.add(cb.lessThan(root.get(attr), toExclusive))
            cb.and(*ps.toTypedArray())
        }
    }

    @SafeVarargs
    @JvmStatic
    fun <E> all(vararg specs: Specification<E>?): Specification<E> {
        return Specification { root, q, cb ->
            val ps = ArrayList<Predicate>()
            for (s in specs) {
                if (s == null) continue
                val p = s.toPredicate(root, q, cb)
                if (p != null) ps.add(p)
            }
            if (ps.isEmpty()) null else cb.and(*ps.toTypedArray())
        }
    }
}
