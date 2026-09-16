package com.crosscert.fidoadmin.common;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** null 안전 Specification 헬퍼. 값이 비면 null 을 돌려주고 all() 이 무시한다. */
public final class Specs {

    private Specs() {}

    public static <E> Specification<E> eq(String attr, Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) return null;
        return (root, q, cb) -> cb.equal(path(root, attr), value);
    }

    public static <E> Specification<E> like(String attr, String value) {
        if (value == null || value.isBlank()) return null;
        String pattern = "%" + value.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(path(root, attr)), pattern);
    }

    /**
     * "id.companyIdx" 처럼 점으로 구분된 경로를 단계별로 해석한다.
     * CCFA_SYSTEM_PROP·통계 테이블은 테넌트 키가 @EmbeddedId 안에 있어
     * root.get("id.companyIdx") 로는 해석되지 않는다.
     */
    @SuppressWarnings("unchecked")
    private static <T> jakarta.persistence.criteria.Path<T> path(
            jakarta.persistence.criteria.Path<?> root, String attr) {
        jakarta.persistence.criteria.Path<?> p = root;
        for (String part : attr.split("\\.")) {
            p = p.get(part);
        }
        return (jakarta.persistence.criteria.Path<T>) p;
    }

    public static <E> Specification<E> between(String attr, LocalDateTime from, LocalDateTime toExclusive) {
        if (from == null && toExclusive == null) return null;
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get(attr), from));
            if (toExclusive != null) ps.add(cb.lessThan(root.get(attr), toExclusive));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    @SafeVarargs
    public static <E> Specification<E> all(Specification<E>... specs) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            for (Specification<E> s : specs) {
                if (s == null) continue;
                Predicate p = s.toPredicate(root, q, cb);
                if (p != null) ps.add(p);
            }
            return ps.isEmpty() ? null : cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
