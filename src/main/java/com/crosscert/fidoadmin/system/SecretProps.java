package com.crosscert.fidoadmin.system;

/**
 * CCFA_SYSTEM_PROP 중 화면에 값을 내보내면 안 되는 키를 가린다.
 *
 * <p>이 테이블은 범용 key-value 라 컬럼만 봐서는 비밀값인지 알 수 없다. 키 이름으로 판별한다.
 * 같은 행을 {@code /system/settings}(정해진 키를 한 번에 저장하는 화면)와
 * {@code /system/props}(임의 키를 직접 다루는 저수준 화면)가 함께 읽으므로, 한쪽만 가리면
 * 다른 쪽이 뒷문이 된다. 그래서 판별을 여기 한 곳에 두고 두 화면이 같이 쓴다.
 *
 * <p>가린 값은 되돌릴 수 없다(단방향). 화면에서 확인할 수 없게 하는 것이 목적이고,
 * 저장할 때는 "빈 값이면 기존 값을 유지한다" 는 규칙으로 덮어쓰기를 막는다.
 */
public final class SecretProps {

    /** 화면에 값 대신 내보내는 표시. 빈 문자열과 구분되어야 "설정되어 있음" 을 알릴 수 있다. */
    public static final String MASK = "********";

    private SecretProps() {}

    /**
     * 비밀값으로 다룰 키인가.
     *
     * <p>이름에 PASSWORD / PASSWD / PW / SECRET / TOKEN 이 들어가면 비밀값으로 본다.
     * 운영 DB 에 실제로 있는 키는 SMTP_PASSWORD 하나지만, 나중에 누가 비슷한 키를 넣어도
     * 자동으로 가려지게 넓게 잡는다 — 빠뜨려서 새는 쪽이 과하게 가리는 쪽보다 해롭다.
     */
    public static boolean isSecret(String propKey) {
        if (propKey == null) return false;
        String k = propKey.toUpperCase();
        return k.contains("PASSWORD") || k.contains("PASSWD") || k.endsWith("_PW") || k.equals("PW")
            || k.contains("SECRET") || k.contains("TOKEN");
    }

    /** 화면에 내보낼 값. 비밀 키면 값이 있을 때만 마스크를, 없으면 빈 값을 준다. */
    public static String forDisplay(String propKey, String value) {
        if (!isSecret(propKey)) return value;
        return (value == null || value.isEmpty()) ? "" : MASK;
    }

    /**
     * 제출된 값을 저장해야 하는가.
     *
     * <p>비밀 키는 빈 값이거나 마스크 그대로면 건드리지 않는다. 화면이 실제 값을 모르는 채로
     * 폼을 다시 제출하므로(다른 항목만 바꿔 저장하는 경우가 그렇다), 이 규칙이 없으면
     * 비밀번호가 빈 값이나 "********" 로 덮어써진다.
     */
    public static boolean shouldSave(String propKey, String submitted) {
        if (!isSecret(propKey)) return true;
        return submitted != null && !submitted.isEmpty() && !MASK.equals(submitted);
    }
}
