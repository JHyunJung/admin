package com.crosscert.fidoadmin.log.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * FIDO 로그 JSONDATA 를 화면이 쓰는 값으로 푼 것.
 *
 * <p>운영 FIDO 서버는 JSONDATA 에 <b>base64url 로 인코딩한</b> {@code FIDOLog} JSON 을 넣는다
 * (이전 어드민 parseLog: base64url 디코드 → Gson). 값은 {@code transaction} 객체 안에 있다 —
 * {@code serviceName}, {@code userName}, {@code op}(Reg/Auth/DeReg/TC), {@code bioType}, {@code status}.
 * 요청 IP·UserAgent·로깅시간은 바깥 {@code FIDOLog} 의 {@code accessIp}·{@code userAgent}·{@code logtime} 이다.
 * 로컬 시드·더미는 평문 JSON 에 키가 최상위라 둘 다 받는다.
 *
 * <p>실제 운영 샘플 없이 이전 소스 기록(docs/legacy/admin-java-features.md "로그 파이프라인")만 보고 정한
 * 키다. <b>운영 키가 다르면 아래 키 목록만 고치면 된다</b> — SQL 과 화면은 이 클래스의 결과만 본다.
 * 어떤 값이 와도 예외를 던지지 않는다. 풀지 못하면 모든 값이 null 이다.
 *
 * @param json 디코드한 JSON 원문. 풀지 못했으면 null(상세 화면은 그때 원문을 그대로 보여 준다)
 */
public record FidoLogPayload(String op, String serviceName, String userName, Long bioType, String status,
                             String accessIp, String userAgent, String logtime, boolean tcRequest, String json) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FidoLogPayload EMPTY = new FidoLogPayload(null, null, null, null, null, null, null, null, false, null);

    private static final String[] OP_KEYS = {"op"};
    private static final String[] SERVICE_KEYS = {"serviceName", "servicename"};
    private static final String[] USER_KEYS = {"userName", "userid", "userId"};
    private static final String[] BIO_KEYS = {"bioType"};
    private static final String[] STATUS_KEYS = {"status"};
    private static final String[] IP_KEYS = {"accessIp", "accessIP"};
    private static final String[] UA_KEYS = {"userAgent"};
    private static final String[] LOGTIME_KEYS = {"logtime", "logTime"};

    /** 이전 어드민 규칙: 사용자 이름의 {@code _*} 뒤는 서비스 구분이고, {@code _*KF} 로 끝나면 KFIDO 다. */
    private static final String USER_SEPARATOR = "_*";
    private static final String KFIDO_SUFFIX = "_*KF";

    public static FidoLogPayload parse(String jsondata) {
        if (jsondata == null || jsondata.isBlank()) return EMPTY;
        String text = jsondata.trim();
        String json = text.startsWith("{") ? text : decodeBase64(text);
        if (json == null) return EMPTY;
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            return EMPTY;
        }
        if (root == null || !root.isObject()) return EMPTY;
        JsonNode tx = root.path("transaction").isObject() ? root.get("transaction") : null;
        return new FidoLogPayload(
            text(tx, root, OP_KEYS),
            text(tx, root, SERVICE_KEYS),
            text(tx, root, USER_KEYS),
            number(text(tx, root, BIO_KEYS)),
            text(tx, root, STATUS_KEYS),
            text(tx, root, IP_KEYS),
            text(tx, root, UA_KEYS),
            text(tx, root, LOGTIME_KEYS),
            // 이전 어드민: Auth 인데 요청에 transaction 이 들어 있으면 TC(전자서명 확인)로 센다.
            // 요청 키 이름을 확인하지 못해 transaction 객체 안 어디든 transaction 필드가 있으면 그렇게 본다.
            tx != null && tx.findValue("transaction") != null,
            json);
    }

    /** 이전 어드민 BioType 과 같은 표. 모르는 코드와 없는 값은 "알수없음". */
    public static String bioTypeLabel(Long bioType) {
        if (bioType == null) return "알수없음";
        return switch (bioType.intValue()) {
            case 1 -> "PRESENCE";
            case 2 -> "지문";
            case 4 -> "PIN";
            case 8 -> "음성";
            case 16 -> "얼굴인식";
            case 32 -> "지역기반";
            case 64 -> "홍채";
            case 128 -> "패턴";
            case 256 -> "손바닥";
            case 512 -> "없음";
            case 1024 -> "ALL";
            default -> "알수없음";
        };
    }

    public String bioTypeLabel() { return bioTypeLabel(bioType); }

    /**
     * 사용자 이름에서 첫 {@code _*} 뒤 서비스 꼬리를 뗀 앞부분. 꼬리가 없으면 이름 그대로.
     * 예: {@code 113057331000001_0_*com.kbstar.kbbiz_*KF} → {@code 113057331000001_0}.
     */
    public String userHead() {
        if (userName == null) return null;
        int at = userName.indexOf(USER_SEPARATOR);
        return at < 0 ? userName : userName.substring(0, at);
    }

    /** FIDO 종류. 사용자 이름이 {@code _*KF} 로 끝나면 KFIDO, 아니면 FIDO. 이름이 없으면 null. */
    public String fidoKind() {
        if (userName == null) return null;
        return userName.endsWith(KFIDO_SUFFIX) ? "KFIDO" : "FIDO";
    }

    /** 타입. 구분(op)에 TC 판정을 더한 값이다 — Auth 인데 요청에 transaction 이 있으면 TC. */
    public String type() {
        if (op != null && op.equalsIgnoreCase("Auth") && tcRequest) return "TC";
        return op;
    }

    /** base64url·표준 base64·패딩 유무·줄바꿈을 모두 받는다. 풀리지 않거나 UTF-8 이 아니면 null. */
    private static String decodeBase64(String text) {
        String normalized = text.replaceAll("\\s", "").replace('+', '-').replace('/', '_').replace("=", "");
        try {
            return new String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** transaction 안을 먼저, 없으면 최상위를 본다. 키마다 앞의 이름이 우선이다. */
    private static String text(JsonNode tx, JsonNode root, String[] keys) {
        for (JsonNode node : new JsonNode[] {tx, root}) {
            if (node == null) continue;
            for (String key : keys) {
                JsonNode v = node.get(key);
                if (v != null && v.isValueNode() && !v.isNull() && !v.asText().isBlank()) return v.asText();
            }
        }
        return null;
    }

    private static Long number(String value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
