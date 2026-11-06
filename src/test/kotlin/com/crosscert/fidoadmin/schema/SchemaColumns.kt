package com.crosscert.fidoadmin.schema

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * src/test/resources/schema-columns.txt 를 테이블 → 컬럼 집합으로 파싱한다.
 * 이 파일은 저장소에 올리지 않는다. 없으면 [available] 가 false 다.
 */
object SchemaColumns {

    private val TABLE: Pattern = Pattern.compile("^## ([A-Z0-9_]+) ")
    private val COLUMN: Pattern = Pattern.compile("^\\s*\\d+\\.\\s+([A-Z0-9_]+)\\s")

    private const val RESOURCE = "/schema-columns.txt"

    /** 대조용 컬럼표가 클래스패스에 있는가. */
    @JvmStatic
    fun available(): Boolean = SchemaColumns::class.java.getResource(RESOURCE) != null

    @JvmStatic
    fun load(): Map<String, Set<String>> {
        try {
            SchemaColumns::class.java.getResourceAsStream(RESOURCE).use { input ->
                if (input == null) throw IllegalStateException("schema-columns.txt not found")
                val text = String(input.readAllBytes(), StandardCharsets.UTF_8)
                val result = LinkedHashMap<String, MutableSet<String>>()
                var current: String? = null
                // String.split("\n") 과 같은 규칙(끝의 빈 조각은 버린다). 빈 줄은 어느 패턴에도 맞지 않는다.
                for (line in text.split("\n")) {
                    val t = TABLE.matcher(line)
                    if (t.find()) { current = t.group(1); result[current] = LinkedHashSet(); continue }
                    val c = COLUMN.matcher(line)
                    if (current != null && c.find()) result[current]!!.add(c.group(1))
                }
                return result
            }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }
}
