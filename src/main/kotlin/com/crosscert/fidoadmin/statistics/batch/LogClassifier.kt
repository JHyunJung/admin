package com.crosscert.fidoadmin.statistics.batch

import com.crosscert.fidoadmin.log.service.FidoLogPayload
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * FIDO 로그 한 건을 통계 칸으로 나눈다. 구 배치(FIDOLogWorker / FIDOStatistics)와 같은 규칙이다.
 *
 * - `op == "TC"` → TC
 * - `op == "Auth"` → `request.requestData` 문자열에 `"transaction"` 이 있으면 TC, 아니면 Auth
 * - `op == "Reg"` → Reg, `op == "DeReg"` → DeReg, 그 밖 → 세지 않음
 * - `status == "Success"` 면 성공, 그 밖(RequestOK·ResponseOK·Wait·Error·없음) → 실패
 *
 * 구 배치는 Gson enum 매핑이라 대소문자가 정확히 같아야 인정했다. 여기서도 같다.
 *
 * 화면의 TC 판정([FidoLogPayload.type])과 일부러 다르다. 통계는 과거 숫자와 이어져야 하므로 구 규칙을 따른다.
 */
object LogClassifier {

    enum class Kind { AUTH, TC, REG, DEREG, NONE }

    /** @param success 성공(_S) 칸이면 true */
    @JvmRecord
    data class Result(val kind: Kind, val success: Boolean)

    private val MAPPER = ObjectMapper()

    /** 풀지 못한 로그면 비어 있다(세지 않는다). */
    @JvmStatic
    fun classify(jsondata: String?): Optional<Result> {
        // 한 번만 파싱한다 — Auth 판정은 parse 가 읽은 루트를 그대로 쓴다.
        // 루트가 없다 = 풀지 못했다(payload.json 도 null). parseWithRoot 는 둘을 함께 채운다.
        val parsed = FidoLogPayload.parseWithRoot(jsondata)
        val root = parsed.root ?: return Optional.empty()
        val p = parsed.payload
        val op = p.op
        val kind = when (op) {
            "TC" -> Kind.TC
            "Auth" -> if (requestHasTransaction(root)) Kind.TC else Kind.AUTH
            "Reg" -> Kind.REG
            "DeReg" -> Kind.DEREG
            else -> Kind.NONE
        }
        return Optional.of(Result(kind, "Success" == p.status))
    }

    /**
     * `transaction.request.requestData`(없으면 최상위 `request.requestData`)에 "transaction" 이 들어 있는가.
     * 값이 객체면 JSON 문자열로 보고 찾는다.
     */
    @JvmStatic
    internal fun requestHasTransaction(json: String?): Boolean {
        val root: JsonNode = try {
            MAPPER.readTree(json)
        } catch (e: Exception) {
            return false
        } ?: return false
        return requestHasTransaction(root)
    }

    /** [requestHasTransaction] 의 이미 읽은 루트 판. 같은 JSON 을 두 번 파싱하지 않으려고 둔다. */
    @JvmStatic
    internal fun requestHasTransaction(root: JsonNode): Boolean {
        for (base in arrayOf(root.path("transaction"), root)) {
            val data = base.path("request").path("requestData")
            if (data.isMissingNode || data.isNull) continue
            val text = if (data.isValueNode) data.asText() else data.toString()
            return text.contains("transaction")
        }
        return false
    }
}
