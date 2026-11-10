package com.crosscert.fidoadmin.common

import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Selection
import jakarta.persistence.metamodel.SingularAttribute
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.query.QueryUtils
import org.springframework.data.support.PageableExecutionUtils

/**
 * [PartialFetch] 구현. 메타모델의 단일값 속성 가운데 제외 목록에 없는 것만 Tuple 로 SELECT 하고,
 * 기본 생성자로 만든 엔티티의 필드에 리플렉션으로 채운다(엔티티는 필드 접근이다).
 *
 * 클래스 이름이 `PartialFetchImpl` 인 것은 Spring Data 가 조각 구현을 "인터페이스 이름 + Impl" 로
 * 찾기 때문이다. 리포지터리 스캔이 찾으므로 `@DataJpaTest` 조각에서도 함께 올라온다.
 */
open class PartialFetchImpl<E>(private val em: EntityManager) : PartialFetch<E> {

    override fun findAllExcluding(type: Class<E>, spec: Specification<E>?, pageable: Pageable, excluded: Set<String>): Page<E> {
        val content = findContentExcluding(type, spec, pageable, excluded)
        return PageableExecutionUtils.getPage(content, pageable) { countMatching(type, spec) }
    }

    override fun findContentExcluding(type: Class<E>, spec: Specification<E>?, pageable: Pageable, excluded: Set<String>): List<E> {
        if (excluded.isEmpty()) return findEntities(type, spec, pageable)
        val cb = em.criteriaBuilder
        val query = cb.createTupleQuery()
        val root = query.from(type)

        val attributes: List<SingularAttribute<in E, *>> = em.metamodel.entity(type).singularAttributes.stream()
            .filter { a -> !excluded.contains(a.name) }
            .sorted(Comparator.comparing { a: SingularAttribute<in E, *> -> a.name })
            .toList()
        val selections = ArrayList<Selection<*>>(attributes.size)
        for (a in attributes) {
            selections.add(root.get<Any>(a.name))
        }
        query.multiselect(selections)
        restrict(query, root, cb, spec, pageable)
        val rows = RowMaterializer(type, attributes)
        return page(em.createQuery(query), pageable).resultList.stream()
            .map { t -> rows.materialize(t) }.toList()
    }

    /** 제외할 속성이 없으면 엔티티를 그대로 읽는다. */
    private fun findEntities(type: Class<E>, spec: Specification<E>?, pageable: Pageable): List<E> {
        val cb = em.criteriaBuilder
        val query = cb.createQuery(type)
        val root = query.from(type)
        query.select(root)
        restrict(query, root, cb, spec, pageable)
        return page(em.createQuery(query), pageable).resultList
    }

    private fun restrict(query: CriteriaQuery<*>, root: Root<E>, cb: CriteriaBuilder, spec: Specification<E>?, pageable: Pageable) {
        val where = spec?.toPredicate(root, query, cb)
        if (where != null) query.where(where)
        if (pageable.sort.isSorted) query.orderBy(QueryUtils.toOrders(pageable.sort, root, cb))
    }

    private fun countMatching(type: Class<E>, spec: Specification<E>?): Long {
        val cb = em.criteriaBuilder
        val query = cb.createQuery(Long::class.javaObjectType)
        val root = query.from(type)
        query.select(cb.count(root))
        val where = spec?.toPredicate(root, query, cb)
        if (where != null) query.where(where)
        return em.createQuery(query).singleResult
    }

    /**
     * Tuple 의 값을 같은 순서의 속성 필드에 넣는다. 돌려주는 엔티티는 영속 상태가 아니다.
     *
     * 생성자와 `Field`(접근 허용 포함)는 처음 쓰는 행에서 한 번만 구해 이후 행에 재사용한다.
     * 첫 행의 리플렉션 순서(생성자 조회 → 인스턴스 생성 → 속성마다 캐스팅·접근 허용·대입)는
     * 행마다 구하던 때와 같고, 빈 결과에서는 리플렉션을 하지 않는다.
     */
    private class RowMaterializer<E>(private val type: Class<E>, private val attributes: List<SingularAttribute<in E, *>>) {
        private var constructor: Constructor<E>? = null
        private val fields = arrayOfNulls<Field>(attributes.size)

        fun materialize(tuple: Tuple): E {
            try {
                val ctor = constructor ?: type.getDeclaredConstructor().also { constructor = it }
                val entity = ctor.newInstance()
                for (i in attributes.indices) {
                    val field = fields[i] ?: (attributes[i].javaMember as Field).also {
                        it.isAccessible = true
                        fields[i] = it
                    }
                    field.set(entity, tuple.get(i))
                }
                return entity
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException(type.simpleName + " 목록 행을 만들 수 없습니다", e)
            }
        }
    }

    private companion object {
        fun <T> page(typed: TypedQuery<T>, pageable: Pageable): TypedQuery<T> {
            if (pageable.isPaged) {
                typed.setFirstResult(pageable.offset.toInt())
                typed.setMaxResults(pageable.pageSize)
            }
            return typed
        }
    }
}
