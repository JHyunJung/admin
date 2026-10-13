package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.config.SecurityConfig;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * README "화면을 추가할 때" 의 규칙을 코드로 고정한다. 지금까지는 사람이 세 곳(컨트롤러·MenuRegistry·
 * SecurityConfig)을 손으로 맞췼고, 어긋나면 테넌트 화면이 선택 없이 열리거나 500 이 났다.
 * 모든 단언은 현재 코드가 통과하는 것이어야 한다 — 통과하지 못하면 그것이 곧 발견이다.
 *
 * <p>스프링 컨텍스트 없이 클래스패스를 훑고 생성자 인자는 모두 목으로 채운다. 훅이 돌려주는 값
 * (basePath, companyIdxAttribute, sortableProperties)은 상수라 목으로도 충분하다.
 */
class ArchitectureRulesTest {

    static final String BASE_PACKAGE = "com.crosscert.fidoadmin";

    /** 스캐너가 비어 있으면 아래 규칙들이 조용히 통과한다. 화면 수의 하한을 둔다. */
    @Test void scannerFindsTheScreens() {
        assertThat(screenControllers()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(services()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(classes(c -> c.getSimpleName().endsWith("Form"))).hasSizeGreaterThanOrEqualTo(20);
    }

    /** 메뉴의 superOnly 와 SecurityConfig 의 SUPER 매처는 같은 말을 해야 한다. */
    @Test void menuSuperOnlyMatchesSecurityMatchers() {
        SoftAssertions soft = new SoftAssertions();
        for (MenuItem m : MenuRegistry.ALL) {
            soft.assertThat(SecurityConfig.isSuperPath(m.href()))
                .as("%s (%s) superOnly=%s 인데 SecurityConfig SUPER 매처와 다르다", m.title(), m.href(), m.superOnly())
                .isEqualTo(m.superOnly());
        }
        soft.assertAll();
    }

    /**
     * 기반 컨트롤러를 상속한 화면의 경로는 MenuRegistry 에 있어야 한다(hidden 이어도 된다 — 영역 판정이 산다).
     * 없으면 areaOf 가 SYSTEM 으로 보아 테넌트 선택을 건너뛰므로, 그 경우는 SUPER 매처 안이어야만 한다.
     */
    @Test void everyScreenIsRegisteredInMenuOrBehindSuperMatcher() {
        MenuRegistry menus = new MenuRegistry();
        SoftAssertions soft = new SoftAssertions();
        for (Class<?> c : screenControllers()) {
            String path = requestMapping(c);
            soft.assertThat(menus.isRegistered(path) || SecurityConfig.isSuperPath(path))
                .as("%s(%s) 는 MenuRegistry 에 없고 SUPER 매처에도 없다", c.getSimpleName(), path).isTrue();
        }
        soft.assertAll();
    }

    /** basePath() 는 @RequestMapping 과 같은 문자열을 두 번 적는 자리다. 어긋나면 리다이렉트와 링크가 깨진다. */
    @Test void basePathEqualsRequestMapping() {
        SoftAssertions soft = new SoftAssertions();
        for (Class<?> c : screenControllers()) {
            Object controller = instantiate(c);
            soft.assertThat(invoke(controller, "basePath"))
                .as("%s.basePath()", c.getSimpleName()).isEqualTo(requestMapping(c));
        }
        soft.assertAll();
    }

    /**
     * companyIdxAttribute() 가 null 인 서비스(전역 테이블)의 화면은 SUPER 전용이다(README).
     * requireSuperForGlobalTable() 을 재정의해 의도적으로 연 서비스(CriteriaQueryService)는 예외다.
     */
    @Test void globalTableScreensAreSuperOnly() {
        SoftAssertions soft = new SoftAssertions();
        for (Class<?> c : screenControllers()) {
            Class<?> serviceType = serviceTypeOf(c);
            Object service = instantiate(serviceType);
            boolean global = invoke(service, "companyIdxAttribute") == null;
            boolean opened = overrides(serviceType, "requireSuperForGlobalTable");
            if (global && !opened) {
                soft.assertThat(SecurityConfig.isSuperPath(requestMapping(c)))
                    .as("%s 는 전역 테이블(%s)인데 SUPER 매처 밖이다", c.getSimpleName(), serviceType.getSimpleName()).isTrue();
            }
        }
        soft.assertAll();
    }

    /** 정렬 허용 목록과 기본 정렬의 속성명은 엔티티에 실제로 있어야 한다. 없으면 조회 시 500 이다. */
    @Test void sortablePropertiesExistOnEntity() {
        SoftAssertions soft = new SoftAssertions();
        for (Class<?> serviceType : services()) {
            Class<?> entity = GenericTypeResolver.resolveTypeArguments(serviceType, CrudService.class)[0];
            Object service = instantiate(serviceType);
            @SuppressWarnings("unchecked")
            Set<String> sortable = (Set<String>) invoke(service, "sortableProperties");
            List<String> props = new ArrayList<>(sortable);
            ((Sort) invoke(service, "defaultSort")).forEach(o -> props.add(o.getProperty()));
            for (String p : props) {
                soft.assertThat(hasPath(entity, p))
                    .as("%s: 정렬 속성 '%s' 이 %s 에 없다", serviceType.getSimpleName(), p, entity.getSimpleName()).isTrue();
            }
        }
        soft.assertAll();
    }

    /**
     * 폼의 문자열 길이는 @Size 가 아니라 @ByteSize 로 건다(BYTE 의미 컬럼). PasswordChangeForm 은 해시로
     * 저장되어 컬럼 길이와 무관하므로 글자 수 규칙(@Size)이 맞다 — 유일한 예외다.
     */
    @Test void formStringFieldsUseByteSizeNotSize() {
        Set<String> allowed = Set.of("com.crosscert.fidoadmin.auth.PasswordChangeForm");
        SoftAssertions soft = new SoftAssertions();
        for (Class<?> form : classes(c -> c.getSimpleName().endsWith("Form"))) {
            if (allowed.contains(form.getName())) continue;
            for (Field f : form.getDeclaredFields()) {
                soft.assertThat(f.isAnnotationPresent(Size.class))
                    .as("%s.%s 가 @Size 를 쓴다 — @ByteSize 여야 한다", form.getSimpleName(), f.getName()).isFalse();
            }
        }
        soft.assertAll();
    }

    // ---- 도우미 ----

    static List<Class<?>> screenControllers() {
        List<Class<?>> all = new ArrayList<>(subtypes(CrudController.class));
        all.addAll(subtypes(ReadOnlyController.class));
        return all;
    }

    static List<Class<?>> services() {
        return subtypes(CrudService.class);
    }

    static List<Class<?>> subtypes(Class<?> base) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(base));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
            .<Class<?>>map(bd -> load(bd.getBeanClassName()))
            .filter(c -> !Modifier.isAbstract(c.getModifiers()) && !c.getName().contains("Test"))
            .sorted((a, b) -> a.getName().compareTo(b.getName()))
            .toList();
    }

    static List<Class<?>> classes(java.util.function.Predicate<Class<?>> filter) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> filter.test(load(reader.getClassMetadata().getClassName())));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
            .<Class<?>>map(bd -> load(bd.getBeanClassName()))
            .filter(c -> !c.getName().contains("Test"))
            .toList();
    }

    static Class<?> load(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    static String requestMapping(Class<?> controller) {
        RequestMapping rm = controller.getAnnotation(RequestMapping.class);
        assertThat(rm).as("%s 에 @RequestMapping 이 없다", controller.getSimpleName()).isNotNull();
        return rm.value()[0];
    }

    /** 컨트롤러 생성자 인자 중 CrudService 하위 타입 하나가 그 화면의 서비스다. */
    static Class<?> serviceTypeOf(Class<?> controller) {
        for (Class<?> p : controller.getDeclaredConstructors()[0].getParameterTypes()) {
            if (CrudService.class.isAssignableFrom(p)) return p;
        }
        throw new AssertionError(controller.getSimpleName() + " 생성자에 CrudService 인자가 없다");
    }

    /** 유일한 생성자를 목 인자로 호출한다. 훅 값은 상수라 협력자가 목이어도 된다. */
    static Object instantiate(Class<?> type) {
        Constructor<?> ctor = java.util.Arrays.stream(type.getDeclaredConstructors())
            .filter(c -> c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))
            .findFirst().orElse(type.getDeclaredConstructors()[0]);
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        var params = ctor.getParameters();
        for (int i = 0; i < args.length; i++) {
            Class<?> p = params[i].getType();
            if (p == int.class || p == long.class) args[i] = 0;
            else if (p == boolean.class) args[i] = false;
            else if (p == String.class) args[i] = params[i].isAnnotationPresent(Value.class) ? "" : null;
            else args[i] = Mockito.mock(p);
        }
        try {
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " 생성 실패", e);
        }
    }

    static Object invoke(Object target, String method) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(method);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException e) {
                // 상위로
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(method, e);
            }
        }
        throw new IllegalStateException(target.getClass().getSimpleName() + " 에 " + method + " 가 없다");
    }

    /** {@code type} 이 CrudService 자체보다 아래에서 메서드를 다시 선언했는가. */
    static boolean overrides(Class<?> type, String method) {
        for (Class<?> c = type; c != null && c != CrudService.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod(method);
                return true;
            } catch (NoSuchMethodException ignored) {
                // 상위로
            }
        }
        return false;
    }

    /** "id.companyIdx" 같은 점 경로를 필드로 따라간다(@EmbeddedId 안의 속성). */
    static boolean hasPath(Class<?> type, String path) {
        Class<?> current = type;
        for (String part : path.split("\\.")) {
            Field f = findField(current, part);
            if (f == null) return false;
            current = f.getType();
        }
        return true;
    }

    static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { /* 상위로 */ }
        }
        return null;
    }
}
