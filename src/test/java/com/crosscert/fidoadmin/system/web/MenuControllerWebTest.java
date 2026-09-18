package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.service.MenuService;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MenuController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class MenuControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean MenuService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaMenu menu(long idx, String name, long parent) {
        CcfaMenu m = new CcfaMenu(); m.setIdx(idx); m.setMenuName(name); m.setMenuCode("C" + idx); m.setMenuParentIdx(parent);
        m.setVisible("true"); m.setOpenType("open"); m.setStatistics("N"); m.setReadonly("N");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/menus").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 부모 IDX 대신 부모 이름을 보여준다. */
    @Test void listRendersRowsWithParentName() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L)));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L))));
        mvc.perform(get("/system/menus").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/list"))
            .andExpect(content().string(containsString("앱 ID")))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("/system/menus/2")));
    }

    /** 수정 폼의 부모 select 에는 자기 자신이 없고 "최상위" 는 있다. */
    @Test void editFormExcludesSelfFromParentSelect() throws Exception {
        when(service.get(2L)).thenReturn(menu(2L, "앱 ID", 1L));
        when(service.selectableParentsFor(2L)).thenReturn(List.of(menu(1L, "FIDO", 0L)));
        mvc.perform(get("/system/menus/2/edit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("value=\"1\"")))
            .andExpect(content().string(not(containsString("value=\"2\">#2 앱 ID"))));
    }

    @Test void blankNameShowsFormAgain() throws Exception {
        when(service.allForSelect()).thenReturn(List.of());
        when(service.parentExists(0L)).thenReturn(true);
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "").param("menuParentIdx", "0").param("visible", "true")
                .param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.parentExists(0L)).thenReturn(true);
        when(service.create(any())).thenReturn(menu(9L, "신규", 0L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "신규").param("menuCode", "NEW").param("menuParentIdx", "0").param("menuSeq", "5")
                .param("visible", "true").param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/menus/9"));
    }

    /** 등록 시 존재하지 않는 부모(예: 999)를 지정하면 저장 전에 막혀 폼을 다시 그린다. */
    @Test void createWithUnknownParentShowsFormAgain() throws Exception {
        when(service.parentExists(999L)).thenReturn(false);
        when(service.allForSelect()).thenReturn(List.of());
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "신규").param("menuCode", "NEW").param("menuParentIdx", "999")
                .param("visible", "true").param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"))
            .andExpect(content().string(containsString("존재하지 않는 부모 메뉴입니다.")));
        verify(service, never()).create(any());
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/menus").with(user(superUser)).param("menuName", "x")).andExpect(status().isForbidden());
    }

    /** 하위 메뉴를 부모로 지정하면(순환) 서비스 검증에서 막혀 200 + 폼 재표시(리다이렉트 없음). */
    @Test void updateWithDescendantAsParentReRendersForm() throws Exception {
        CcfaMenu existing = menu(1L, "FIDO", 0L);
        when(service.get(1L)).thenReturn(existing);
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(3L, "하위", 1L)));
        when(service.parentExists(3L)).thenReturn(true);
        when(service.isSelfOrDescendant(3L, 1L)).thenReturn(true);
        when(service.update(eq(1L), any())).thenAnswer(inv -> {
            Consumer<CcfaMenu> mutator = inv.getArgument(1);
            CcfaMenu target = menu(1L, "FIDO", 0L);
            mutator.accept(target);
            return target;
        });

        mvc.perform(post("/system/menus/1").with(user(superUser)).with(csrf())
                .param("menuName", "FIDO").param("menuParentIdx", "3")
                .param("visible", "true").param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"));
    }

    /** 수정 폼은 selectableParentsFor(id) 를 사용해 자신+하위를 제외한 목록을 그린다. */
    @Test void editFormUsesSelectableParentsFor() throws Exception {
        when(service.get(1L)).thenReturn(menu(1L, "FIDO", 0L));
        when(service.selectableParentsFor(1L)).thenReturn(List.of(menu(4L, "무관", 0L)));
        mvc.perform(get("/system/menus/1/edit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"))
            .andExpect(content().string(containsString("무관")))
            .andExpect(content().string(not(containsString("value=\"2\">#2 앱 ID"))));
        verify(service).selectableParentsFor(1L);
    }
}
