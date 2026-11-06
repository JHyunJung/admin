package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.company.entity.CcfaLicense
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.springframework.data.jpa.domain.Specification

/**
 * Specification 을 기록용 Criteria API 위에서 평가해 `속성 = 값` 조건을 뽑아낸다.
 *
 * 목록 조회의 테넌트 필터는 격리의 핵심인데, Specification 이 null 이 아닌지만 검사하면
 * 필터를 통째로 제거해도 테스트가 통과한다. 실제로 어떤 조건이 만들어졌는지 확인하기 위한 도구다.
 */
internal object CriteriaProbe {

    /** spec 을 평가하며 root 에서 내려간 경로 조각을 순서대로 돌려준다. */
    @JvmStatic
    fun pathStepsFor(spec: Specification<CcfaLicense>?): List<String> {
        val steps = ArrayList<String>()
        if (spec == null) return steps

        val dummy = proxy(Predicate::class.java) { m, _ -> if (m.name == "getExpressions") listOf<Any>() else null }
        val cb = proxy(CriteriaBuilder::class.java) { _, _ -> dummy }
        val query = proxy(CriteriaQuery::class.java) { _, _ -> null }
        val root = proxy(Root::class.java) { m, args ->
            if (m.name == "get" && args != null && args.size == 1) {
                steps.add(args[0].toString())
                recordingPath(steps)
            } else {
                null
            }
        }

        @Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
        val ignored = spec.toPredicate(root as Root<CcfaLicense>, query, cb)
        return steps
    }

    private fun recordingPath(steps: MutableList<String>): Path<*> =
        proxy(Path::class.java) { m, args ->
            if (m.name == "get" && args != null && args.size == 1) {
                steps.add(args[0].toString())
                recordingPath(steps)
            } else {
                null
            }
        }

    /** spec 이 만든 조건 중 attr 에 걸린 등치 값을 돌려준다. 없으면 null. */
    @JvmStatic
    fun equalsValueFor(spec: Specification<CcfaLicense>?, attr: String): Long? {
        if (spec == null) return null

        val pathNames = ArrayList<String>()
        val equals = ArrayList<Array<Any?>>()

        val path = proxy(Path::class.java) { m, args ->
            if (m.name == "get" && args != null && args.size == 1) {
                pathNames.add(args[0].toString())
                proxyPathNamed(args[0].toString(), pathNames)
            } else {
                null
            }
        }

        val root = proxy(Root::class.java) { m, args ->
            if (m.name == "get" && args != null && args.size == 1) {
                proxyPathNamed(args[0].toString(), pathNames)
            } else {
                path
            }
        }

        val dummy = proxy(Predicate::class.java) { m, _ ->
            if (m.name == "getExpressions") listOf<Any>() else null
        }

        val cb = proxy(CriteriaBuilder::class.java) { m, args ->
            if (m.name == "equal" && args != null && args.size == 2) {
                equals.add(arrayOf(nameOf(args[0]), args[1]))
            }
            dummy
        }

        val query = proxy(CriteriaQuery::class.java) { _, _ -> null }

        @Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
        val ignored = spec.toPredicate(root as Root<CcfaLicense>, query, cb)

        for (pair in equals) {
            val v = pair[1]
            if (attr == pair[0] && v is Long) return v
        }
        return null
    }

    @Suppress("unused")
    private val LAST_NAME = ThreadLocal<String>()

    private fun proxyPathNamed(name: String, names: MutableList<String>): Path<*> =
        proxy(Path::class.java) { m, args ->
            if (m.name == "toString") {
                name
            } else if (m.name == "get" && args != null && args.size == 1) {
                proxyPathNamed(args[0].toString(), names)
            } else {
                null
            }
        }

    private fun nameOf(pathOrValue: Any?): String? = pathOrValue?.toString()

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, handler: Handler): T =
        Proxy.newProxyInstance(
            CriteriaProbe::class.java.classLoader, arrayOf<Class<*>>(type)
        ) { p, method, args ->
            if (method.name == "toString" && (args == null || args.isEmpty())) {
                val r = handler.handle(method, args)
                return@newProxyInstance r ?: "proxy"
            }
            if (method.name == "hashCode") return@newProxyInstance System.identityHashCode(p)
            if (method.name == "equals") return@newProxyInstance p === args!![0]
            handler.handle(method, args)
        } as T

    private fun interface Handler {
        fun handle(method: Method, args: Array<out Any?>?): Any?
    }
}
