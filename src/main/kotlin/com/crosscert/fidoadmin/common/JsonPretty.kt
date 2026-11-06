package com.crosscert.fidoadmin.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/** CLOB 에 담긴 JSON(FIDO_LOGS.JSONDATA, CRITERIA.JSONDATA 등)을 상세 화면용으로 정리한다. */
object JsonPretty {

    private val MAPPER: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    /** JSON 이면 들여쓰기해 돌려주고, 아니면 원문 그대로 돌려준다(절대 예외를 던지지 않는다). */
    @JvmStatic
    fun pretty(raw: String?): String? {
        if (raw == null || raw.javaIsBlank()) return raw
        return try {
            val node = MAPPER.readTree(raw)
            if (node == null || !node.isContainerNode) raw
            else MAPPER.writeValueAsString(node)
        } catch (e: Exception) {
            raw
        }
    }

    /** 객체 또는 배열 형태의 JSON 인지. 폼 검증(JSONDATA 편집)에 쓴다. */
    @JvmStatic
    fun isValidJson(raw: String?): Boolean {
        if (raw == null || raw.javaIsBlank()) return false
        return try {
            val node = MAPPER.readTree(raw)
            node != null && node.isContainerNode
        } catch (e: Exception) {
            false
        }
    }
}
