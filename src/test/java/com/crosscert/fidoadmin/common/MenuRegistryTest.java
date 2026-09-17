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
                "/criteria", "/fido2/metadata", "/fido2/credential-params", "/fido2/demo-access-codes");
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
}
