package com.crosscert.fidoadmin.erd;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** 엔티티 39개의 @Table/@Column 이름 집합이 ERD 추출본과 정확히 같은지 검사한다. */
class ErdConformanceTest {

    private static final String BASE = "com.crosscert.fidoadmin";

    @Test
    void everyErdTableHasExactlyMatchingEntity() throws Exception {
        Map<String, Set<String>> erd = ErdColumns.load();
        assertThat(erd).hasSize(39);
        assertThat(erd.values().stream().mapToInt(Set::size).sum()).isEqualTo(354);

        Map<String, Set<String>> entities = scanEntities();
        assertThat(entities.keySet()).containsExactlyInAnyOrderElementsOf(erd.keySet());
        erd.forEach((table, cols) ->
            assertThat(entities.get(table)).as("columns of %s", table).containsExactlyInAnyOrderElementsOf(cols));
    }

    private Map<String, Set<String>> scanEntities() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (var bd : scanner.findCandidateComponents(BASE)) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            Table table = type.getAnnotation(Table.class);
            assertThat(table).as("@Table on %s", type.getSimpleName()).isNotNull();
            result.put(unquote(table.name()), collectColumns(type));
        }
        return result;
    }

    private Set<String> collectColumns(Class<?> type) {
        Set<String> cols = new LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Transient.class)) continue;
            if (f.isAnnotationPresent(EmbeddedId.class)) {
                assertThat(f.getType().isAnnotationPresent(Embeddable.class)).isTrue();
                cols.addAll(collectColumns(f.getType()));
                continue;
            }
            Column c = f.getAnnotation(Column.class);
            assertThat(c).as("@Column missing on %s.%s", type.getSimpleName(), f.getName()).isNotNull();
            assertThat(c.name()).as("@Column name empty on %s.%s", type.getSimpleName(), f.getName()).isNotEmpty();
            cols.add(unquote(c.name()));
        }
        return cols;
    }

    private static String unquote(String s) {
        return s.replace("\"", "");
    }
}
