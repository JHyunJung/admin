package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.config.SecurityConfig
import jakarta.validation.constraints.Size
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.function.Predicate
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.GenericTypeResolver
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.RequestMapping

/**
 * README "화면을 추가할 때" 의 규칙을 코드로 고정한다. 지금까지는 사람이 세 곳(컨트롤러·MenuRegistry·
 * SecurityConfig)을 손으로 맞췄고, 어긋나면 테넌트 화면이 선택 없이 열리거나 500 이 났다.
 * 모든 단언은 현재 코드가 통과하는 것이어야 한다 — 통과하지 못하면 그것이 곧 발견이다.
 *
 * 스프링 컨텍스트 없이 클래스패스를 훑고 생성자 인자는 모두 목으로 채운다. 훅이 돌려주는 값
 * (basePath, companyIdxAttribute, sortableProperties)은 상수라 목으로도 충분하다.
 */
class ArchitectureRulesTest {

    /** 스캐너가 비어 있으면 아래 규칙들이 조용히 통과한다. 화면 수의 하한을 둔다. */
    @Test fun scannerFindsTheScreens() {
        assertThat(screenControllers()).hasSizeGreaterThanOrEqualTo(20)
        assertThat(services()).hasSizeGreaterThanOrEqualTo(20)
        assertThat(classes { name -> name.endsWith("Form") }).hasSizeGreaterThanOrEqualTo(20)
    }

    /** 메뉴의 superOnly 와 SecurityConfig 의 SUPER 매처는 같은 말을 해야 한다. */
    @Test fun menuSuperOnlyMatchesSecurityMatchers() {
        val soft = SoftAssertions()
        for (m in MenuRegistry.ALL) {
            soft.assertThat(SecurityConfig.isSuperPath(m.href))
                .`as`("%s (%s) superOnly=%s 인데 SecurityConfig SUPER 매처와 다르다", m.title, m.href, m.superOnly)
                .isEqualTo(m.superOnly)
        }
        soft.assertAll()
    }

    /**
     * 기반 컨트롤러를 상속한 화면의 경로는 MenuRegistry 에 있어야 한다(hidden 이어도 된다 — 영역 판정이 산다).
     * 없으면 areaOf 가 SYSTEM 으로 보아 테넌트 선택을 건너뛰므로, 그 경우는 SUPER 매처 안이어야만 한다.
     */
    @Test fun everyScreenIsRegisteredInMenuOrBehindSuperMatcher() {
        val menus = MenuRegistry()
        val soft = SoftAssertions()
        for (c in screenControllers()) {
            val path = requestMapping(c)
            soft.assertThat(menus.isRegistered(path) || SecurityConfig.isSuperPath(path))
                .`as`("%s(%s) 는 MenuRegistry 에 없고 SUPER 매처에도 없다", c.simpleName, path).isTrue()
        }
        soft.assertAll()
    }

    /** basePath() 는 @RequestMapping 과 같은 문자열을 두 번 적는 자리다. 어긋나면 리다이렉트와 링크가 깨진다. */
    @Test fun basePathEqualsRequestMapping() {
        val soft = SoftAssertions()
        for (c in screenControllers()) {
            val controller = instantiate(c)
            soft.assertThat(invoke(controller, "basePath"))
                .`as`("%s.basePath()", c.simpleName).isEqualTo(requestMapping(c))
        }
        soft.assertAll()
    }

    /**
     * companyIdxAttribute() 가 null 인 서비스(전역 테이블)의 화면은 SUPER 전용이다(README).
     * requireSuperForGlobalTable() 을 재정의해 의도적으로 연 서비스(CriteriaQueryService)는 예외다.
     */
    @Test fun globalTableScreensAreSuperOnly() {
        val soft = SoftAssertions()
        for (c in screenControllers()) {
            val serviceType = serviceTypeOf(c)
            val service = instantiate(serviceType)
            val global = invoke(service, "companyIdxAttribute") == null
            val opened = overrides(serviceType, "requireSuperForGlobalTable")
            if (global && !opened) {
                soft.assertThat(SecurityConfig.isSuperPath(requestMapping(c)))
                    .`as`("%s 는 전역 테이블(%s)인데 SUPER 매처 밖이다", c.simpleName, serviceType.simpleName).isTrue()
            }
        }
        soft.assertAll()
    }

    /** 정렬 허용 목록과 기본 정렬의 속성명은 엔티티에 실제로 있어야 한다. 없으면 조회 시 500 이다. */
    @Test fun sortablePropertiesExistOnEntity() {
        val soft = SoftAssertions()
        for (serviceType in services()) {
            val entity = GenericTypeResolver.resolveTypeArguments(serviceType, CrudService::class.java)!![0]
            val service = instantiate(serviceType)
            @Suppress("UNCHECKED_CAST")
            val sortable = invoke(service, "sortableProperties") as Set<String>
            val props = ArrayList<String>(sortable)
            for (o in invoke(service, "defaultSort") as Sort) props.add(o.property)
            for (p in props) {
                soft.assertThat(hasPath(entity, p))
                    .`as`("%s: 정렬 속성 '%s' 이 %s 에 없다", serviceType.simpleName, p, entity.simpleName).isTrue()
            }
        }
        soft.assertAll()
    }

    /**
     * 폼의 문자열 길이는 @Size 가 아니라 @ByteSize 로 건다(BYTE 의미 컬럼). PasswordChangeForm 은 해시로
     * 저장되어 컬럼 길이와 무관하므로 글자 수 규칙(@Size)이 맞다 — 유일한 예외다.
     */
    @Test fun formStringFieldsUseByteSizeNotSize() {
        val allowed = setOf("com.crosscert.fidoadmin.auth.PasswordChangeForm")
        val soft = SoftAssertions()
        for (form in classes { name -> name.endsWith("Form") }) {
            if (allowed.contains(form.name)) continue
            for (f in form.declaredFields) {
                soft.assertThat(f.isAnnotationPresent(Size::class.java))
                    .`as`("%s.%s 가 @Size 를 쓴다 — @ByteSize 여야 한다", form.simpleName, f.name).isFalse()
            }
        }
        soft.assertAll()
    }

    // ---- 도우미 ----

    companion object {
        const val BASE_PACKAGE = "com.crosscert.fidoadmin"

        private val DOT: Pattern = Pattern.compile("\\.")

        @JvmStatic
        fun screenControllers(): List<Class<*>> {
            val all = ArrayList<Class<*>>(subtypes(CrudController::class.java))
            all.addAll(subtypes(ReadOnlyController::class.java))
            return all
        }

        @JvmStatic
        fun services(): List<Class<*>> = subtypes(CrudService::class.java)

        @JvmStatic
        fun subtypes(base: Class<*>): List<Class<*>> {
            val scanner = ClassPathScanningCandidateComponentProvider(false)
            scanner.addIncludeFilter(AssignableTypeFilter(base))
            return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map { bd -> load(bd.beanClassName!!) }
                .filter { c -> !Modifier.isAbstract(c.modifiers) && !c.name.contains("Test") }
                .sorted { a, b -> a.name.compareTo(b.name) }
                .toList()
        }

        /**
         * 클래스 이름으로 고른다. 메타데이터 단계에서 걸러야 한다 — 후보가 아닌 클래스까지 로딩하면
         * 테스트 클래스의 정적 초기화(Testcontainers 기동 등)가 따라 돈다.
         */
        @JvmStatic
        fun classes(simpleNameFilter: Predicate<String>): List<Class<*>> {
            val scanner = ClassPathScanningCandidateComponentProvider(false)
            scanner.addIncludeFilter { reader, _ ->
                val name = reader.classMetadata.className
                val simple = name.substring(name.lastIndexOf('.') + 1)
                !name.contains("Test") && simpleNameFilter.test(simple)
            }
            return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map { bd -> load(bd.beanClassName!!) }
                .toList()
        }

        @JvmStatic
        fun load(name: String): Class<*> {
            try { return Class.forName(name) } catch (e: ClassNotFoundException) { throw IllegalStateException(e) }
        }

        @JvmStatic
        fun requestMapping(controller: Class<*>): String {
            val rm = controller.getAnnotation(RequestMapping::class.java)
            assertThat(rm).`as`("%s 에 @RequestMapping 이 없다", controller.simpleName).isNotNull()
            return rm.value[0]
        }

        /** 컨트롤러 생성자 인자 중 CrudService 하위 타입 하나가 그 화면의 서비스다. */
        @JvmStatic
        fun serviceTypeOf(controller: Class<*>): Class<*> {
            for (p in controller.declaredConstructors[0].parameterTypes) {
                if (CrudService::class.java.isAssignableFrom(p)) return p
            }
            throw AssertionError(controller.simpleName + " 생성자에 CrudService 인자가 없다")
        }

        /** 유일한 생성자를 목 인자로 호출한다. 훅 값은 상수라 협력자가 목이어도 된다. */
        @JvmStatic
        fun instantiate(type: Class<*>): Any {
            val ctor: Constructor<*> = type.declaredConstructors
                .firstOrNull { c -> c.isAnnotationPresent(Autowired::class.java) }
                ?: type.declaredConstructors[0]
            ctor.isAccessible = true
            val args = arrayOfNulls<Any>(ctor.parameterCount)
            val params = ctor.parameters
            for (i in args.indices) {
                val p = params[i].type
                if (p == Int::class.javaPrimitiveType || p == Long::class.javaPrimitiveType) args[i] = 0
                else if (p == Boolean::class.javaPrimitiveType) args[i] = false
                else if (p == String::class.java) args[i] = if (params[i].isAnnotationPresent(Value::class.java)) "" else null
                else args[i] = Mockito.mock(p)
            }
            try {
                return ctor.newInstance(*args)
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException(type.simpleName + " 생성 실패", e)
            }
        }

        @JvmStatic
        fun invoke(target: Any, method: String): Any? {
            var c: Class<*>? = target.javaClass
            while (c != null) {
                try {
                    val m = c.getDeclaredMethod(method)
                    m.isAccessible = true
                    return m.invoke(target)
                } catch (e: NoSuchMethodException) {
                    // 상위로
                } catch (e: ReflectiveOperationException) {
                    throw IllegalStateException(method, e)
                }
                c = c.superclass
            }
            throw IllegalStateException(target.javaClass.simpleName + " 에 " + method + " 가 없다")
        }

        /** `type` 이 CrudService 자체보다 아래에서 메서드를 다시 선언했는가. */
        @JvmStatic
        fun overrides(type: Class<*>, method: String): Boolean {
            var c: Class<*>? = type
            while (c != null && c != CrudService::class.java) {
                try {
                    c.getDeclaredMethod(method)
                    return true
                } catch (ignored: NoSuchMethodException) {
                    // 상위로
                }
                c = c.superclass
            }
            return false
        }

        /** "id.companyIdx" 같은 점 경로를 필드로 따라간다(@EmbeddedId 안의 속성). */
        @JvmStatic
        fun hasPath(type: Class<*>, path: String): Boolean {
            var current = type
            for (part in DOT.split(path)) {
                val f = findField(current, part) ?: return false
                current = f.type
            }
            return true
        }

        @JvmStatic
        fun findField(type: Class<*>, name: String): Field? {
            var c: Class<*>? = type
            while (c != null && c != Any::class.java) {
                try { return c.getDeclaredField(name) } catch (ignored: NoSuchFieldException) { /* 상위로 */ }
                c = c.superclass
            }
            return null
        }
    }
}
