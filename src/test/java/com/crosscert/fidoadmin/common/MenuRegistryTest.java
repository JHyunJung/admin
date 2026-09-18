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
        assertThat(r.areaOf("/system/menus")).isEqualTo(MenuArea.SYSTEM);
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
