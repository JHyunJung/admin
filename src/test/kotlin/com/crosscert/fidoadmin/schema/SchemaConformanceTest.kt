package com.crosscert.fidoadmin.schema

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.lang.reflect.Modifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

/**
 * 엔티티 39개의 @Table/@Column 이름 집합이 스키마 컬럼표와 정확히 같은지 검사한다.
 * 컬럼표는 저장소에 올리지 않으므로, 없는 환경에서는 건너뛴다.
 */
class SchemaConformanceTest {

    @Test
    fun everySchemaTableHasExactlyMatchingEntity() {
        assumeTrue(SchemaColumns.available(), "schema-columns.txt 가 없어 건너뛴다")
        val schema = SchemaColumns.load()
        assertThat(schema).hasSize(39)
        assertThat(schema.values.sumOf { it.size }).isEqualTo(354)

        val entities = scanEntities()
        assertThat(entities.keys).containsExactlyInAnyOrderElementsOf(schema.keys)
        schema.forEach { (table, cols) ->
            assertThat(entities[table]).`as`("columns of %s", table).containsExactlyInAnyOrderElementsOf(cols)
        }
    }

    private fun scanEntities(): Map<String, Set<String>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        val result = LinkedHashMap<String, Set<String>>()
        for (bd in scanner.findCandidateComponents(BASE)) {
            val type = Class.forName(bd.beanClassName)
            val table = type.getAnnotation(Table::class.java)
            assertThat(table).`as`("@Table on %s", type.simpleName).isNotNull()
            result[unquote(table.name)] = collectColumns(type)
        }
        return result
    }

    private fun collectColumns(type: Class<*>): Set<String> {
        val cols = LinkedHashSet<String>()
        for (f in type.declaredFields) {
            if (Modifier.isStatic(f.modifiers) || f.isAnnotationPresent(Transient::class.java)) continue
            if (f.isAnnotationPresent(EmbeddedId::class.java)) {
                assertThat(f.type.isAnnotationPresent(Embeddable::class.java)).isTrue()
                cols.addAll(collectColumns(f.type))
                continue
            }
            val c = f.getAnnotation(Column::class.java)
            assertThat(c).`as`("@Column missing on %s.%s", type.simpleName, f.name).isNotNull()
            assertThat(c.name).`as`("@Column name empty on %s.%s", type.simpleName, f.name).isNotEmpty()
            cols.add(unquote(c.name))
        }
        return cols
    }

    private companion object {
        const val BASE = "com.crosscert.fidoadmin"

        fun unquote(s: String): String = s.replace("\"", "")
    }
}
