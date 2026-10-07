package com.crosscert.fidoadmin.system.web;

import java.util.ArrayList;
import java.util.List;

/**
 * FIDO 서버 설정 화면의 위젯 범위·선택지(슬라이더, 저장 기간 드롭다운). 다만 <b>저장된 값이 범위·목록 밖이어도 깎지 않는다</b> — 화면을 열고 다른 항목만
 * 바꿔 저장했는데 이 값이 조용히 바뀌면 FIDO 서버 동작이 달라진다.
 */
public final class FidoSettingOptions {

    public static final int CHALLENGE_MIN = 10;
    public static final int CHALLENGE_MAX = 180;
    public static final int CHALLENGE_STEP = 10;

    /** TC원문 저장 기간(일). 9999 는 "영구저장". */
    public static final String TC_FOREVER = "9999";

    public record Option(String value, String label) {}

    private static final List<Option> TC_BASE = List.of(
        new Option("30", "30일"),
        new Option("90", "90일"),
        new Option("180", "180일"),
        new Option("365", "1년"),
        new Option("1095", "3년"),
        new Option("1825", "5년"),
        new Option(TC_FOREVER, "영구저장"));

    private FidoSettingOptions() {}

    /** 슬라이더 최대값. 저장값이 기본 최대보다 크면 그 값까지 늘린다. */
    public static int challengeMax(String stored) {
        Integer v = parse(stored);
        return v == null ? CHALLENGE_MAX : Math.max(CHALLENGE_MAX, v);
    }

    /** 슬라이더 현재값. 숫자가 아니면 기본값(180). */
    public static int challengeValue(String stored) {
        Integer v = parse(stored);
        return v == null ? CHALLENGE_MAX : v;
    }

    /** TC 저장 기간 선택지. 저장값이 목록에 없으면 "N일" 로 끼워 넣는다(값을 잃지 않게). */
    public static List<Option> tcOptions(String stored) {
        String v = stored == null ? null : stored.trim();
        if (v == null || v.isEmpty() || TC_BASE.stream().anyMatch(o -> o.value().equals(v))) return TC_BASE;
        List<Option> list = new ArrayList<>(TC_BASE);
        list.add(0, new Option(v, v + "일"));
        return list;
    }

    private static Integer parse(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
