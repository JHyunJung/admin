package com.crosscert.fidoadmin.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** CLOB 에 담긴 JSON(FIDO_LOGS.JSONDATA, CRITERIA.JSONDATA 등)을 상세 화면용으로 정리한다. */
public final class JsonPretty {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonPretty() {}

    /** JSON 이면 들여쓰기해 돌려주고, 아니면 원문 그대로 돌려준다(절대 예외를 던지지 않는다). */
    public static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node == null || !node.isContainerNode()) return raw;
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    /** 객체 또는 배열 형태의 JSON 인지. 폼 검증(JSONDATA 편집)에 쓴다. */
    public static boolean isValidJson(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            JsonNode node = MAPPER.readTree(raw);
            return node != null && node.isContainerNode();
        } catch (Exception e) {
            return false;
        }
    }
}
