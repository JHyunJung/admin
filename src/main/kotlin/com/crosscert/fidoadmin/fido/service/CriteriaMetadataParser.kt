package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.common.isNullOrJavaBlank
import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import org.springframework.stereotype.Component

/**
 * FIDO UAF 메타데이터 문(Metadata Statement) JSON → CRITERIA 행.
 *
 * 메타데이터 JSON 을 검증해 CRITERIA 컬럼 값으로 푼다. 숫자 자리에 다른 형이 오면
 * (FIDO2/MDS3 메타데이터처럼 keyProtection 이 문자열 배열인 경우) 저장하지 않고 형식 오류로 돌려보낸다.
 * METAHASH 는 원문 UTF-8 바이트의 SHA-256 을 base64url(패딩 없음)로 둔다 — MDS 관례다.
 * 해시 방식은 FIDO 서버와 맞춰 확인하지 못했다.
 */
@Component
class CriteriaMetadataParser {

    fun parse(json: String?): Criteria {
        if (json == null || json.isNullOrJavaBlank()) throw CriteriaMetadataException(INVALID)
        val root: JsonNode? = try {
            READER.readTree(json)
        } catch (e: JsonProcessingException) {
            throw CriteriaMetadataException(INVALID)
        }
        if (root == null || !root.isObject) throw CriteriaMetadataException(INVALID)

        val aaidNode = root.get("aaid")
        if (aaidNode == null || !aaidNode.isTextual || aaidNode.asText().isNullOrJavaBlank()) {
            throw CriteriaMetadataException(INVALID)
        }
        // Java String.trim() 과 같은 기준(U+0020 이하 제거). Kotlin trim() 은 유니코드 공백 기준이라 다르다.
        val aaid = aaidNode.asText().javaTrim()
        // AAID 는 "VVVV#MMMM" 형식이다. 안쪽 개행·제어문자는 로그·감사 메시지에 가짜 줄을 만든다.
        if (CONTROL.containsMatchIn(aaid)) throw CriteriaMetadataException("$INVALID (aaid)")
        val hash = aaid.indexOf('#')

        val c = Criteria()
        c.aaid = limit("aaid", aaid, 64)
        c.vendorids = if (hash < 0) null else limit("vendorId", aaid.substring(0, hash), 32)
        c.userverification = userVerification(root.get("userVerificationDetails"))
        c.keyprotection = number(root, "keyProtection")
        c.matcherprotection = number(root, "matcherProtection")
        c.attachmenthnumber = number(root, "attachmentHint")
        c.tcdisplay = number(root, "tcDisplay")
        c.tcdisplaycontenttype = limit("tcDisplayContentType", text(root, "tcDisplayContentType", "text/plain"), 128)
        val singleAlgorithm = number(root, "authenticationAlgorithm")
        val algorithms = if (root.has("authenticationAlgorithms")) {
            joinNumbers(root, "authenticationAlgorithms")
        } else {
            singleAlgorithm?.toString()
        }
        c.authenticationalgorithms = limit("authenticationAlgorithms", algorithms, 64)
        c.assertionschemes = limit("assertionScheme", text(root, "assertionScheme", "UAFV1TLV"), 64)
        c.attestationtypes = limit("attestationTypes", joinNumbers(root, "attestationTypes"), 64)
        c.authenticatorversion = number(root, "authenticatorVersion")
        c.jsondata = json
        c.metahash = sha256(json)
        return c
    }

    companion object {
        const val INVALID = "유효하지 않은 metadata 형식입니다."
        private val CONTROL = Regex("[\\p{Cc}\\u2028\\u2029]")

        /** 첫 JSON 값 뒤에 남은 글자가 있으면 실패시킨다. 끄면 두 문을 붙여 넣었을 때 첫 문만 읽고 원문 전체를 저장한다. */
        private val READER: ObjectReader = ObjectMapper().reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

        /** userVerificationDetails 는 [[{userVerification: n}, ...], ...]. 모든 n 을 비트 OR 한다. */
        private fun userVerification(details: JsonNode?): Long? {
            if (details == null || details.isNull) return null
            if (!details.isArray) throw invalid("userVerificationDetails")
            var bits = 0L
            var any = false
            for (combination in details) {
                if (!combination.isArray) throw invalid("userVerificationDetails")
                for (descriptor in combination) {
                    val uv = descriptor.get("userVerification")
                    if (uv == null || !uv.canConvertToExactIntegral()) throw invalid("userVerificationDetails")
                    bits = bits or uv.asLong()
                    any = true
                }
            }
            return if (any) bits else null
        }

        private fun number(root: JsonNode, field: String): Long? {
            val n = root.get(field)
            if (n == null || n.isNull) return null
            if (!n.isNumber || !n.canConvertToExactIntegral()) throw invalid(field)
            return n.asLong()
        }

        private fun text(root: JsonNode, field: String, fallback: String): String {
            val n = root.get(field)
            if (n == null || n.isNull) return fallback
            if (!n.isTextual) throw invalid(field)
            return n.asText()
        }

        private fun joinNumbers(root: JsonNode, field: String): String? {
            val n = root.get(field)
            if (n == null || n.isNull) return null
            if (!n.isArray) throw invalid(field)
            val parts = ArrayList<String>()
            for (item in n) {
                if (!item.isNumber || !item.canConvertToExactIntegral()) throw invalid(field)
                parts.add(item.asLong().toString())
            }
            return if (parts.isEmpty()) null else parts.joinToString(",")
        }

        private fun limit(field: String, value: String?, maxBytes: Int): String? {
            if (value != null && value.toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
                throw CriteriaMetadataException("$field 는 ${maxBytes}바이트를 넘을 수 없습니다.")
            }
            return value
        }

        private fun invalid(field: String): CriteriaMetadataException =
            CriteriaMetadataException("$INVALID ($field)")

        private fun sha256(json: String): String {
            try {
                val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(StandardCharsets.UTF_8))
                return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException(e) // JDK 에 SHA-256 은 항상 있다
            }
        }
    }
}
