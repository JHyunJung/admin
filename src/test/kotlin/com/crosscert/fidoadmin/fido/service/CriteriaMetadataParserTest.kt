package com.crosscert.fidoadmin.fido.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CriteriaMetadataParserTest {

    val parser = CriteriaMetadataParser()

    companion object {
        /** Java 텍스트 블록과 같은 값(공통 들여쓰기 제거, 끝 줄바꿈 포함). */
        val FULL = """
            {
              "aaid": "0012#0001",
              "description": "Sample fingerprint",
              "authenticatorVersion": 2,
              "assertionScheme": "UAFV1TLV",
              "authenticationAlgorithm": 1,
              "attestationTypes": [15879, 15880],
              "userVerificationDetails": [[{"userVerification": 2}], [{"userVerification": 4}, {"userVerification": 2}]],
              "keyProtection": 6,
              "matcherProtection": 2,
              "attachmentHint": 1,
              "tcDisplay": 3,
              "tcDisplayContentType": "image/png"
            }
            """.trimIndent() + "\n"
    }

    @Test fun mapsEveryColumn() {
        val c = parser.parse(FULL)

        assertThat(c.idx).isNull()
        assertThat(c.aaid).isEqualTo("0012#0001")
        assertThat(c.vendorids).isEqualTo("0012")
        assertThat(c.userverification).isEqualTo(6L) // 2 | 4 | 2
        assertThat(c.keyprotection).isEqualTo(6L)
        assertThat(c.matcherprotection).isEqualTo(2L)
        assertThat(c.attachmenthnumber).isEqualTo(1L)
        assertThat(c.tcdisplay).isEqualTo(3L)
        assertThat(c.tcdisplaycontenttype).isEqualTo("image/png")
        assertThat(c.authenticationalgorithms).isEqualTo("1")
        assertThat(c.assertionschemes).isEqualTo("UAFV1TLV")
        assertThat(c.attestationtypes).isEqualTo("15879,15880")
        assertThat(c.authenticatorversion).isEqualTo(2L)
        assertThat(c.jsondata).isEqualTo(FULL)
        val digest = MessageDigest.getInstance("SHA-256").digest(FULL.toByteArray(StandardCharsets.UTF_8))
        assertThat(c.metahash).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
        assertThat(c.createtime).isNull()
    }

    @Test fun pluralAlgorithmsWinOverSingular() {
        val c = parser.parse("""{"aaid":"0012#0002","authenticationAlgorithm":1,"authenticationAlgorithms":[1,7]}""")
        assertThat(c.authenticationalgorithms).isEqualTo("1,7")
    }

    @Test fun missingOptionalFieldsBecomeNullOrDdlDefaults() {
        val c = parser.parse("{\"aaid\":\"ABCD#0001\"}")
        assertThat(c.userverification).isNull()
        assertThat(c.keyprotection).isNull()
        assertThat(c.authenticationalgorithms).isNull()
        assertThat(c.attestationtypes).isNull()
        assertThat(c.tcdisplaycontenttype).isEqualTo("text/plain")
        assertThat(c.assertionschemes).isEqualTo("UAFV1TLV")
    }

    @Test fun nullAlgorithmStaysNull() {
        assertThat(parser.parse("{\"aaid\":\"0012#0003\",\"authenticationAlgorithm\":null}").authenticationalgorithms).isNull()
    }

    @Test fun aaidWithoutHashHasNoVendor() {
        assertThat(parser.parse("{\"aaid\":\"NOHASH\"}").vendorids).isNull()
    }

    @Test fun aaidIsTrimmed() {
        assertThat(parser.parse("{\"aaid\":\"  0012#0001 \"}").aaid).isEqualTo("0012#0001")
    }

    @Test fun brokenJsonIsFormatError() {
        assertThatThrownBy { parser.parse("{\"aaid\":") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID)
    }

    @Test fun topLevelArrayIsFormatError() {
        assertThatThrownBy { parser.parse("[{\"aaid\":\"0012#0001\"}]") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID)
    }

    @Test fun missingOrBlankAaidIsFormatError() {
        assertThatThrownBy { parser.parse("{\"description\":\"x\"}") }
            .isInstanceOf(CriteriaMetadataException::class.java).hasMessage(CriteriaMetadataParser.INVALID)
        assertThatThrownBy { parser.parse("{\"aaid\":\"   \"}") }
            .isInstanceOf(CriteriaMetadataException::class.java).hasMessage(CriteriaMetadataParser.INVALID)
        assertThatThrownBy { parser.parse("{\"aaid\":12}") }
            .isInstanceOf(CriteriaMetadataException::class.java).hasMessage(CriteriaMetadataParser.INVALID)
    }

    /** FIDO2/MDS3 메타데이터는 keyProtection 이 문자열 배열이다. 500 이 아니라 형식 오류여야 한다. */
    @Test fun fido2StyleArrayFieldIsFormatError() {
        assertThatThrownBy { parser.parse("{\"aaid\":\"0012#0001\",\"keyProtection\":[\"hardware\"]}") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID + " (keyProtection)")
    }

    @Test fun fractionalNumberIsFormatError() {
        assertThatThrownBy { parser.parse("{\"aaid\":\"0012#0001\",\"tcDisplay\":1.5}") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID + " (tcDisplay)")
    }

    @Test fun overlongColumnIsRejectedWithFieldAndLimit() {
        val aaid = "A".repeat(65)
        assertThatThrownBy { parser.parse("{\"aaid\":\"$aaid\"}") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage("aaid 는 64바이트를 넘을 수 없습니다.")
    }

    @Test fun nullOrBlankInputIsFormatError() {
        assertThatThrownBy { parser.parse(null) }.isInstanceOf(CriteriaMetadataException::class.java)
        assertThatThrownBy { parser.parse("  ") }.isInstanceOf(CriteriaMetadataException::class.java)
    }

    /** 두 문을 한꺼번에 붙여 넣으면 첫 문만 등록되고 나머지는 조용히 버려졌다. 원문 전체가 JSON 하나여야 한다. */
    @Test fun twoConcatenatedStatementsAreFormatError() {
        assertThatThrownBy { parser.parse("{\"aaid\":\"0012#0001\"}, {\"aaid\":\"0012#0002\"}") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID)
    }

    @Test fun trailingTextIsFormatError() {
        assertThatThrownBy { parser.parse("{\"aaid\":\"0012#0001\"} trailing") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID)
    }
}
