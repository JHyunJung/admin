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

    /** 요청 IP·UserAgent·로깅시간은 transaction 바깥(FIDOLog 최상위)에 있다. */
    @Test void readsAccessIpUserAgentAndLogtime() {
        FidoLogPayload p = FidoLogPayload.parse(base64url("""
            {"transaction":{"userName":"u1","op":"Auth"},"logtime":"2026-10-02 14:42:20",\
            "accessIp":"10.0.0.7","userAgent":"Mozilla/5.0 (Linux; Android 14)"}"""));

        assertThat(p.accessIp()).isEqualTo("10.0.0.7");
        assertThat(p.userAgent()).isEqualTo("Mozilla/5.0 (Linux; Android 14)");
        assertThat(p.logtime()).isEqualTo("2026-10-02 14:42:20");
    }

    /** 이전 어드민: 사용자 이름의 첫 _* 뒤는 서비스 꼬리이고, _*KF 로 끝나면 KFIDO 다. 값은 운영 화면에서 본 형식. */
    @Test void splitsUserNameIntoHeadAndFidoKind() {
        FidoLogPayload kf = FidoLogPayload.parse(
            "{\"transaction\":{\"userName\":\"113057331000001_0_*com.kbstar.kbbiz_*KF\"}}");
        assertThat(kf.userName()).isEqualTo("113057331000001_0_*com.kbstar.kbbiz_*KF");
        assertThat(kf.userHead()).isEqualTo("113057331000001_0");
        assertThat(kf.fidoKind()).isEqualTo("KFIDO");

        FidoLogPayload tail = FidoLogPayload.parse("{\"transaction\":{\"userName\":\"4536345_aju.ac.kr/AJU_*AJU\"}}");
        assertThat(tail.userHead()).isEqualTo("4536345_aju.ac.kr/AJU");
        assertThat(tail.fidoKind()).isEqualTo("FIDO");

        FidoLogPayload plain = FidoLogPayload.parse("{\"transaction\":{\"userName\":\"f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR\"}}");
        assertThat(plain.userHead()).isEqualTo("f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR");
        assertThat(plain.fidoKind()).isEqualTo("FIDO");

        FidoLogPayload none = FidoLogPayload.parse("{\"transaction\":{\"op\":\"Reg\"}}");
        assertThat(none.userHead()).isNull();
        assertThat(none.fidoKind()).isNull();
    }

    /** 이전 어드민: Auth 인데 요청에 transaction 이 있으면 TC 로 센다. 그 밖에는 구분 그대로다. */
    @Test void typeIsTcWhenAuthRequestCarriesTransaction() {
        assertThat(FidoLogPayload.parse(
            "{\"transaction\":{\"op\":\"Auth\",\"request\":{\"transaction\":[{\"content\":\"x\"}]}}}").type())
            .isEqualTo("TC");
        assertThat(FidoLogPayload.parse("{\"transaction\":{\"op\":\"Auth\",\"request\":{}}}").type()).isEqualTo("Auth");
        assertThat(FidoLogPayload.parse("{\"transaction\":{\"op\":\"Reg\"}}").type()).isEqualTo("Reg");
        assertThat(FidoLogPayload.parse("{\"op\":\"TC\"}").type()).isEqualTo("TC");
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

    /** 구분·상태는 값이 정해져 있어 값마다 배지 색이 다르다. 대소문자는 가리지 않고, 모르는 값은 회색. */
    @Test void badgeClassesPerOpAndStatus() {
        assertThat(FidoLogPayload.opBadge("Reg")).isEqualTo("fa-badge-reg");
        assertThat(FidoLogPayload.opBadge("auth")).isEqualTo("fa-badge-auth");
        assertThat(FidoLogPayload.opBadge("DeReg")).isEqualTo("fa-badge-dereg");
        assertThat(FidoLogPayload.opBadge("TC")).isEqualTo("fa-badge-tc");
        assertThat(FidoLogPayload.opBadge("Other")).isEqualTo("text-bg-secondary");
        assertThat(FidoLogPayload.opBadge(null)).isEqualTo("text-bg-secondary");

        assertThat(FidoLogPayload.statusBadge("Success")).isEqualTo("text-bg-success");
        assertThat(FidoLogPayload.statusBadge("ERROR")).isEqualTo("text-bg-danger");
        assertThat(FidoLogPayload.statusBadge("Wait")).isEqualTo("text-bg-warning");
        assertThat(FidoLogPayload.statusBadge("RequestOK")).isEqualTo("fa-badge-progress");
        assertThat(FidoLogPayload.statusBadge("ResponseOK")).isEqualTo("fa-badge-progress");
        assertThat(FidoLogPayload.statusBadge("Other")).isEqualTo("text-bg-secondary");
        assertThat(FidoLogPayload.statusBadge(null)).isEqualTo("text-bg-secondary");

        assertThat(FidoLogPayload.bioBadge(2L)).isEqualTo("fa-bio-finger");
        assertThat(FidoLogPayload.bioBadge(16L)).isEqualTo("fa-bio-face");
        assertThat(FidoLogPayload.bioBadge(4L)).isEqualTo("fa-bio-pin");
        assertThat(FidoLogPayload.bioBadge(128L)).isEqualTo("fa-bio-pattern");
        assertThat(FidoLogPayload.bioBadge(64L)).isEqualTo("fa-bio-iris");
        assertThat(FidoLogPayload.bioBadge(8L)).isEqualTo("fa-bio-voice");
        assertThat(FidoLogPayload.bioBadge(256L)).isEqualTo("fa-bio-palm");
        assertThat(FidoLogPayload.bioBadge(512L)).isEqualTo("text-bg-secondary");
        assertThat(FidoLogPayload.bioBadge(null)).isEqualTo("text-bg-secondary");
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
