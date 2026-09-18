package com.crosscert.fidoadmin.common;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. COMPANY_IDX 가 없는 테이블(CRITERIA, FIDO2_*, 시스템)의 화면은 SUPER 전용이다(설계 3.3). */
@Component("menuRegistry")
public class MenuRegistry {

    static final List<MenuItem> ALL = List.of(
        new MenuItem(MenuArea.TENANT, "대시보드", "통계", "/", false, "bi-speedometer2"),
        new MenuItem(MenuArea.SYSTEM, "고객사", "고객사", "/companies", true, "bi-building"),
        new MenuItem(MenuArea.TENANT, "고객사", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem(MenuArea.TENANT, "고객사", "라이선스", "/licenses", true, "bi-award"),
        new MenuItem(MenuArea.TENANT, "운영자", "운영자", "/managers", true, "bi-person-badge"),
        new MenuItem(MenuArea.SYSTEM, "운영자", "가입 승인", "/signups", true, "bi-person-check"),
        new MenuItem(MenuArea.PERSONAL, "운영자", "내 비밀번호 변경", "/me/password", false, "bi-key"),
        new MenuItem(MenuArea.TENANT, "FIDO", "앱 ID", "/appids", false, "bi-app-indicator"),
        new MenuItem(MenuArea.TENANT, "FIDO", "앱 서버", "/appservers", false, "bi-hdd-network"),
        new MenuItem(MenuArea.TENANT, "FIDO", "사용자", "/users", false, "bi-people"),
        new MenuItem(MenuArea.TENANT, "FIDO", "챌린지", "/challenges", false, "bi-patch-question"),
        new MenuItem(MenuArea.TENANT, "FIDO", "서명", "/signs", false, "bi-pen"),
        new MenuItem(MenuArea.TENANT, "FIDO", "거래 해시", "/transaction-hashes", false, "bi-hash"),
        new MenuItem(MenuArea.TENANT, "FIDO", "거래 확인", "/transaction-confirmations", false, "bi-check2-square"),
        new MenuItem(MenuArea.SYSTEM, "FIDO", "인증기기 기준", "/criteria", true, "bi-fingerprint"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "메타데이터", "/fido2/metadata", true, "bi-card-list"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true, "bi-shield-lock"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "데모 접근코드", "/fido2/demo-access-codes", true, "bi-ticket-perforated"),
        new MenuItem(MenuArea.TENANT, "로그", "FIDO 로그", "/logs/fido", false, "bi-journal-text"),
        new MenuItem(MenuArea.TENANT, "로그", "감사 로그", "/logs/audit", false, "bi-clipboard-check"),
        new MenuItem(MenuArea.TENANT, "로그", "예외 로그", "/logs/exceptions", false, "bi-exclamation-triangle"),
        new MenuItem(MenuArea.TENANT, "로그", "메일/SMS 큐", "/logs/mailing", false, "bi-envelope"),
        new MenuItem(MenuArea.TENANT, "시스템", "시스템 설정", "/system/props", true, "bi-sliders"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "시스템 정보", "/system/info", true, "bi-info-circle"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "에러 코드", "/system/error-codes", true, "bi-bug"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "FIDO 서버", "/system/fido-clients", true, "bi-server"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "어드민 기준", "/system/criteria", true, "bi-ui-checks"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "메뉴 정의", "/system/menus", true, "bi-list-nested"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "코드 그룹/코드", "/system/options", true, "bi-tags"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "필드 정의", "/system/fields", true, "bi-input-cursor-text"),
        new MenuItem(MenuArea.SYSTEM, "시스템", "슈퍼관리자 계정", "/managers/super", true, "bi-person-gear"));

    public List<MenuItem> itemsFor(boolean isSuper) {
        return isSuper ? ALL : ALL.stream().filter(m -> !m.superOnly()).toList();
    }

    public static LinkedHashMap<String, List<MenuItem>> groups(List<MenuItem> items) {
        LinkedHashMap<String, List<MenuItem>> map = new LinkedHashMap<>();
        for (MenuItem m : items) map.computeIfAbsent(m.group(), k -> new java.util.ArrayList<>()).add(m);
        return map;
    }

    /**
     * 경로가 속한 영역. 하위 경로(/users/123/edit)는 가장 긴 접두사로 판정한다.
     *
     * <p>가장 긴 접두사여야 하는 이유: /managers 는 TENANT 지만 /managers/super 는
     * SYSTEM 이다. 짧은 쪽이 먼저 맞으면 슈퍼관리자 계정 화면이 테넌트 선택을 요구한다.
     *
     * <p>메뉴에 없는 경로는 SYSTEM 으로 본다. 테넌트 화면을 메뉴에 등록하지 않은 채
     * 추가하면 필터가 걸리지 않는데, 그것은 CrudService 가 유효 테넌트로 막는다.
     */
    public MenuArea areaOf(String path) {
        if (path == null) return MenuArea.SYSTEM;
        return ALL.stream()
            .filter(m -> path.equals(m.href()) || (!m.href().equals("/") && path.startsWith(withSlash(m.href()))))
            .max(java.util.Comparator.comparingInt(m -> m.href().length()))
            .map(MenuItem::area)
            .orElse(MenuArea.SYSTEM);
    }

    /**
     * 경로가 속한 메뉴의 목록 경로. "/users/123/edit" → "/users".
     * 일치하는 메뉴가 없으면 "/" 다.
     *
     * <p>루트 "/" 는 areaOf 와 같은 이유로 접두사 판정에서 제외한다 — 그대로 두면
     * 등록되지 않은 경로가 모두 대시보드로 절상되어 버그가 새 자리에서 재발한다.
     */
    public String listPathFor(String path) {
        if (path == null) return "/";
        return ALL.stream()
            .filter(m -> path.equals(m.href()) || (!m.href().equals("/") && path.startsWith(withSlash(m.href()))))
            .max(java.util.Comparator.comparingInt(m -> m.href().length()))
            .map(MenuItem::href)
            .orElse("/");
    }

    /**
     * "/users" → "/users/" 로 만들어 /usersfoo 오탐을 막는다.
     *
     * <p>루트 "/" 는 이 헬퍼를 하위 경로 접두사 판정에 쓰지 않는다(areaOf 참고) — 그대로 두면
     * 모든 경로가 "/" 로 시작하므로 등록되지 않은 경로까지 대시보드(TENANT)로 오판된다.
     */
    private static String withSlash(String href) {
        return href.endsWith("/") ? href : href + "/";
    }
}
