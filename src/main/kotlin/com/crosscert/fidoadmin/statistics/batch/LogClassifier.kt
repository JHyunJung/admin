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
        val p = FidoLogPayload.parse(jsondata)
        val json = p.json ?: return Optional.empty()
        val op = p.op
        val kind = when (op) {
            "TC" -> Kind.TC
            "Auth" -> if (requestHasTransaction(json)) Kind.TC else Kind.AUTH
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
        for (base in arrayOf(root.path("transaction"), root)) {
            val data = base.path("request").path("requestData")
            if (data.isMissingNode || data.isNull) continue
            val text = if (data.isValueNode) data.asText() else data.toString()
            return text.contains("transaction")
        }
        return false
    }
}
