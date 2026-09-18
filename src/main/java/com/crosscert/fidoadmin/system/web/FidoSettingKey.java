package com.crosscert.fidoadmin.system.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * FIDO 서버 설정 화면이 다루는 고정 키 목록. 값은 {@code CCFA_SYSTEM_PROP} 에
 * {@code (PROP_KEY, COMPANY_IDX)} 로 저장된다 — 고객사마다 다를 수 있다.
 *
 * <p><b>키 이름은 아직 운영 FIDO 서버와 맞춰지지 않았다.</b> ERD 의
 * {@code CCFA_SYSTEM_PROP} 은 범용 key-value 테이블이라 어떤 키를 쓰는지 스키마에
 * 드러나지 않고, 시드에도 {@code PW_FAIL_LIMIT}·{@code SESSION_TIMEOUT}·
 * {@code SERVICE_NAME} 셋뿐이다. 그래서 아래 이름은 <b>이 화면이 정한 것</b>이다.
 * 저장은 정상 동작하지만 서버가 이 키를 읽지 않으면 설정이 반영되지 않는다.
 * 운영 DB 에서 실제 키를 확인하면 {@link #key()} 값만 바꾸면 된다 — 화면·서비스는
 * 이 enum 만 보므로 다른 코드는 손대지 않아도 된다.
 *
 * <p>확인 방법:
 * {@code SELECT PROP_KEY, COMPANY_IDX, PROP_VALUE FROM CCFA_SYSTEM_PROP ORDER BY PROP_KEY}
 */
public enum FidoSettingKey {

    // ---- 인증서 설정 ----
    CERT_VERIFY_ON_REGISTER("CERT_VERIFY_ON_REGISTER", "인증서 설정", "등록시 인증서 검증하기", Type.SELECT, "NONE"),
    SIGN_VERIFY_ON_AUTH("SIGN_VERIFY_ON_AUTH", "인증서 설정", "인증시 서명 검증하기", Type.TOGGLE, "N"),
    AUTH_RESPONSE_OPTIONS("AUTH_RESPONSE_OPTIONS", "인증서 설정", "인증 응답시 추가 옵션", Type.CHECKBOXES,
        "PKCS1,PUBLIC_KEY,PUBLIC_KEY_ALG,AUTHENTICATOR_ALG"),

    // ---- FIDO 부가기능 설정 ----
    ENCRYPT_USER_ID("ENCRYPT_USER_ID", "FIDO 부가기능 설정", "사용자 ID 암호화", Type.TOGGLE, "Y"),
    SAVE_DETAIL_LOG_DB("SAVE_DETAIL_LOG_DB", "FIDO 부가기능 설정", "상세로그 DB저장", Type.TOGGLE, "N"),
    VERIFY_TRANSACTION("VERIFY_TRANSACTION", "FIDO 부가기능 설정", "트랜잭션 검증", Type.TOGGLE, "Y"),
    VERIFY_ATTESTCERT_AAID("VERIFY_ATTESTCERT_AAID", "FIDO 부가기능 설정", "ATTESTCERT AAID 검증", Type.TOGGLE, "N"),
    VERIFY_METADATA_ALGORITHM("VERIFY_METADATA_ALGORITHM", "FIDO 부가기능 설정", "메타데이터 알고리즘 검증", Type.TOGGLE, "N"),
    FORCE_RE_REGISTER("FORCE_RE_REGISTER", "FIDO 부가기능 설정", "강제 재 등록", Type.TOGGLE, "Y"),
    CHALLENGE_EXPIRE_SECONDS("CHALLENGE_EXPIRE_SECONDS", "FIDO 부가기능 설정", "Challenge 유효기간(초)", Type.NUMBER, "60"),
    TC_TEXT_RETENTION_DAYS("TC_TEXT_RETENTION_DAYS", "FIDO 부가기능 설정", "TC원문 저장 기간", Type.SELECT, "0"),

    // ---- 알림메일 설정 ----
    SMTP_HOST("SMTP_HOST", "알림메일 설정", "메일서버 IP", Type.TEXT, ""),
    SMTP_PORT("SMTP_PORT", "알림메일 설정", "메일서버 PORT", Type.NUMBER, "25"),
    SMTP_FROM("SMTP_FROM", "알림메일 설정", "발신자 주소", Type.TEXT, "fidoadmin@crosscert.com"),
    SMTP_USER("SMTP_USER", "알림메일 설정", "사용자 ID", Type.TEXT, ""),
    SMTP_PASSWORD("SMTP_PASSWORD", "알림메일 설정", "사용자 PW", Type.PASSWORD, ""),
    MAIL_SEND_INTERVAL_MINUTES("MAIL_SEND_INTERVAL_MINUTES", "알림메일 설정", "발송주기(분)", Type.NUMBER, "5");

    /** 입력 형태. 템플릿이 이 값으로 위젯을 고른다. */
    public enum Type { TOGGLE, SELECT, CHECKBOXES, NUMBER, TEXT, PASSWORD }

    /** 등록시 인증서 검증하기의 선택지. */
    public static final List<Option> CERT_VERIFY_OPTIONS = List.of(
        new Option("NONE", "검증안함"), new Option("CHAIN", "체인검증"), new Option("REVOKE", "폐기검증"));

    /** TC원문 저장 기간의 선택지. 0 은 영구저장이다. */
    public static final List<Option> TC_RETENTION_OPTIONS = List.of(
        new Option("0", "영구저장"), new Option("30", "30일"), new Option("90", "90일"), new Option("365", "365일"));

    /** 인증 응답시 추가 옵션의 체크박스. 선택된 것들을 CSV 로 모아 한 행에 저장한다. */
    public static final List<Option> AUTH_RESPONSE_CHOICES = List.of(
        new Option("PKCS1", "PKCS #1"), new Option("PKCS7", "PKCS #7"),
        new Option("PKCS7_PKCS9", "PKCS #7 + PKCS #9"), new Option("PUBLIC_KEY", "공개키"),
        new Option("PUBLIC_KEY_ALG", "공개키 알고리즘"), new Option("AUTHENTICATOR_ALG", "Authenticator 알고리즘"));

    /** select·checkbox 의 한 선택지. */
    public record Option(String value, String label) {}

    private final String key;
    private final String section;
    private final String label;
    private final Type type;
    private final String defaultValue;

    FidoSettingKey(String key, String section, String label, Type type, String defaultValue) {
        this.key = key;
        this.section = section;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String key() { return key; }
    public String section() { return section; }
    public String label() { return label; }
    public Type type() { return type; }
    public String defaultValue() { return defaultValue; }

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
