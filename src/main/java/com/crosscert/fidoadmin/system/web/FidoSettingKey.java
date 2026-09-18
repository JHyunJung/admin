package com.crosscert.fidoadmin.system.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * FIDO 서버 설정 화면이 다루는 고정 키 목록. 값은 {@code CCFA_SYSTEM_PROP} 에
 * {@code (PROP_KEY, COMPANY_IDX)} 로 저장된다 — 고객사마다 다를 수 있다.
 *
 * <p>키 이름과 값 표기는 <b>운영 DB 의 실제 행에서 확인한 것</b>이다. 스키마에는
 * 드러나지 않는다({@code CCFA_SYSTEM_PROP} 은 범용 key-value 테이블이다).
 * 이름을 바꾸면 화면은 그대로 동작하는데 FIDO 서버만 값을 못 읽는 상태가 되므로,
 * {@code FidoSettingKeyTest} 가 키 이름 전체를 고정해 둔다.
 *
 * <p>값 표기가 키마다 다르다. 대부분 {@code ENABLE}/{@code DISABLE} 인데
 * {@code FIDO_ATTESTCERT_AAID_CHECK} 만 {@code Y}/{@code N} 이고
 * {@code CERT_VERIFY} 만 소문자 {@code yes}/{@code no} 다. 운영 데이터가 그렇게
 * 저장되어 있어 통일하지 않는다 — 서버가 읽는 표기를 화면이 따라간다.
 *
 * <p>화면에 넣지 않은 키: {@code LICENSE}·{@code *_ROOT}(경로)·{@code OCSP_*}.
 * 이전 어드민의 시스템 관리 화면에도 없던 항목이라 범위 밖이다.
 */
public enum FidoSettingKey {

    // ---- 인증서 설정 ----
    CERT("CERT", "인증서 설정", "인증서 설정 사용", ValueStyle.ENABLE_DISABLE),
    CERT_VERIFY("CERT_VERIFY", "인증서 설정", "등록시 인증서 검증하기", ValueStyle.YES_NO_LOWER),
    CERT_SIGN_VERIFY("CERT_SIGN_VERIFY", "인증서 설정", "인증시 서명 검증하기", ValueStyle.ENABLE_DISABLE),
    CERT_P1("CERT_P1", "인증서 설정", "PKCS #1", ValueStyle.ENABLE_DISABLE),
    CERT_P7("CERT_P7", "인증서 설정", "PKCS #7", ValueStyle.ENABLE_DISABLE),
    CERT_P9("CERT_P9", "인증서 설정", "PKCS #7 + PKCS #9", ValueStyle.ENABLE_DISABLE),
    CERT_PUBKEY("CERT_PUBKEY", "인증서 설정", "공개키", ValueStyle.ENABLE_DISABLE),
    CERT_PUBKEY_ALG("CERT_PUBKEY_ALG", "인증서 설정", "공개키 알고리즘", ValueStyle.ENABLE_DISABLE),
    AUTH_ALG("AUTH_ALG", "인증서 설정", "Authenticator 알고리즘", ValueStyle.ENABLE_DISABLE),

    // ---- FIDO 부가기능 설정 ----
    USERNAME_ENC("USERNAME_ENC", "FIDO 부가기능 설정", "사용자 ID 암호화", ValueStyle.ENABLE_DISABLE),
    FIDO_DETAIL_LOG_DB_SAVE("FIDO_DETAIL_LOG_DB_SAVE", "FIDO 부가기능 설정", "상세로그 DB저장", ValueStyle.ENABLE_DISABLE),
    PROTOCOL_TV("PROTOCOL_TV", "FIDO 부가기능 설정", "트랜잭션 검증", ValueStyle.ENABLE_DISABLE),
    FIDO_ATTESTCERT_AAID_CHECK("FIDO_ATTESTCERT_AAID_CHECK", "FIDO 부가기능 설정", "ATTESTCERT AAID 검증", ValueStyle.YES_NO),
    FIDO_METADATA_VALID("FIDO_METADATA_VALID", "FIDO 부가기능 설정", "메타데이터 알고리즘 검증", ValueStyle.ENABLE_DISABLE),
    FIDO_REREG_ENABLE("FIDO_REREG_ENABLE", "FIDO 부가기능 설정", "강제 재 등록", ValueStyle.ENABLE_DISABLE),
    CHALLENGE_TERM("CHALLENGE_TERM", "FIDO 부가기능 설정", "Challenge 유효기간(초)", Type.NUMBER, "180"),
    TC_ORIGIN_TERM("TC_ORIGIN_TERM", "FIDO 부가기능 설정", "TC원문 저장 기간(일)", Type.NUMBER, "9999"),

    // ---- 알림메일 설정 ----
    SMTP("SMTP", "알림메일 설정", "알림메일 사용", ValueStyle.ENABLE_DISABLE),
    SMTP_IP("SMTP_IP", "알림메일 설정", "메일서버 IP", Type.TEXT, ""),
    SMTP_PORT("SMTP_PORT", "알림메일 설정", "메일서버 PORT", Type.NUMBER, "25"),
    SMTP_SENDER("SMTP_SENDER", "알림메일 설정", "발신자 주소", Type.TEXT, ""),
    SMTP_USERNAME("SMTP_USERNAME", "알림메일 설정", "사용자 ID", Type.TEXT, ""),
    SMTP_PASSWORD("SMTP_PASSWORD", "알림메일 설정", "사용자 PW", Type.PASSWORD, ""),
    MAILING_TERM("MAILING_TERM", "알림메일 설정", "발송주기(분)", Type.NUMBER, "5");

    /** 입력 형태. 템플릿이 이 값으로 위젯을 고른다. */
    public enum Type { TOGGLE, NUMBER, TEXT, PASSWORD }

    /**
     * 켬/끔을 어떤 문자열로 저장하는지. 운영 데이터의 표기를 그대로 따른다.
     *
     * <p>{@link #RAW} 는 토글이 아닌 값(숫자·문자열)이다. 변환하지 않는다.
     */
    public enum ValueStyle {
        ENABLE_DISABLE("ENABLE", "DISABLE"),
        YES_NO("Y", "N"),
        YES_NO_LOWER("yes", "no"),
        RAW(null, null);

        private final String on;
        private final String off;

        ValueStyle(String on, String off) {
            this.on = on;
            this.off = off;
        }

        public String on() { return on; }
        public String off() { return off; }

        /**
         * 저장된 값이 "켬" 인가. 대소문자는 무시한다 — 같은 키라도 고객사마다
         * 표기가 섞여 들어온 흔적이 운영 데이터에 있다.
         *
         * <p>{@code null} 은 꺼짐이다. 행이 없거나 PROP_VALUE 가 NULL 인 경우가 흔하다.
         */
        public boolean isOn(String value) {
            return on != null && on.equalsIgnoreCase(value);
        }

        /** 화면의 체크 여부 → 저장할 문자열. */
        public String of(boolean on) {
            return on ? this.on : this.off;
        }
    }

    private final String key;
    private final String section;
    private final String label;
    private final Type type;
    private final ValueStyle style;
    private final String defaultValue;

    /** 토글. 기본값은 그 표기의 "끔" 이다 — 모르는 설정을 켜 두지 않는다. */
    FidoSettingKey(String key, String section, String label, ValueStyle style) {
        this(key, section, label, Type.TOGGLE, style, style.off());
    }

    /** 토글이 아닌 항목. 값을 그대로 저장한다. */
    FidoSettingKey(String key, String section, String label, Type type, String defaultValue) {
        this(key, section, label, type, ValueStyle.RAW, defaultValue);
    }

    FidoSettingKey(String key, String section, String label, Type type, ValueStyle style, String defaultValue) {
        this.key = key;
        this.section = section;
        this.label = label;
        this.type = type;
        this.style = style;
        this.defaultValue = defaultValue;
    }

    public String key() { return key; }
    public String section() { return section; }
    public String label() { return label; }
    public Type type() { return type; }
    public ValueStyle style() { return style; }
    public String defaultValue() { return defaultValue; }

    /** 저장된 값이 켜져 있는가. 템플릿이 체크박스 상태를 고르는 데 쓴다. */
    public boolean isOn(String value) { return style.isOn(value); }

    /** 섹션 이름 → 그 섹션의 키들. 화면이 섹션별로 묶어 그린다. 선언 순서를 유지한다. */
    public static LinkedHashMap<String, List<FidoSettingKey>> bySection() {
        LinkedHashMap<String, List<FidoSettingKey>> map = new LinkedHashMap<>();
        for (FidoSettingKey k : values()) {
            map.computeIfAbsent(k.section(), s -> new java.util.ArrayList<>()).add(k);
        }
        return map;
    }

    /** PROP_KEY 문자열 → enum. 모르는 키는 비어 있다(운영 DB 에 우리가 안 쓰는 키가 있어도 무시한다). */
    public static java.util.Optional<FidoSettingKey> of(String propKey) {
        return Arrays.stream(values()).filter(k -> k.key.equals(propKey)).findFirst();
    }
}
