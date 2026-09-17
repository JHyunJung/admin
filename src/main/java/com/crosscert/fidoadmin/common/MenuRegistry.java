package com.crosscert.fidoadmin.common;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. COMPANY_IDX 가 없는 테이블(CRITERIA, FIDO2_*, 시스템)의 화면은 SUPER 전용이다(설계 3.3). */
@Component("menuRegistry")
public class MenuRegistry {

    static final List<MenuItem> ALL = List.of(
        new MenuItem("대시보드", "통계", "/", false, "bi-speedometer2"),
        new MenuItem("고객사", "고객사", "/companies", true, "bi-building"),
        new MenuItem("고객사", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem("고객사", "라이선스", "/licenses", true, "bi-award"),
        new MenuItem("운영자", "운영자", "/managers", true, "bi-person-badge"),
        new MenuItem("운영자", "가입 승인", "/signups", true, "bi-person-check"),
        new MenuItem("운영자", "내 비밀번호 변경", "/me/password", false, "bi-key"),
        new MenuItem("FIDO", "앱 ID", "/appids", false, "bi-app-indicator"),
        new MenuItem("FIDO", "앱 서버", "/appservers", false, "bi-hdd-network"),
        new MenuItem("FIDO", "사용자", "/users", false, "bi-people"),
        new MenuItem("FIDO", "챌린지", "/challenges", false, "bi-patch-question"),
        new MenuItem("FIDO", "서명", "/signs", false, "bi-pen"),
        new MenuItem("FIDO", "거래 해시", "/transaction-hashes", false, "bi-hash"),
        new MenuItem("FIDO", "거래 확인", "/transaction-confirmations", false, "bi-check2-square"),
        new MenuItem("FIDO", "인증기기 기준", "/criteria", true, "bi-fingerprint"),
        new MenuItem("FIDO2", "메타데이터", "/fido2/metadata", true, "bi-card-list"),
        new MenuItem("FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true, "bi-shield-lock"),
        new MenuItem("FIDO2", "데모 접근코드", "/fido2/demo-access-codes", true, "bi-ticket-perforated"),
        new MenuItem("로그", "FIDO 로그", "/logs/fido", false, "bi-journal-text"),
        new MenuItem("로그", "감사 로그", "/logs/audit", false, "bi-clipboard-check"),
        new MenuItem("로그", "예외 로그", "/logs/exceptions", false, "bi-exclamation-triangle"),
        new MenuItem("로그", "메일/SMS 큐", "/logs/mailing", false, "bi-envelope"),
        new MenuItem("시스템", "시스템 설정", "/system/props", true, "bi-sliders"),
        new MenuItem("시스템", "시스템 정보", "/system/info", true, "bi-info-circle"),
        new MenuItem("시스템", "에러 코드", "/system/error-codes", true, "bi-bug"),
        new MenuItem("시스템", "FIDO 서버", "/system/fido-clients", true, "bi-server"),
        new MenuItem("시스템", "어드민 기준", "/system/criteria", true, "bi-ui-checks"),
        new MenuItem("시스템", "메뉴 정의", "/system/menus", true, "bi-list-nested"),
        new MenuItem("시스템", "코드 그룹/코드", "/system/options", true, "bi-tags"),
        new MenuItem("시스템", "필드 정의", "/system/fields", true, "bi-input-cursor-text"));

    public List<MenuItem> itemsFor(boolean isSuper) {
        return isSuper ? ALL : ALL.stream().filter(m -> !m.superOnly()).toList();
    }

    public static LinkedHashMap<String, List<MenuItem>> groups(List<MenuItem> items) {
        LinkedHashMap<String, List<MenuItem>> map = new LinkedHashMap<>();
        for (MenuItem m : items) map.computeIfAbsent(m.group(), k -> new java.util.ArrayList<>()).add(m);
        return map;
    }
}
