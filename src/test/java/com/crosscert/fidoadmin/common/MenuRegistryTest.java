package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MenuRegistryTest {

    MenuRegistry registry = new MenuRegistry();

    /** SUPER 는 역할로 가려지는 메뉴가 없다. 감춘 항목(hidden)은 누구에게도 보이지 않으므로 뺀다. */
    @Test void superSeesEveryMenu() {
        long visible = MenuRegistry.ALL.stream().filter(m -> !m.hidden()).count();
        assertThat(registry.itemsFor(true)).hasSize((int) visible);
        assertThat(registry.itemsFor(true)).noneMatch(MenuItem::hidden);
    }

    @Test void companyDoesNotSeeSuperOnlyMenus() {
        assertThat(registry.itemsFor(false)).noneMatch(MenuItem::superOnly);
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href)
            .contains("/", "/appids", "/users", "/logs/fido", "/fds-policies")
            // /fido2/demo-access-codes 는 메뉴에서 뺐다. 여기 남겨두면 "SUPER 전용이라 안 보인다" 가
            // 아니라 "메뉴에 아예 없어서 안 보인다" 로 통과해, 아무것도 검증하지 않는 단언이 된다.
            // 그 화면이 되살아나는 것은 아래 전용 테스트가 막는다.
            .doesNotContain("/companies", "/managers", "/system/props",
                "/criteria", "/fido2/metadata", "/fido2/credential-params",
                "/signups");
    }

    /** 가입 승인은 SUPER 전용이다. 메뉴에 보이지 않아야 SecurityConfig 의 403 과 화면이 어긋나지 않는다. */
    @Test void signupApprovalMenuIsSuperOnly() {
        assertThat(MenuRegistry.ALL).filteredOn(m -> "/signups".equals(m.href()))
            .singleElement()
            .satisfies(m -> {
                assertThat(m.group()).isEqualTo("시스템관리");
                assertThat(m.superOnly()).isTrue();
            });
    }

    /**
     * COMPANY_IDX 가 없는 테이블의 화면은 SUPER 전용(설계 3.3)이라 COMPANY 에게는
     * FIDO2 그룹과 시스템관리 그룹이 통째로 사라진다. 나머지 그룹은 유지된다.
     */
    @Test void companySeesNoFido2Group() {
        var groups = MenuRegistry.groups(registry.itemsFor(false));
        assertThat(groups.keySet())
            .containsExactly("대시보드", "로그", "이상 징후 탐지", "FIDO 서버 관리", "내 정보");
    }

    /** 사이드바 아이콘. 모든 메뉴가 Bootstrap Icons 클래스명을 가진다(빈 값 금지). */
    @Test void everyMenuHasAnIcon() {
        assertThat(MenuRegistry.ALL).allSatisfy(m -> {
            assertThat(m.icon()).as("%s 의 아이콘", m.title()).isNotBlank();
            assertThat(m.icon()).as("%s 의 아이콘", m.title()).startsWith("bi-");
        });
    }

    /** 그룹 순서는 이전 어드민의 업무 도메인 순서를 따른다(MenuRegistry.ALL 주석 참고). */
    @Test void groupsPreserveOrder() {
        var groups = MenuRegistry.groups(registry.itemsFor(true));
        assertThat(groups.keySet()).containsExactly(
            "대시보드", "로그", "이상 징후 탐지", "FIDO 서버 관리", "FIDO2", "시스템관리", "내 정보");
    }

    /** 숫자만 맞추지 않도록, 영역 합이 전체와 같은지도 함께 본다. */
    @Test void 테넌트_영역_화면은_17개다() {
        long tenant = MenuRegistry.ALL.stream().filter(m -> m.area() == MenuArea.TENANT).count();
        long system = MenuRegistry.ALL.stream().filter(m -> m.area() == MenuArea.SYSTEM).count();
        long personal = MenuRegistry.ALL.stream().filter(m -> m.area() == MenuArea.PERSONAL).count();
        assertThat(tenant + system + personal).isEqualTo(MenuRegistry.ALL.size());
        // 감춘 항목(hidden)도 ALL 에는 남는다 — 경로의 영역 판정에 필요하다.
        assertThat(tenant).isEqualTo(17);
    }

    /**
     * 메뉴 정의 화면(/system/menus)은 제거했다. MenuRegistry 가 CCFA_MENU 를 읽지 않아
     * 이 화면에서 무엇을 고쳐도 사이드바가 바뀌지 않았기 때문이다 — 운영자가 반영된 줄
     * 아는 상태가 더 해로웠다. 되살릴 때는 사이드바가 실제로 그 데이터를 읽게 만든 뒤여야 한다.
     */
    @Test void 메뉴_정의_화면은_메뉴에_없다() {
        assertThat(MenuRegistry.ALL).extracting(MenuItem::href).doesNotContain("/system/menus");
    }

    /**
     * 필드 정의(/system/fields)는 메뉴 정의 화면과 같은 이유로 메뉴에서 뺐다. CCFA_FIELDS 를
     * 고쳐도 Thymeleaf 템플릿이 그리는 화면은 바뀌지 않는다. 데모 접근코드는 데모용이라
     * 운영 동선에 없다. 메일/SMS 큐는 어드민이 읽기만 하는 화면인데 운영에서 쓰지 않는다.
     * 셋 다 화면·컨트롤러는 남아 있어 URL 로는 열린다(사이드바에서만 감췄다).
     *
     * <p>되살릴 때는 이 테스트를 지우는 것으로 끝내지 말고, 필드 정의는 사이드바가 실제로
     * 그 데이터를 읽게 만든 뒤여야 하고, 메일/SMS 큐는 발송 주체가 동작하는지 확인한 뒤여야 한다.
     */
    @Test void 운영_동선에서_치운_화면은_메뉴에_없다() {
        assertThat(registry.itemsFor(true)).extracting(MenuItem::href)
            .doesNotContain("/system/fields", "/fido2/demo-access-codes", "/logs/mailing");
    }

    /**
     * 감춘 TENANT 화면도 영역 판정은 살아 있어야 한다.
     *
     * <p>목록에서 통째로 지우면 areaOf 가 그 경로를 몰라 SYSTEM 으로 떨어지고, 테넌트 선택
     * 인터셉터가 막지 못한다. 그러면 서비스 계층이 NoTenantSelectedException 을 던지고
     * GlobalExceptionHandler 가 "인터셉터가 놓친 경로" 경고를 남긴다 — 화면 동작은 같지만
     * 운영 로그에 계속 쌓인다. 실제로 그 상태를 한 번 만들었다가 되돌린 이력이 있다.
     */
    @Test void 감춘_화면도_영역_판정은_유지된다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/logs/mailing")).isEqualTo(MenuArea.TENANT);
        assertThat(r.areaOf("/logs/mailing/1")).isEqualTo(MenuArea.TENANT);
        // 감췄어도 목록 경로 절상은 그대로여야 한다(테넌트 전환 후 돌아갈 곳).
        assertThat(r.listPathFor("/logs/mailing/1")).isEqualTo("/logs/mailing");
    }

    @Test void 시스템_영역은_전부_superOnly_다() {
        assertThat(MenuRegistry.ALL.stream()
            .filter(m -> m.area() == MenuArea.SYSTEM))
            .allMatch(MenuItem::superOnly);
    }

    /** 가입 승인은 COMPANY_IDX = -1 인 미배정 계정을 다루므로 테넌트 영역이 아니다. */
    @Test void 가입_승인은_시스템_영역이다() {
        assertThat(area("/signups")).isEqualTo(MenuArea.SYSTEM);
    }

    /** 라이선스·운영자는 SUPER 전용이지만 실제 테넌트 데이터다. */
    @Test void 라이선스와_운영자는_테넌트_영역이다() {
        assertThat(area("/licenses")).isEqualTo(MenuArea.TENANT);
        assertThat(area("/managers")).isEqualTo(MenuArea.TENANT);
    }

    @Test void 하위_경로는_가장_긴_접두사로_판정한다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/users/123/edit")).isEqualTo(MenuArea.TENANT);
        assertThat(r.areaOf("/system/options/5")).isEqualTo(MenuArea.SYSTEM);
    }

    /**
     * /managers/super 는 /managers(TENANT) 의 하위 경로처럼 보이지만 SYSTEM 이어야 한다.
     * 가장 긴 접두사로 판정해야 하는 이유가 바로 이 쌍이다 — 짧은 쪽이 먼저 맞으면
     * 슈퍼관리자 계정 화면이 테넌트 선택을 요구하게 된다.
     */
    @Test void 슈퍼관리자_계정_화면은_시스템_영역이다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/managers/super")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/managers/super/3")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/managers")).isEqualTo(MenuArea.TENANT);
    }

    @Test void 내_비밀번호_변경은_개인_영역이다() {
        assertThat(new MenuRegistry().areaOf("/me/password")).isEqualTo(MenuArea.PERSONAL);
    }

    /**
     * 회귀 테스트: 루트 "/" 가 접두사로 취급되면 등록되지 않은 모든 경로가 잘못 TENANT(대시보드)로
     * 판정된다. "/" 는 정확히 일치할 때만 매칭되어야 한다.
     */
    @Test void 등록되지_않은_경로는_시스템_영역이다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/xyz")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/usersfoo")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/signupsX")).isEqualTo(MenuArea.SYSTEM);
    }

    @Test void 루트는_정확히_일치할_때만_대시보드다() {
        assertThat(new MenuRegistry().areaOf("/")).isEqualTo(MenuArea.TENANT);
    }

    /** /system 하위 형제 경로라도 영역이 다를 수 있다 — 접두사 버그가 숨기 쉬운 지점이다. */
    @Test void system_하위_형제_경로는_각자의_영역으로_판정한다() {
        MenuRegistry r = new MenuRegistry();
        assertThat(r.areaOf("/system/props")).isEqualTo(MenuArea.TENANT);
        assertThat(r.areaOf("/system/props/KEY@1")).isEqualTo(MenuArea.TENANT);
        assertThat(r.areaOf("/system/options")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/system/info")).isEqualTo(MenuArea.SYSTEM);
    }

    @Test void listPathFor_은_하위_경로를_목록_경로로_절상한다() {
        assertThat(new MenuRegistry().listPathFor("/users/123/edit")).isEqualTo("/users");
    }

    /**
     * 미등록 경로는 "/" 를 받는다.
     *
     * <p>주의: 이 단언은 areaOf 의 루트 가드({@code !m.href().equals("/")}) 가
     * listPathFor 에서 빠져도 그대로 통과한다 — 루트 메뉴의 href 자체가 "/" 이고
     * 미일치 시 기본값도 "/" 이므로, "가드가 있어서 접두사로 안 걸림"과
     * "접두사로 걸렸는데 그 href 가 마침 '/' 임"이 같은 값으로 관찰된다.
     * (areaOf 는 MenuArea 를 반환해 SYSTEM/TENANT 로 값이 갈리므로 가드 유무가
     * 드러나지만, listPathFor 는 반환형이 String "/" 하나뿐이라 이 특정 입력으로는
     * 가드 유무를 구분할 수 없다.) 그래도 "미등록 경로 → '/'" 자체는 계약이 맞는
     * 동작이므로 남겨 둔다 — 가드 회귀를 잡는 테스트는 areaOf 쪽의
     * 등록되지_않은_경로는_시스템_영역이다 다.
     */
    @Test void listPathFor_은_미등록_경로에서_루트를_반환한다() {
        assertThat(new MenuRegistry().listPathFor("/xyz")).isEqualTo("/");
    }

    @Test void listPathFor_은_루트_자기_자신에서_루트를_반환한다() {
        assertThat(new MenuRegistry().listPathFor("/")).isEqualTo("/");
    }

    private MenuArea area(String href) {
        return MenuRegistry.ALL.stream().filter(m -> m.href().equals(href))
            .findFirst().orElseThrow().area();
    }
}
