package com.crosscert.fidoadmin.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * {@link PartialFetch} 구현. 메타모델의 단일값 속성 가운데 제외 목록에 없는 것만 Tuple 로 SELECT 하고,
 * 기본 생성자로 만든 엔티티의 필드에 리플렉션으로 채운다(엔티티는 필드 접근이다).
 *
 * <p>클래스 이름이 {@code PartialFetchImpl} 인 것은 Spring Data 가 조각 구현을 "인터페이스 이름 + Impl" 로
 * 찾기 때문이다. 리포지터리 스캔이 찾으므로 {@code @DataJpaTest} 조각에서도 함께 올라온다.
 */
public class PartialFetchImpl<E> implements PartialFetch<E> {

    private final EntityManager em;

    public PartialFetchImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Page<E> findAllExcluding(Class<E> type, Specification<E> spec, Pageable pageable, Set<String> excluded) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<E> root = query.from(type);

        List<SingularAttribute<? super E, ?>> attributes = em.getMetamodel().entity(type).getSingularAttributes().stream()
            .filter(a -> !excluded.contains(a.getName()))
            .sorted(Comparator.comparing(SingularAttribute::getName))
            .toList();
        List<Selection<?>> selections = new ArrayList<>(attributes.size());
        for (SingularAttribute<? super E, ?> a : attributes) {
            selections.add(root.get(a.getName()));
        }
        query.multiselect(selections);

        Predicate where = spec == null ? null : spec.toPredicate(root, query, cb);
        if (where != null) query.where(where);
        if (pageable.getSort().isSorted()) query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));

        TypedQuery<Tuple> typed = em.createQuery(query);
        if (pageable.isPaged()) {
            typed.setFirstResult((int) pageable.getOffset());
            typed.setMaxResults(pageable.getPageSize());
        }
        List<E> content = typed.getResultList().stream().map(t -> materialize(type, attributes, t)).toList();
        return PageableExecutionUtils.getPage(content, pageable, () -> countMatching(type, spec));
    }

    private long countMatching(Class<E> type, Specification<E> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<E> root = query.from(type);
        query.select(cb.count(root));
        Predicate where = spec == null ? null : spec.toPredicate(root, query, cb);
        if (where != null) query.where(where);
        return em.createQuery(query).getSingleResult();
    }

    /** Tuple 의 값을 같은 순서의 속성 필드에 넣는다. 돌려주는 엔티티는 영속 상태가 아니다. */
    private E materialize(Class<E> type, List<SingularAttribute<? super E, ?>> attributes, Tuple tuple) {
        try {
            E entity = type.getDeclaredConstructor().newInstance();
            for (int i = 0; i < attributes.size(); i++) {
                Field field = (Field) attributes.get(i).getJavaMember();
                field.setAccessible(true);
                field.set(entity, tuple.get(i));
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 목록 행을 만들 수 없습니다", e);
        }
    }
}
