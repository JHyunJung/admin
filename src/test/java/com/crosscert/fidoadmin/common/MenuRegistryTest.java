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
        assertThat(registry.itemsFor(false)).extracting(MenuItem::href).contains("/", "/appids", "/users", "/logs/fido")
            .doesNotContain("/companies", "/managers", "/system/props");
    }

    @Test void groupsPreserveOrder() {
        var groups = MenuRegistry.groups(registry.itemsFor(true));
        assertThat(groups.keySet()).containsExactly("대시보드", "고객사", "운영자", "FIDO", "FIDO2", "로그", "시스템");
    }
}
