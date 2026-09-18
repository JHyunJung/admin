package com.crosscert.fidoadmin.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.system.web.FidoSettingKey;
import com.crosscert.fidoadmin.system.web.FidoSettingKey.ValueStyle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FidoSettingKeyTest {

    /** PROP_KEY 는 VARCHAR2(128) 이고 BYTE 의미다. 키는 ASCII 뿐이라 길이=바이트다. */
    @Test void 키는_128바이트를_넘지_않는다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.key().getBytes(StandardCharsets.UTF_8).length)
                .as("%s 의 PROP_KEY 길이", k.name())
                .isLessThanOrEqualTo(128));
    }

    @Test void 키가_중복되지_않는다() {
        var keys = Arrays.stream(FidoSettingKey.values()).map(FidoSettingKey::key).collect(Collectors.toSet());
        assertThat(keys).hasSize(FidoSettingKey.values().length);
    }

    @Test void 키는_URL_안전한_문자만_쓴다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.key()).as("%s", k.name()).matches("[A-Za-z0-9._-]+"));
    }

    @Test void 세_섹션이_모두_존재한다() {
        assertThat(Arrays.stream(FidoSettingKey.values()).map(FidoSettingKey::section).distinct())
            .containsExactlyInAnyOrder("인증서 설정", "FIDO 부가기능 설정", "알림메일 설정");
    }

    @Test void 모든_키에_기본값이_있다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.defaultValue()).as("%s 의 기본값", k.name()).isNotNull());
    }

    /**
     * 운영 DB 에서 확인한 실제 키다. 하나라도 이름이 달라지면 저장한 값을 FIDO 서버가
     * 읽지 못한다 — 화면은 정상으로 보이는데 설정만 먹지 않는 상태가 되므로 이름을 고정한다.
     */
    @Test void 운영_DB_의_실제_키_이름을_쓴다() {
        var keys = Arrays.stream(FidoSettingKey.values()).map(FidoSettingKey::key).collect(Collectors.toSet());
        assertThat(keys).containsExactlyInAnyOrder(
            // 인증서 설정
            "CERT", "CERT_VERIFY", "CERT_SIGN_VERIFY",
            "CERT_P1", "CERT_P7", "CERT_P9", "CERT_PUBKEY", "CERT_PUBKEY_ALG", "AUTH_ALG",
            // FIDO 부가기능
            "USERNAME_ENC", "FIDO_DETAIL_LOG_DB_SAVE", "PROTOCOL_TV",
            "FIDO_ATTESTCERT_AAID_CHECK", "FIDO_METADATA_VALID", "FIDO_REREG_ENABLE",
            "CHALLENGE_TERM", "TC_ORIGIN_TERM",
            // 알림메일
            "SMTP", "SMTP_IP", "SMTP_PORT", "SMTP_SENDER",
            "SMTP_USERNAME", "SMTP_PASSWORD", "MAILING_TERM");
    }

    /**
     * 값 형식이 키마다 다르다. 운영 데이터에서 확인한 예외 둘을 고정한다 —
     * 대부분 ENABLE/DISABLE 인데 AAID 검증만 Y/N 이고 인증서 검증만 소문자 yes/no 다.
     */
    @Test void 값_형식의_예외_둘을_지킨다() {
        assertThat(FidoSettingKey.FIDO_ATTESTCERT_AAID_CHECK.style()).isEqualTo(ValueStyle.YES_NO);
        assertThat(FidoSettingKey.CERT_VERIFY.style()).isEqualTo(ValueStyle.YES_NO_LOWER);
        assertThat(FidoSettingKey.CERT.style()).isEqualTo(ValueStyle.ENABLE_DISABLE);
    }

    /** 토글의 기본값은 그 키의 형식에서 "끈 값" 또는 "켠 값" 중 하나여야 한다. */
    @Test void 토글_기본값이_자기_형식에_맞는다() {
        assertThat(FidoSettingKey.values())
            .filteredOn(k -> k.style() != ValueStyle.RAW)
            .allSatisfy(k -> assertThat(k.defaultValue())
                .as("%s 의 기본값", k.name())
                .isIn(k.style().on(), k.style().off()));
    }

    /** 화면이 boolean 으로 다루고 저장은 키별 문자열로 되돌린다. 왕복이 깨지면 값이 뒤집힌다. */
    @Test void 켬_끔_왕복이_일치한다() {
        for (ValueStyle s : ValueStyle.values()) {
            if (s == ValueStyle.RAW) continue;
            assertThat(s.isOn(s.on())).as("%s on", s).isTrue();
            assertThat(s.isOn(s.off())).as("%s off", s).isFalse();
        }
    }

    /** 운영 데이터의 실제 표기를 그대로 읽어야 한다(대소문자 혼용 포함). */
    @Test void 운영_표기를_읽는다() {
        assertThat(ValueStyle.ENABLE_DISABLE.isOn("ENABLE")).isTrue();
        assertThat(ValueStyle.ENABLE_DISABLE.isOn("DISABLE")).isFalse();
        assertThat(ValueStyle.YES_NO.isOn("Y")).isTrue();
        assertThat(ValueStyle.YES_NO.isOn("N")).isFalse();
        assertThat(ValueStyle.YES_NO_LOWER.isOn("yes")).isTrue();
        assertThat(ValueStyle.YES_NO_LOWER.isOn("no")).isFalse();
    }

    /** NULL 컬럼이 흔하다(SMTP_IP 등). null 은 꺼짐으로 읽어 NPE 를 내지 않는다. */
    @Test void null_은_꺼짐으로_읽는다() {
        assertThat(ValueStyle.ENABLE_DISABLE.isOn(null)).isFalse();
        assertThat(ValueStyle.YES_NO.isOn(null)).isFalse();
    }
}
