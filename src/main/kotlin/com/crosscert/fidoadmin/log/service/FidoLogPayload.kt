package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.javaIsBlank
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.regex.Pattern

/**
 * FIDO 로그 JSONDATA 를 화면이 쓰는 값으로 푼 것.
 *
 * 운영 FIDO 서버는 JSONDATA 에 **base64url 로 인코딩한** 로그 JSON 을 넣는다.
 * 값은 `transaction` 객체 안에 있다 —
 * `serviceName`, `userName`, `op`(Reg/Auth/DeReg/TC), `bioType`, `status`.
 * 요청 IP·UserAgent·로깅시간은 바깥 객체의 `accessIp`·`userAgent`·`logtime` 이다.
 * 로컬 시드·더미는 평문 JSON 에 키가 최상위라 둘 다 받는다.
 *
 * 운영 샘플로 확인하지 못한 키도 있다. **운영 키가 다르면 아래 키 목록만 고치면 된다** — SQL 과 화면은 이 클래스의 결과만 본다.
 * 어떤 값이 와도 예외를 던지지 않는다. 풀지 못하면 모든 값이 null 이다.
 *
 * @param json 디코드한 JSON 원문. 풀지 못했으면 null(상세 화면은 그때 원문을 그대로 보여 준다)
 */
@JvmRecord
data class FidoLogPayload(
    val op: String?, val serviceName: String?, val userName: String?, val bioType: Long?, val status: String?,
    val accessIp: String?, val userAgent: String?, val logtime: String?, val tcRequest: Boolean, val json: String?,
) {

    fun bioTypeLabel(): String = bioTypeLabel(bioType)

    /**
     * 사용자 이름에서 첫 `_*` 뒤 서비스 꼬리를 뗀 앞부분. 꼬리가 없으면 이름 그대로.
     * 예: `113057331000001_0_*com.kbstar.kbbiz_*KF` → `113057331000001_0`.
     */
    fun userHead(): String? {
        if (userName == null) return null
        val at = userName.indexOf(USER_SEPARATOR)
        return if (at < 0) userName else userName.substring(0, at)
    }

    /** FIDO 종류. 사용자 이름이 `_*KF` 로 끝나면 KFIDO, 아니면 FIDO. 이름이 없으면 null. */
    fun fidoKind(): String? {
        if (userName == null) return null
        return if (userName.endsWith(KFIDO_SUFFIX)) "KFIDO" else "FIDO"
    }

    /** 타입. 구분(op)에 TC 판정을 더한 값이다 — Auth 인데 요청에 transaction 이 있으면 TC. */
    fun type(): String? {
        if (op != null && op.equals("Auth", ignoreCase = true) && tcRequest) return "TC"
        return op
    }

    companion object {
        private val MAPPER = ObjectMapper()
        /** base64 안의 줄바꿈·공백. 행마다 호출되므로 한 번만 컴파일한다. */
        private val WHITESPACE: Pattern = Pattern.compile("\\s")
        private val EMPTY = FidoLogPayload(null, null, null, null, null, null, null, null, false, null)

        private val OP_KEYS = arrayOf("op")
        private val SERVICE_KEYS = arrayOf("serviceName", "servicename")
        private val USER_KEYS = arrayOf("userName", "userid", "userId")
        private val BIO_KEYS = arrayOf("bioType")
        private val STATUS_KEYS = arrayOf("status")
        private val IP_KEYS = arrayOf("accessIp", "accessIP")
        private val UA_KEYS = arrayOf("userAgent")
        private val LOGTIME_KEYS = arrayOf("logtime", "logTime")

        /** 사용자 이름의 `_*` 뒤는 서비스 구분이고, `_*KF` 로 끝나면 KFIDO 다. */
        private const val USER_SEPARATOR = "_*"
        private const val KFIDO_SUFFIX = "_*KF"

        @JvmStatic
        fun parse(jsondata: String?): FidoLogPayload {
            if (jsondata == null || jsondata.javaIsBlank()) return EMPTY
            val text = jsondata.javaTrim()
            val json = (if (text.startsWith("{")) text else decodeBase64(text)) ?: return EMPTY
            val root: JsonNode = try {
                MAPPER.readTree(json)
            } catch (e: Exception) {
                return EMPTY
            } ?: return EMPTY
            if (!root.isObject) return EMPTY
            val tx: JsonNode? = if (root.path("transaction").isObject) root.get("transaction") else null
            return FidoLogPayload(
                text(tx, root, OP_KEYS),
                text(tx, root, SERVICE_KEYS),
                text(tx, root, USER_KEYS),
                number(text(tx, root, BIO_KEYS)),
                text(tx, root, STATUS_KEYS),
                text(tx, root, IP_KEYS),
                text(tx, root, UA_KEYS),
                text(tx, root, LOGTIME_KEYS),
                // Auth 인데 요청에 transaction 이 들어 있으면 TC(전자서명 확인)로 센다.
                // 요청 키 이름을 확인하지 못해 transaction 객체 안 어디든 transaction 필드가 있으면 그렇게 본다.
                tx != null && tx.findValue("transaction") != null,
                json)
        }

        /** 인증장치 코드표. 모르는 코드와 없는 값은 "알수없음". */
        @JvmStatic
        fun bioTypeLabel(bioType: Long?): String {
            if (bioType == null) return "알수없음"
            return when (bioType.toInt()) {
                1 -> "PRESENCE"
                2 -> "지문"
                4 -> "PIN"
                8 -> "음성"
                16 -> "얼굴인식"
                32 -> "지역기반"
                64 -> "홍채"
                128 -> "패턴"
                256 -> "손바닥"
                512 -> "없음"
                1024 -> "ALL"
                else -> "알수없음"
            }
        }

        /**
         * 인증장치 아이콘(Bootstrap Icons). AppID 장치 칸의 AOS·iOS 로고처럼 글자 앞에 붙인다.
         * 손바닥은 맞는 아이콘이 없어 손 모양(hand-index)을 쓴다.
         */
        @JvmStatic
        fun bioIcon(bioType: Long?): String {
            if (bioType == null) return "bi-question-circle"
            return when (bioType.toInt()) {
                1 -> "bi-hand-index-thumb"
                2 -> "bi-fingerprint"
                4 -> "bi-123"
                8 -> "bi-mic"
                16 -> "bi-person-bounding-box"
                32 -> "bi-geo-alt"
                64 -> "bi-eye"
                128 -> "bi-grid-3x3-gap"
                256 -> "bi-hand-index"
                512 -> "bi-dash-circle"
                1024 -> "bi-collection"
                else -> "bi-question-circle"
            }
        }

        /**
         * 인증장치 아이콘 색(CSS 클래스). 사람이 고르는 인증 수단(지문·얼굴인식·PIN·패턴·홍채·음성·손바닥)은 값마다
         * 다른 색이고, PRESENCE·지역기반·없음·ALL·알수없음은 회색이다.
         */
        @JvmStatic
        fun bioColor(bioType: Long?): String {
            if (bioType == null) return "text-secondary"
            return when (bioType.toInt()) {
                2 -> "fa-bio-finger"
                4 -> "fa-bio-pin"
                8 -> "fa-bio-voice"
                16 -> "fa-bio-face"
                64 -> "fa-bio-iris"
                128 -> "fa-bio-pattern"
                256 -> "fa-bio-palm"
                else -> "text-secondary"
            }
        }

        /**
         * 구분 배지 색(CSS 클래스). 등록·인증·해지·TC 를 한눈에 가르도록 값마다 다른 색이다.
         * 모르는 값은 회색. 대소문자는 가리지 않는다.
         */
        @JvmStatic
        fun opBadge(op: String?): String {
            if (op == null) return "text-bg-secondary"
            return when (op.javaTrim().lowercase(Locale.ROOT)) {
                "reg" -> "fa-badge-reg"
                "auth" -> "fa-badge-auth"
                "dereg" -> "fa-badge-dereg"
                "tc" -> "fa-badge-tc"
                else -> "text-bg-secondary"
            }
        }

        /**
         * 상태 배지 색(CSS 클래스). 완료(Success) 초록, 실패(Error) 빨강, 대기(Wait) 노랑,
         * 진행 중(RequestOK·ResponseOK) 파랑. 모르는 값은 회색. 대소문자는 가리지 않는다.
         */
        @JvmStatic
        fun statusBadge(status: String?): String {
            if (status == null) return "text-bg-secondary"
            return when (status.javaTrim().lowercase(Locale.ROOT)) {
                "success" -> "text-bg-success"
                "error" -> "text-bg-danger"
                "wait" -> "text-bg-warning"
                "requestok", "responseok" -> "fa-badge-progress"
                else -> "text-bg-secondary"
            }
        }

        /** base64url·표준 base64·패딩 유무·줄바꿈을 모두 받는다. 풀리지 않거나 UTF-8 이 아니면 null. */
        private fun decodeBase64(text: String): String? {
            val normalized = WHITESPACE.matcher(text).replaceAll("").replace('+', '-').replace('/', '_').replace("=", "")
            return try {
                String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /** transaction 안을 먼저, 없으면 최상위를 본다. 키마다 앞의 이름이 우선이다. */
        private fun text(tx: JsonNode?, root: JsonNode, keys: Array<String>): String? {
            for (node in arrayOf(tx, root)) {
                if (node == null) continue
                for (key in keys) {
                    val v = node.get(key)
                    if (v != null && v.isValueNode && !v.isNull && !v.asText().javaIsBlank()) return v.asText()
                }
            }
            return null
        }

        private fun number(value: String?): Long? {
            if (value == null) return null
            return try {
                java.lang.Long.valueOf(value.javaTrim())
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

