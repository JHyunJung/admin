package com.crosscert.fidoadmin.common;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. COMPANY_IDX 가 없는 테이블(CRITERIA, FIDO2_*, 시스템)의 화면은 SUPER 전용이다(설계 3.3). */
@Component("menuRegistry")
public class MenuRegistry {

    static final List<MenuItem> ALL = List.of(
        new MenuItem("대시보드", "통계", "/", false),
        new MenuItem("고객사", "고객사", "/companies", true),
        new MenuItem("고객사", "FDS 정책", "/fds-policies", false),
        new MenuItem("고객사", "라이선스", "/licenses", true),
        new MenuItem("운영자", "운영자", "/managers", true),
        new MenuItem("운영자", "내 비밀번호 변경", "/me/password", false),
        new MenuItem("FIDO", "앱 ID", "/appids", false),
        new MenuItem("FIDO", "앱 서버", "/appservers", false),
        new MenuItem("FIDO", "사용자", "/users", false),
        new MenuItem("FIDO", "챌린지", "/challenges", false),
        new MenuItem("FIDO", "서명", "/signs", false),
        new MenuItem("FIDO", "거래 해시", "/transaction-hashes", false),
        new MenuItem("FIDO", "거래 확인", "/transaction-confirmations", false),
        new MenuItem("FIDO", "인증기기 기준", "/criteria", true),
        new MenuItem("FIDO2", "메타데이터", "/fido2/metadata", true),
        new MenuItem("FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true),
        new MenuItem("FIDO2", "데모 접근코드", "/fido2/demo-access-codes", true),
        new MenuItem("로그", "FIDO 로그", "/logs/fido", false),
        new MenuItem("로그", "감사 로그", "/logs/audit", false),
        new MenuItem("로그", "예외 로그", "/logs/exceptions", false),
        new MenuItem("로그", "메일/SMS 큐", "/logs/mailing", false),
        new MenuItem("시스템", "시스템 설정", "/system/props", true),
        new MenuItem("시스템", "시스템 정보", "/system/info", true),
        new MenuItem("시스템", "에러 코드", "/system/error-codes", true),
        new MenuItem("시스템", "FIDO 서버", "/system/fido-clients", true),
        new MenuItem("시스템", "어드민 기준", "/system/criteria", true),
        new MenuItem("시스템", "메뉴 정의", "/system/menus", true),
        new MenuItem("시스템", "코드 그룹/코드", "/system/options", true),
        new MenuItem("시스템", "필드 정의", "/system/fields", true));

    public List<MenuItem> itemsFor(boolean isSuper) {
        return isSuper ? ALL : ALL.stream().filter(m -> !m.superOnly()).toList();
    }

    public static LinkedHashMap<String, List<MenuItem>> groups(List<MenuItem> items) {
        LinkedHashMap<String, List<MenuItem>> map = new LinkedHashMap<>();
        for (MenuItem m : items) map.computeIfAbsent(m.group(), k -> new java.util.ArrayList<>()).add(m);
        return map;
    }
}
