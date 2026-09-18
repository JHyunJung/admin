package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MenuRegistryTest {

    MenuRegistry registry = new MenuRegistry();

    @Test void superSeesEveryMenu() {
        assertThat(registry.itemsFor(true)).hasSize(MenuRegistry.ALL.size());
    }

    @Test void companyDoesNotSeeSuperOnlyMenus() {
        assertThat(registry.itemsFor(false)).noneMatch(MenuItem::superOnly);
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href)
            .contains("/", "/appids", "/users", "/logs/fido", "/fds-policies")
            .doesNotContain("/companies", "/managers", "/system/props",
                "/criteria", "/fido2/metadata", "/fido2/credential-params", "/fido2/demo-access-codes",
                "/signups");
    }

    /** 가입 승인은 SUPER 전용이다. 메뉴에 보이지 않아야 SecurityConfig 의 403 과 화면이 어긋나지 않는다. */
    @Test void signupApprovalMenuIsSuperOnly() {
        assertThat(MenuRegistry.ALL).filteredOn(m -> "/signups".equals(m.href()))
            .singleElement()
            .satisfies(m -> {
                assertThat(m.group()).isEqualTo("운영자");
                assertThat(m.superOnly()).isTrue();
            });
    }

    /** COMPANY_IDX 가 없는 테이블의 화면은 SUPER 전용(설계 3.3). 그룹은 유지된다. */
    @Test void companySeesNoFido2Group() {
        var groups = MenuRegistry.groups(registry.itemsFor(false));
        assertThat(groups.keySet()).containsExactly("대시보드", "고객사", "운영자", "FIDO", "로그");
    }

    /** 사이드바 아이콘. 모든 메뉴가 Bootstrap Icons 클래스명을 가진다(빈 값 금지). */
    @Test void everyMenuHasAnIcon() {
        assertThat(MenuRegistry.ALL).allSatisfy(m -> {
            assertThat(m.icon()).as("%s 의 아이콘", m.title()).isNotBlank();
            assertThat(m.icon()).as("%s 의 아이콘", m.title()).startsWith("bi-");
        });
    }

    @Test void groupsPreserveOrder() {
        var groups = MenuRegistry.groups(registry.itemsFor(true));
        assertThat(groups.keySet()).containsExactly("대시보드", "고객사", "운영자", "FIDO", "FIDO2", "로그", "시스템");
    }

    @Test void 테넌트_영역_화면은_16개다() {
        long tenant = MenuRegistry.ALL.stream().filter(m -> m.area() == MenuArea.TENANT).count();
        assertThat(tenant).isEqualTo(16);
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
        assertThat(r.areaOf("/system/menus/5")).isEqualTo(MenuArea.SYSTEM);
    }

    // 슈퍼관리자_계정_화면은_시스템_영역이다: /managers/super 는 Task 9 에서 ALL 에 추가된다.
    // 이 테스트는 그때 함께 작성한다(지금 작성하면 실패하고, @Disabled 는 금지되어 있다).

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
        assertThat(r.areaOf("/system/menus")).isEqualTo(MenuArea.SYSTEM);
        assertThat(r.areaOf("/system/info")).isEqualTo(MenuArea.SYSTEM);
    }

    private MenuArea area(String href) {
        return MenuRegistry.ALL.stream().filter(m -> m.href().equals(href))
            .findFirst().orElseThrow().area();
    }
}
