package com.crosscert.fidoadmin.common;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 코드에 고정된 메뉴. CCFA_MENU 는 데이터로만 다룬다. COMPANY_IDX 가 없는 테이블(CRITERIA, FIDO2_*, 시스템)의 화면은 SUPER 전용이다(설계 3.3). */
@Component("menuRegistry")
public class MenuRegistry {

    /**
     * 그룹 이름과 순서는 이전 어드민의 업무 도메인 구분을 따른다(로그·이상 징후 탐지·
     * FIDO 서버 관리·시스템관리). 기존 사용자가 찾던 자리에서 찾게 하려는 것이므로,
     * 테이블 성격이 아니라 <b>업무 성격</b>으로 묶는다.
     *
     * <p>이전 어드민에 있던 "통계 → 통계관리"와 "이상 징후 탐지 → 모니터링"은 넣지 않았다.
     * 전자는 대시보드가 같은 일을 하고, 후자는 대응하는 화면이 없다. 화면 없는 메뉴는
     * 404 나 빈 페이지로 이어지므로, 화면이 생길 때 함께 추가한다.
     *
     * <p>"필드 정의"(/system/fields)와 "데모 접근코드"(/fido2/demo-access-codes)는 메뉴에서 뺐다
     * (설계서 1의 "프로토타입 확인 후 불필요한 화면은 제외한다"). 화면과 컨트롤러는 남아 있어
     * URL 로는 열리지만, 운영자의 동선에서는 치운다.
     *
     * <p>필드 정의를 뺀 이유는 메뉴 정의 화면을 제거한 이유와 같다. CCFA_FIELDS 는 화면 필드를
     * 데이터로 정의하려던 구조인데 새 어드민은 Thymeleaf 템플릿에 직접 쓴다. 즉 이 화면에서
     * 값을 바꿔도 화면은 바뀌지 않는다 — 반영된 줄 아는 상태가 해롭다는 같은 판단이다
     * (docs/erd/2026-09-18-테이블-존치-검토.md 가 CCFA_MENU 와 한 문단에서 함께 지목했다).
     * 데모 접근코드는 이름 그대로 데모용이라 운영 동선에 들어오지 않는다.
     *
     * <p>"메일/SMS 큐"(/logs/mailing)도 운영에서 쓰지 않아 뺐다. 어드민은 CCFA_MAILING 을
     * 읽기만 하고 발송은 다른 시스템이 한다 — 존치 검토 문서가 "그 발송 주체가 지금도
     * 동작하는가" 를 확인 사항으로 남겨 둔 화면이다. 발송 주체가 살아 있고 큐를 봐야 할
     * 일이 생기면 그때 되살린다.
     *
     * <p>다만 이것은 목록에서 지우지 않고 {@code hidden} 으로 감춘다. TENANT 경로라서
     * 지우면 {@link #areaOf} 가 SYSTEM 으로 판정해 테넌트 선택 인터셉터가 막지 못한다
     * (자세한 이유는 {@link MenuItem} 주석에 있다). 위의 두 화면은 SYSTEM 이라 지워도
     * 판정이 달라지지 않아 그대로 두었다.
     *
     * <p>"시스템관리"는 테넌트 항목(운영자·라이선스·시스템 설정)과 전역 항목이 섞인
     * 유일한 그룹이다. 사이드바가 영역별로 나눠 그리므로 이 제목은 테넌트 구역과
     * 시스템 구역에 각각 한 번씩 나타난다. 의미상 맞는 표시다 — 위쪽은 선택한 고객사의
     * 운영자·라이선스, 아래쪽은 고객사와 무관한 전역 설정이다.
     */
    static final List<MenuItem> ALL = List.of(
        new MenuItem(MenuArea.TENANT, "대시보드", "통계", "/", false, "bi-speedometer2"),
        // 이전 어드민의 "로그 → 인증로그 / 시스템(감사)로그" 를 잇는다.
        new MenuItem(MenuArea.TENANT, "로그", "FIDO 로그", "/logs/fido", false, "bi-journal-text"),
        new MenuItem(MenuArea.TENANT, "로그", "감사 로그", "/logs/audit", false, "bi-clipboard-check"),
        new MenuItem(MenuArea.TENANT, "로그", "예외 로그", "/logs/exceptions", false, "bi-exclamation-triangle"),
        // 메일/SMS 큐는 사이드바에서 감췄다(hidden). 항목을 지우지 않는 이유는 MenuItem 주석에 있다 —
        // TENANT 경로를 목록에서 빼면 areaOf 가 SYSTEM 으로 판정해 인터셉터가 막지 못한다.
        new MenuItem(MenuArea.TENANT, "로그", "메일/SMS 큐", "/logs/mailing", false, "bi-envelope", true),
        // 이전 어드민의 "이상 징후 탐지 → 정책관리". 모니터링 화면은 아직 없다.
        new MenuItem(MenuArea.TENANT, "이상 징후 탐지", "FDS 정책", "/fds-policies", false, "bi-shield-check"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "앱 ID", "/appids", false, "bi-app-indicator"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "앱 서버", "/appservers", false, "bi-hdd-network"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "사용자", "/users", false, "bi-people"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "챌린지", "/challenges", false, "bi-patch-question"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "서명", "/signs", false, "bi-pen"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "거래 해시", "/transaction-hashes", false, "bi-hash"),
        new MenuItem(MenuArea.TENANT, "FIDO 서버 관리", "거래 확인", "/transaction-confirmations", false, "bi-check2-square"),
        // FIDO2 는 이전 어드민에 없던 그룹이다. FIDO 서버 관리에 합치면 12개가 되어 따로 둔다.
        //
        // 인증기기 기준(CRITERIA)은 이전 어드민에서 "AAID(정책) 보기" 로 FIDO 서버 관리에
        // 있었지만 여기서는 FIDO2 에 둔다. COMPANY_IDX 가 없는 전역 테이블이라 SYSTEM 영역인데,
        // FIDO 서버 관리의 나머지 7개는 전부 TENANT 다. 같은 그룹에 두면 사이드바가 영역별로
        // 나눠 그리면서 "FIDO 서버 관리" 제목이 두 번 나오고 이 항목만 아래쪽에 홀로 떨어진다.
        // 성격상으로도 FIDO2 메타데이터와 함께 있는 편이 맞다(둘 다 SUPER 전용 전역 기준 데이터).
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "인증기기 기준", "/criteria", true, "bi-fingerprint"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "메타데이터", "/fido2/metadata", true, "bi-card-list"),
        new MenuItem(MenuArea.SYSTEM, "FIDO2", "크리덴셜 파라미터", "/fido2/credential-params", true, "bi-shield-lock"),
        // 이전 어드민의 "시스템관리 → 업체 관리 / 관리자 설정 / 라이선스관리 / 시스템 설정".
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "고객사", "/companies", true, "bi-building"),
        new MenuItem(MenuArea.TENANT, "시스템관리", "운영자", "/managers", true, "bi-person-badge"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "슈퍼관리자 계정", "/managers/super", true, "bi-person-gear"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "가입 승인", "/signups", true, "bi-person-check"),
        new MenuItem(MenuArea.TENANT, "시스템관리", "라이선스", "/licenses", true, "bi-award"),
        new MenuItem(MenuArea.TENANT, "시스템관리", "FIDO 서버 설정", "/system/settings", true, "bi-toggles"),
        new MenuItem(MenuArea.TENANT, "시스템관리", "시스템 설정", "/system/props", true, "bi-sliders"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "시스템 정보", "/system/info", true, "bi-info-circle"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "에러 코드", "/system/error-codes", true, "bi-bug"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "FIDO 서버", "/system/fido-clients", true, "bi-server"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "어드민 기준", "/system/criteria", true, "bi-ui-checks"),
        new MenuItem(MenuArea.SYSTEM, "시스템관리", "코드 그룹/코드", "/system/options", true, "bi-tags"),
        new MenuItem(MenuArea.PERSONAL, "내 정보", "내 비밀번호 변경", "/me/password", false, "bi-key"));

    /**
     * 화면에 내놓을 메뉴. {@code hidden} 항목은 뺀다.
     *
     * <p>{@link #ALL} 을 직접 쓰지 않는 이유가 여기 있다 — ALL 은 "이 경로가 어느 영역인가" 를
     * 아는 원본이고(areaOf·listPathFor), 이 메서드는 "사람에게 보여 줄 목록" 이다. 둘을 갈라
     * 두어야 화면에서 감춘 것이 경로 판정까지 바꾸지 않는다.
     */
    public List<MenuItem> itemsFor(boolean isSuper) {
        return ALL.stream()
            .filter(m -> !m.hidden())
            .filter(m -> isSuper || !m.superOnly())
            .toList();
    }

    /**
     * 영역으로 거른 목록. 사이드바가 영역별로 나눠 그리는 데 쓴다(Task 10).
     *
     * <p>템플릿의 SpEL 에는 {@code items.?[area.name() == 'TENANT']} 같은 선택 연산자 전례가
     * 없어, 필터를 자바로 옮겨 이 오버로드로 둔다. 기존 {@code itemsFor(boolean)} 시그니처는
     * sidebar.html 이 그대로 호출하므로 바꾸지 않는다.
     */
    public List<MenuItem> itemsFor(boolean isSuper, MenuArea area) {
        return itemsFor(isSuper).stream().filter(m -> m.area() == area).toList();
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
