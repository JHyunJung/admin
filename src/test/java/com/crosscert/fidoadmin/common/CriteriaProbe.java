package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification 을 기록용 Criteria API 위에서 평가해 `속성 = 값` 조건을 뽑아낸다.
 *
 * 목록 조회의 테넌트 필터는 격리의 핵심인데, Specification 이 null 이 아닌지만 검사하면
 * 필터를 통째로 제거해도 테스트가 통과한다. 실제로 어떤 조건이 만들어졌는지 확인하기 위한 도구다.
 */
final class CriteriaProbe {

    private CriteriaProbe() {}

    /** spec 을 평가하며 root 에서 내려간 경로 조각을 순서대로 돌려준다. */
    static List<String> pathStepsFor(Specification<CcfaLicense> spec) {
        List<String> steps = new ArrayList<>();
        if (spec == null) return steps;

        Predicate dummy = proxy(Predicate.class, (m, args) -> m.getName().equals("getExpressions") ? List.of() : null);
        CriteriaBuilder cb = proxy(CriteriaBuilder.class, (m, args) -> dummy);
        CriteriaQuery<?> query = proxy(CriteriaQuery.class, (m, args) -> null);
        Root<?> root = proxy(Root.class, (m, args) -> {
            if (m.getName().equals("get") && args != null && args.length == 1) {
                steps.add(String.valueOf(args[0]));
                return recordingPath(steps);
            }
            return null;
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        Predicate ignored = spec.toPredicate((Root) root, (CriteriaQuery) query, cb);
        return steps;
    }

    private static Path<?> recordingPath(List<String> steps) {
        return proxy(Path.class, (m, args) -> {
            if (m.getName().equals("get") && args != null && args.length == 1) {
                steps.add(String.valueOf(args[0]));
                return recordingPath(steps);
            }
            return null;
        });
    }

    /** spec 이 만든 조건 중 attr 에 걸린 등치 값을 돌려준다. 없으면 null. */
    static Long equalsValueFor(Specification<CcfaLicense> spec, String attr) {
        if (spec == null) return null;

        List<String> pathNames = new ArrayList<>();
        List<Object[]> equals = new ArrayList<>();

        Path<?> path = proxy(Path.class, (m, args) -> {
            if (m.getName().equals("get") && args != null && args.length == 1) {
                pathNames.add(String.valueOf(args[0]));
                return proxyPathNamed(String.valueOf(args[0]), pathNames);
            }
            return null;
        });

        Root<?> root = proxy(Root.class, (m, args) -> {
            if (m.getName().equals("get") && args != null && args.length == 1) {
                return proxyPathNamed(String.valueOf(args[0]), pathNames);
            }
            return path;
        });

        Predicate dummy = proxy(Predicate.class, (m, args) -> {
            if (m.getName().equals("getExpressions")) return List.of();
            return null;
        });

        CriteriaBuilder cb = proxy(CriteriaBuilder.class, (m, args) -> {
            if (m.getName().equals("equal") && args != null && args.length == 2) {
                equals.add(new Object[] {nameOf(args[0]), args[1]});
            }
            return dummy;
        });

        CriteriaQuery<?> query = proxy(CriteriaQuery.class, (m, args) -> null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        Predicate ignored = spec.toPredicate((Root) root, (CriteriaQuery) query, cb);

        for (Object[] pair : equals) {
            if (attr.equals(pair[0]) && pair[1] instanceof Long l) return l;
        }
        return null;
    }

    private static final ThreadLocal<String> LAST_NAME = new ThreadLocal<>();

    private static Path<?> proxyPathNamed(String name, List<String> names) {
        return proxy(Path.class, (m, args) -> {
            if (m.getName().equals("toString")) return name;
            if (m.getName().equals("get") && args != null && args.length == 1) {
                return proxyPathNamed(String.valueOf(args[0]), names);
            }
            return null;
        });
    }

    private static String nameOf(Object pathOrValue) {
        return pathOrValue == null ? null : pathOrValue.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(
            CriteriaProbe.class.getClassLoader(), new Class<?>[] {type},
            (p, method, args) -> {
                if (method.getName().equals("toString") && (args == null || args.length == 0)) {
                    Object r = handler.handle(method, args);
                    return r != null ? r : "proxy";
                }
                if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                if (method.getName().equals("equals")) return p == args[0];
                return handler.handle(method, args);
            });
    }

    private interface Handler {
        Object handle(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
