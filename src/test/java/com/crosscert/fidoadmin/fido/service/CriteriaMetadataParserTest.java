package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CriteriaMetadataParserTest {

    CriteriaMetadataParser parser = new CriteriaMetadataParser();

    static final String FULL = """
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
        """;

    @Test void mapsEveryColumn() throws Exception {
        Criteria c = parser.parse(FULL);

        assertThat(c.getIdx()).isNull();
        assertThat(c.getAaid()).isEqualTo("0012#0001");
        assertThat(c.getVendorids()).isEqualTo("0012");
        assertThat(c.getUserverification()).isEqualTo(6L); // 2 | 4 | 2
        assertThat(c.getKeyprotection()).isEqualTo(6L);
        assertThat(c.getMatcherprotection()).isEqualTo(2L);
        assertThat(c.getAttachmenthnumber()).isEqualTo(1L);
        assertThat(c.getTcdisplay()).isEqualTo(3L);
        assertThat(c.getTcdisplaycontenttype()).isEqualTo("image/png");
        assertThat(c.getAuthenticationalgorithms()).isEqualTo("1");
        assertThat(c.getAssertionschemes()).isEqualTo("UAFV1TLV");
        assertThat(c.getAttestationtypes()).isEqualTo("15879,15880");
        assertThat(c.getAuthenticatorversion()).isEqualTo(2L);
        assertThat(c.getJsondata()).isEqualTo(FULL);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(FULL.getBytes(StandardCharsets.UTF_8));
        assertThat(c.getMetahash()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        assertThat(c.getCreatetime()).isNull();
    }

    @Test void pluralAlgorithmsWinOverSingular() {
        Criteria c = parser.parse("""
            {"aaid":"0012#0002","authenticationAlgorithm":1,"authenticationAlgorithms":[1,7]}""");
        assertThat(c.getAuthenticationalgorithms()).isEqualTo("1,7");
    }

    @Test void missingOptionalFieldsBecomeNullOrDdlDefaults() {
        Criteria c = parser.parse("{\"aaid\":\"ABCD#0001\"}");
        assertThat(c.getUserverification()).isNull();
        assertThat(c.getKeyprotection()).isNull();
        assertThat(c.getAuthenticationalgorithms()).isNull();
        assertThat(c.getAttestationtypes()).isNull();
        assertThat(c.getTcdisplaycontenttype()).isEqualTo("text/plain");
        assertThat(c.getAssertionschemes()).isEqualTo("UAFV1TLV");
    }

    @Test void nullAlgorithmStaysNull() {
        assertThat(parser.parse("{\"aaid\":\"0012#0003\",\"authenticationAlgorithm\":null}").getAuthenticationalgorithms()).isNull();
    }

    @Test void aaidWithoutHashHasNoVendor() {
        assertThat(parser.parse("{\"aaid\":\"NOHASH\"}").getVendorids()).isNull();
    }

    @Test void aaidIsTrimmed() {
        assertThat(parser.parse("{\"aaid\":\"  0012#0001 \"}").getAaid()).isEqualTo("0012#0001");
    }

    @Test void brokenJsonIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID);
    }

    @Test void topLevelArrayIsFormatError() {
        assertThatThrownBy(() -> parser.parse("[{\"aaid\":\"0012#0001\"}]"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID);
    }

    @Test void missingOrBlankAaidIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"description\":\"x\"}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"   \"}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":12}"))
            .isInstanceOf(CriteriaMetadataException.class).hasMessage(CriteriaMetadataParser.INVALID);
    }

    /** FIDO2/MDS3 메타데이터는 keyProtection 이 문자열 배열이다. 500 이 아니라 형식 오류여야 한다. */
    @Test void fido2StyleArrayFieldIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"0012#0001\",\"keyProtection\":[\"hardware\"]}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID + " (keyProtection)");
    }

    @Test void fractionalNumberIsFormatError() {
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"0012#0001\",\"tcDisplay\":1.5}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage(CriteriaMetadataParser.INVALID + " (tcDisplay)");
    }

    @Test void overlongColumnIsRejectedWithFieldAndLimit() {
        String aaid = "A".repeat(65);
        assertThatThrownBy(() -> parser.parse("{\"aaid\":\"" + aaid + "\"}"))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage("aaid 는 64바이트를 넘을 수 없습니다.");
    }

    @Test void nullOrBlankInputIsFormatError() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(CriteriaMetadataException.class);
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(CriteriaMetadataException.class);
    }
}
