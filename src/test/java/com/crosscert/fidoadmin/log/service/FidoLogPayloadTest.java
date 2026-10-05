package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FidoLogPayloadTest {

    static final String LEGACY = """
        {"transaction":{"serviceName":"com.kbstar.kbbank","userName":"f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR",\
        "op":"TC","bioType":2,"status":"Success"},"hash":"abc","logtime":"2026-10-02 14:42:20"}""";

    static String base64url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** 운영 FIDO 서버는 JSONDATA 를 base64url 로 넣는다(이전 어드민 parseLog). */
    @Test void decodesBase64UrlLegacyLog() {
        FidoLogPayload p = FidoLogPayload.parse(base64url(LEGACY));

        assertThat(p.op()).isEqualTo("TC");
        assertThat(p.serviceName()).isEqualTo("com.kbstar.kbbank");
        assertThat(p.userName()).isEqualTo("f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR");
        assertThat(p.bioType()).isEqualTo(2L);
        assertThat(p.status()).isEqualTo("Success");
        assertThat(p.json()).isEqualTo(LEGACY);
    }

    @Test void acceptsPaddedAndStandardAlphabetBase64() {
        String standard = Base64.getEncoder().encodeToString(LEGACY.getBytes(StandardCharsets.UTF_8));
        assertThat(FidoLogPayload.parse(standard).op()).isEqualTo("TC");
        assertThat(FidoLogPayload.parse(standard + "\n").op()).isEqualTo("TC");
    }

    /** 로컬 시드·더미는 평문 JSON 이고 키가 최상위에 있다. */
    @Test void readsPlainJsonWithTopLevelKeys() {
        FidoLogPayload p = FidoLogPayload.parse(
            "{\"op\":\"Auth\",\"userid\":\"user131\",\"serviceName\":\"kbstar\",\"result\":\"1491\"}");

        assertThat(p.op()).isEqualTo("Auth");
        assertThat(p.userName()).isEqualTo("user131");
        assertThat(p.serviceName()).isEqualTo("kbstar");
        assertThat(p.bioType()).isNull();
    }

    @Test void numericStringBioTypeIsAccepted() {
        assertThat(FidoLogPayload.parse("{\"transaction\":{\"bioType\":\"16\"}}").bioType()).isEqualTo(16L);
    }

    /** 어떤 값이 와도 화면이 깨지면 안 된다. 해석하지 못하면 빈 값이다. */
    @Test void garbageYieldsEmptyPayload() {
        for (String bad : new String[] {null, "", "   ", "not base64 !!", base64url("not json"), base64url("[1,2]"), "{broken"}) {
            FidoLogPayload p = FidoLogPayload.parse(bad);
            assertThat(p.op()).as(bad).isNull();
            assertThat(p.userName()).as(bad).isNull();
            assertThat(p.json()).as(bad).isNull();
        }
    }

    @Test void bioTypeLabelsMatchLegacyBioType() {
        assertThat(FidoLogPayload.bioTypeLabel(1L)).isEqualTo("PRESENCE");
        assertThat(FidoLogPayload.bioTypeLabel(2L)).isEqualTo("지문");
        assertThat(FidoLogPayload.bioTypeLabel(4L)).isEqualTo("PIN");
        assertThat(FidoLogPayload.bioTypeLabel(8L)).isEqualTo("음성");
        assertThat(FidoLogPayload.bioTypeLabel(16L)).isEqualTo("얼굴인식");
        assertThat(FidoLogPayload.bioTypeLabel(32L)).isEqualTo("지역기반");
        assertThat(FidoLogPayload.bioTypeLabel(64L)).isEqualTo("홍채");
        assertThat(FidoLogPayload.bioTypeLabel(128L)).isEqualTo("패턴");
        assertThat(FidoLogPayload.bioTypeLabel(256L)).isEqualTo("손바닥");
        assertThat(FidoLogPayload.bioTypeLabel(512L)).isEqualTo("없음");
        assertThat(FidoLogPayload.bioTypeLabel(1024L)).isEqualTo("ALL");
        assertThat(FidoLogPayload.bioTypeLabel(3L)).isEqualTo("알수없음");
        assertThat(FidoLogPayload.bioTypeLabel(null)).isEqualTo("알수없음");
    }
}
