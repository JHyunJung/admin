package com.crosscert.fidoadmin.statistics.batch;

import com.crosscert.fidoadmin.log.service.FidoLogPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * FIDO 로그 한 건을 통계 칸으로 나눈다. 구 배치(FIDOLogWorker / FIDOStatistics)와 같은 규칙이다.
 *
 * <ul>
 *   <li>{@code op == "TC"} → TC</li>
 *   <li>{@code op == "Auth"} → {@code request.requestData} 문자열에 {@code "transaction"} 이 있으면 TC, 아니면 Auth</li>
 *   <li>{@code op == "Reg"} → Reg, {@code op == "DeReg"} → DeReg, 그 밖 → 세지 않음</li>
 *   <li>{@code status == "Success"} 면 성공, 그 밖(RequestOK·ResponseOK·Wait·Error·없음) → 실패</li>
 * </ul>
 * 구 배치는 Gson enum 매핑이라 대소문자가 정확히 같아야 인정했다. 여기서도 같다.
 *
 * <p>화면의 TC 판정({@link FidoLogPayload#type()})과 일부러 다르다. 통계는 과거 숫자와 이어져야 하므로 구 규칙을 따른다.
 */
public final class LogClassifier {

    public enum Kind { AUTH, TC, REG, DEREG, NONE }

    /** @param success 성공(_S) 칸이면 true */
    public record Result(Kind kind, boolean success) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LogClassifier() {}

    /** 풀지 못한 로그면 비어 있다(세지 않는다). */
    public static Optional<Result> classify(String jsondata) {
        FidoLogPayload p = FidoLogPayload.parse(jsondata);
        if (p.json() == null) return Optional.empty();
        String op = p.op();
        Kind kind;
        if ("TC".equals(op)) kind = Kind.TC;
        else if ("Auth".equals(op)) kind = requestHasTransaction(p.json()) ? Kind.TC : Kind.AUTH;
        else if ("Reg".equals(op)) kind = Kind.REG;
        else if ("DeReg".equals(op)) kind = Kind.DEREG;
        else kind = Kind.NONE;
        return Optional.of(new Result(kind, "Success".equals(p.status())));
    }

    /**
     * {@code transaction.request.requestData}(없으면 최상위 {@code request.requestData})에 "transaction" 이 들어 있는가.
     * 값이 객체면 JSON 문자열로 보고 찾는다.
     */
    static boolean requestHasTransaction(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            return false;
        }
        if (root == null) return false;
        for (JsonNode base : new JsonNode[] {root.path("transaction"), root}) {
            JsonNode data = base.path("request").path("requestData");
            if (data.isMissingNode() || data.isNull()) continue;
            String text = data.isValueNode() ? data.asText() : data.toString();
            return text.contains("transaction");
        }
        return false;
    }
}
